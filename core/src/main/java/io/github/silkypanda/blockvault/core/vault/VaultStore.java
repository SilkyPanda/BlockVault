package io.github.silkypanda.blockvault.core.vault;

import io.github.silkypanda.blockvault.core.crypto.AesGcm;
import io.github.silkypanda.blockvault.core.crypto.Kdf;
import io.github.silkypanda.blockvault.core.crypto.SecureBytes;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

/** Thread-confined encrypted vault storage. Call file and crypto methods off the game thread. */
public final class VaultStore {
    private final Path directory;
    private VaultFile.Header header;
    private byte[] wrappedKey;
    private byte[] vaultKey;
    private Vault vault;

    private VaultStore(Path directory) {
        this.directory = directory.toAbsolutePath().normalize();
    }

    /** Opens the vault metadata without decrypting account data. */
    public static VaultStore open(Path dir) throws IOException, UnsupportedVaultVersionException {
        VaultStore store = new VaultStore(dir);
        Path file = store.path("vault.bv");
        if (Files.exists(file)) {
            VaultFile.Parsed parsed = VaultFile.parse(readBounded(file));
            store.header = parsed.header();
            store.wrappedKey = parsed.wrappedKey();
        }
        return store;
    }

    /** Returns whether a vault file exists. */
    public boolean exists() { return Files.isRegularFile(path("vault.bv")); }

    /** Returns the file key mode, or {@code null} before the first create. */
    public KeyMode keyMode() { return header == null ? null : header.keyMode(); }

    /** Creates and unlocks a new vault. Master-password work runs on the caller's background thread. */
    public void create(KeyMode mode, char[] masterPasswordOrNull, KekProvider deviceProvider) throws IOException {
        if (exists()) throw new IllegalStateException("Vault already exists");
        if (mode == KeyMode.MASTER_PASSWORD && (masterPasswordOrNull == null || masterPasswordOrNull.length == 0)) {
            throw new IllegalArgumentException("A master password is required");
        }
        byte[] salt = mode == KeyMode.MASTER_PASSWORD ? SecureBytes.random(16) : new byte[16];
        try {
            int iterations = mode == KeyMode.MASTER_PASSWORD ? Kdf.calibrate(250, 210_000, 2_000_000) : 0;
            int kdfId = mode == KeyMode.MASTER_PASSWORD ? 1 : 0;
            int providerId = mode == KeyMode.MASTER_PASSWORD ? 0 : requireProvider(deviceProvider).id();
            header = new VaultFile.Header(mode, kdfId, providerId, iterations, salt);
            KekProvider provider = mode == KeyMode.MASTER_PASSWORD
                    ? masterProvider(masterPasswordOrNull, salt, iterations, prefix(header)) : deviceProvider;
            try { wrappedKey = provider.wrap(vaultKey = SecureBytes.random(32)); }
            finally { if (provider instanceof MasterPasswordKek master) master.clear(); }
            vault = Vault.empty();
            save(vault);
        } finally {
            SecureBytes.wipe(salt);
        }
    }

    /** Unlocks a master-password vault without modifying the file on any failure. */
    public void unlock(char[] masterPasswordOrNull) throws Exception {
        unlock(masterPasswordOrNull, null);
    }

    /** Unlocks a device-protected vault using the selected platform provider. */
    public void unlock(char[] masterPasswordOrNull, KekProvider deviceProvider) throws Exception {
        byte[] file = readBounded(path("vault.bv"));
        VaultFile.Parsed parsed = VaultFile.parse(file);
        header = parsed.header();
        wrappedKey = parsed.wrappedKey();
        byte[] wrapAad = new byte[28];
        System.arraycopy(parsed.headerAad(), 0, wrapAad, 0, wrapAad.length);
        KekProvider provider;
        if (header.keyMode() == KeyMode.MASTER_PASSWORD) {
            if (masterPasswordOrNull == null) throw new IllegalArgumentException("A master password is required");
            provider = masterProvider(masterPasswordOrNull, header.salt(), header.iterations(), wrapAad);
        } else {
            provider = requireProvider(deviceProvider);
        }
        byte[] candidate;
        try {
            candidate = provider.unwrap(wrappedKey);
        } finally {
            if (provider instanceof MasterPasswordKek master) master.clear();
        }
        if (candidate.length != 32) {
            SecureBytes.wipe(candidate);
            throw new IllegalArgumentException("Invalid unwrapped vault key");
        }
        byte[] plaintext = null;
        try {
            plaintext = AesGcm.decrypt(candidate, parsed.dataNonce(), parsed.headerAad(), parsed.ciphertext());
            Vault decoded = VaultJson.read(plaintext);
            vaultKey = candidate;
            vault = decoded;
        } catch (Exception e) {
            SecureBytes.wipe(candidate);
            throw e;
        } finally {
            SecureBytes.wipe(plaintext);
        }
    }

    /** Returns the unlocked vault or throws while locked. */
    public Vault vault() {
        if (vault == null) throw new IllegalStateException("Vault is locked");
        return vault;
    }

