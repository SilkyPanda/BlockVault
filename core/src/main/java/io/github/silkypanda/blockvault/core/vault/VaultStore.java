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
    public void create(char[] masterPassword) throws IOException {
        if (exists()) throw new IllegalStateException("Vault already exists");
        if (masterPassword == null || masterPassword.length == 0) {
            throw new IllegalArgumentException("A master password is required");
        }
        byte[] salt = SecureBytes.random(16);
        try {
            int iterations = Kdf.calibrate(250, 210_000, 2_000_000);
            header = new VaultFile.Header(KeyMode.MASTER_PASSWORD, 1, 0, iterations, salt);
            MasterPasswordKek kek = masterKek(masterPassword, salt, iterations, prefix(header));
            try { wrappedKey = kek.wrap(vaultKey = SecureBytes.random(32)); }
            finally { kek.clear(); }
            vault = Vault.empty();
            save(vault);
        } finally {
            SecureBytes.wipe(salt);
        }
    }

    /** Unlocks a master-password vault without modifying the file on any failure. */
    public void unlock(char[] masterPassword) throws Exception {
        byte[] file = readBounded(path("vault.bv"));
        VaultFile.Parsed parsed = VaultFile.parse(file);
        header = parsed.header();
        wrappedKey = parsed.wrappedKey();
        byte[] wrapAad = new byte[28];
        System.arraycopy(parsed.headerAad(), 0, wrapAad, 0, wrapAad.length);
        if (header.keyMode() != KeyMode.MASTER_PASSWORD) throw new IllegalArgumentException("Unsupported vault key mode");
        if (masterPassword == null) throw new IllegalArgumentException("A master password is required");
        MasterPasswordKek kek = masterKek(masterPassword, header.salt(), header.iterations(), wrapAad);
        byte[] candidate;
        try {
            candidate = kek.unwrap(wrappedKey);
        } finally {
            kek.clear();
        }
        activateKey(parsed, candidate);
    }

    /** Stores the currently unlocked vault key for the user's explicit trusted-device choice. */
    public void trustThisDevice() throws IOException {
        if (vaultKey == null) throw new IllegalStateException("Vault is locked");
        Files.createDirectories(directory);
        setPermissions(directory, true);
        Path temporary = path("vault.bv.trust.tmp");
        Path trusted = path("vault.bv.trust");
        byte[] copy = vaultKey.clone();
        try {
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                setPermissions(temporary, false);
                java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(copy);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            Files.move(temporary, trusted, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            SecureBytes.wipe(copy);
            Files.deleteIfExists(temporary);
        }
    }

    /** Removes the local trusted-device key file. */
    public void forgetTrustedDevice() throws IOException {
        Files.deleteIfExists(path("vault.bv.trust"));
        Files.deleteIfExists(path("vault.bv.trust.tmp"));
    }

    /** Unlocks with the owner-only local key file, validating it against the encrypted vault. */
    public void unlockTrustedDevice() throws Exception {
        Path trusted = path("vault.bv.trust");
        if (!Files.isRegularFile(trusted, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                || Files.size(trusted) != 32) {
            throw new IOException("Trusted-device key is missing or invalid");
        }
        requirePrivatePermissions(trusted);
        byte[] candidate = Files.readAllBytes(trusted);
        try {
            byte[] file = readBounded(path("vault.bv"));
            VaultFile.Parsed parsed = VaultFile.parse(file);
            header = parsed.header();
            wrappedKey = parsed.wrappedKey();
            if (header.keyMode() != KeyMode.MASTER_PASSWORD) {
                throw new IllegalArgumentException("Unsupported vault key mode");
            }
            activateKey(parsed, candidate);
            candidate = null;
        } finally {
            SecureBytes.wipe(candidate);
        }
    }

    private void activateKey(VaultFile.Parsed parsed, byte[] candidate) throws Exception {
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
        MasterPasswordKek oldProvider = masterKek(oldPassword, header.salt(), header.iterations(), oldAad);
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
            MasterPasswordKek provider = masterKek(newPassword, salt, iterations, prefix(header));
            try { wrappedKey = provider.wrap(vaultKey); }
            finally { provider.clear(); }
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

    private static MasterPasswordKek masterKek(char[] password, byte[] salt, int iterations, byte[] aad) {
        return new MasterPasswordKek(password, salt, iterations, aad);
    }

    private static byte[] prefix(VaultFile.Header h) {
        java.nio.ByteBuffer bytes = java.nio.ByteBuffer.allocate(28).order(java.nio.ByteOrder.BIG_ENDIAN);
        bytes.put(new byte[] {'B', 'V', 'L', 'T'}).put((byte) 1).put((byte) h.keyMode().id())
                .put((byte) h.kdfId()).put((byte) h.reserved()).putInt(h.iterations()).put(h.salt());
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

    private static void requirePrivatePermissions(Path path) throws IOException {
        try {
            Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(path,
                    java.nio.file.LinkOption.NOFOLLOW_LINKS);
            if (!permissions.equals(Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE))) {
                throw new IOException("Trusted-device key permissions are not private");
            }
        } catch (UnsupportedOperationException ignored) {
            // Windows uses the current user's AppData ACL.
        }
    }
}