    /** Atomically encrypts and persists the supplied vault, rotating three backups. */
    public void save(Vault value) throws IOException {
        if (vaultKey == null || header == null || wrappedKey == null) throw new IllegalStateException("Vault is locked");
        Vault toSave = value;
        byte[] json = null;
        byte[] encoded = null;
        Path lockPath = path("vault.bv.lock");
        Files.createDirectories(directory);
        setPermissions(directory, true);
        try (FileChannel lockChannel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock lock = lockChannel.lock()) {
            if (!lock.isValid()) throw new IOException("Could not acquire vault lock");
            setPermissions(lockPath, false);
            if (Files.exists(path("vault.bv"))) {
                try {
                    VaultFile.Parsed disk = VaultFile.parse(readBounded(path("vault.bv")));
                    byte[] diskJson = AesGcm.decrypt(vaultKey, disk.dataNonce(), disk.headerAad(), disk.ciphertext());
                    try {
                        toSave = VaultMerger.merge(toSave, VaultJson.read(diskJson), System.currentTimeMillis());
                    } finally {
                        SecureBytes.wipe(diskJson);
                    }
                } catch (Exception e) {
                    throw new IOException("Existing vault could not be authenticated; file was left untouched", e);
                }
            }
            json = VaultJson.write(toSave);
            encoded = VaultFile.encode(header, wrappedKey, vaultKey, json);
            rotateBackups();
            Path temp = path("vault.bv.tmp");
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE)) {
                java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(encoded);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            setPermissions(temp, false);
            Files.move(temp, path("vault.bv"), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            vault = toSave;
        } finally {
            SecureBytes.wipe(json);
            SecureBytes.wipe(encoded);
        }
    }

    /** Wipes the in-memory vault key and drops decrypted account data. */
    public void lock() {
        SecureBytes.wipe(vaultKey);
        if (vault != null) {
            for (ServerEntry server : vault.servers()) {
                for (Account account : server.accounts()) account.clearPassword();
            }
        }
        vaultKey = null;
        vault = null;
    }

    /** Re-wraps the same vault key with a new master password. */
    public void changeMasterPassword(char[] oldPassword, char[] newPassword) throws Exception {
        if (vaultKey == null || header.keyMode() != KeyMode.MASTER_PASSWORD) throw new IllegalStateException("Vault is not unlocked in master mode");
        byte[] oldAad = prefix(header);
        MasterPasswordKek oldProvider = masterProvider(oldPassword, header.salt(), header.iterations(), oldAad);
        byte[] verifiedKey;
        try {
            verifiedKey = oldProvider.unwrap(wrappedKey);
        } finally {
            oldProvider.clear();
        }
        if (!java.security.MessageDigest.isEqual(verifiedKey, vaultKey)) {
            SecureBytes.wipe(verifiedKey);
            throw new IllegalArgumentException("Master password does not match");
        }
        SecureBytes.wipe(verifiedKey);
        byte[] salt = SecureBytes.random(16);
        try {
            int iterations = Kdf.calibrate(250, 210_000, 2_000_000);
            header = new VaultFile.Header(KeyMode.MASTER_PASSWORD, 1, 0, iterations, salt);
            MasterPasswordKek provider = masterProvider(newPassword, salt, iterations, prefix(header));
            try { wrappedKey = provider.wrap(vaultKey); }
            finally { provider.clear(); }
            save(vault);
        } finally {
            SecureBytes.wipe(salt);
        }
    }

    /** Changes key mode by re-wrapping the existing random vault key. */
    public void switchMode(KeyMode mode, char[] passwordOrNull, KekProvider deviceProvider) throws Exception {
        if (vaultKey == null) throw new IllegalStateException("Vault is locked");
        byte[] salt = mode == KeyMode.MASTER_PASSWORD ? SecureBytes.random(16) : new byte[16];
        try {
            int iterations = mode == KeyMode.MASTER_PASSWORD ? Kdf.calibrate(250, 210_000, 2_000_000) : 0;
            header = new VaultFile.Header(mode, mode == KeyMode.MASTER_PASSWORD ? 1 : 0,
                    mode == KeyMode.MASTER_PASSWORD ? 0 : requireProvider(deviceProvider).id(), iterations, salt);
            KekProvider provider = mode == KeyMode.MASTER_PASSWORD
                    ? masterProvider(passwordOrNull, salt, iterations, prefix(header)) : deviceProvider;
            try { wrappedKey = provider.wrap(vaultKey); }
            finally { if (provider instanceof MasterPasswordKek master) master.clear(); }
            save(vault);
        } finally {
            SecureBytes.wipe(salt);
        }
    }

    private void rotateBackups() throws IOException {
        for (int i = 3; i >= 2; i--) {
            Path from = path("vault.bv.bak" + (i - 1));
            if (Files.exists(from)) Files.move(from, path("vault.bv.bak" + i), StandardCopyOption.REPLACE_EXISTING);
        }
        Path current = path("vault.bv");
        if (Files.exists(current)) Files.copy(current, path("vault.bv.bak1"), StandardCopyOption.REPLACE_EXISTING);
    }

    private Path path(String name) { return directory.resolve(name); }

    private static byte[] readBounded(Path file) throws IOException {
        long size = Files.size(file);
        if (size > VaultFile.MAX_BYTES || size < 0) throw new IOException("Vault file exceeds the supported size");
        return Files.readAllBytes(file);
    }

    private static KekProvider requireProvider(KekProvider provider) {
        if (provider == null || !provider.isAvailable()) throw new IllegalStateException("No key provider is available");
        return provider;
    }

    private static MasterPasswordKek masterProvider(char[] password, byte[] salt, int iterations, byte[] aad) {
        return new MasterPasswordKek(password, salt, iterations, aad);
    }

    private static byte[] prefix(VaultFile.Header h) {
        java.nio.ByteBuffer bytes = java.nio.ByteBuffer.allocate(28).order(java.nio.ByteOrder.BIG_ENDIAN);
        bytes.put(new byte[] {'B', 'V', 'L', 'T'}).put((byte) 1).put((byte) h.keyMode().id())
                .put((byte) h.kdfId()).put((byte) h.providerId()).putInt(h.iterations()).put(h.salt());
        return bytes.array();
    }

    private static void setPermissions(Path path, boolean directory) throws IOException {
        try {
            Files.setPosixFilePermissions(path, directory
                    ? Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE)
                    : Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException ignored) {
            // Windows uses the current user's AppData ACL.
        }
    }
}
