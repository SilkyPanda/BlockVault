package io.github.silkypanda.blockvault.core.vault;

import io.github.silkypanda.blockvault.core.crypto.AesGcm;
import io.github.silkypanda.blockvault.core.crypto.Kdf;
import io.github.silkypanda.blockvault.core.crypto.SecureBytes;
import io.github.silkypanda.blockvault.core.crypto.VaultAuthException;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/** Thread-confined encrypted vault storage. Call file and crypto methods off the game thread. */
public final class VaultStore {
    private static final long BACKUP_INTERVAL_MILLIS = 24L * 60 * 60 * 1000;
    public static final int MANUAL_BACKUP_SLOTS = 10;
    /** Number of rolling automatic backups. */
    public static final int AUTO_BACKUP_SLOTS = 3;
    private static final int PREFIX_BYTES = 28;
    private static final int TARGET_KDF_MILLIS = 250;

    @FunctionalInterface
    private interface LockedAction<T> { T run() throws IOException; }
    private final Path directory;
    private VaultFile.Header header;
    private byte[] wrappedKey;
    private byte[] vaultKey;
    private Vault vault;
    private byte[] diskDigest;

    private VaultStore(Path directory) {
        this.directory = directory.toAbsolutePath().normalize();
    }

    /** Opens the vault metadata without decrypting account data. */
    public static VaultStore open(Path dir) throws IOException, UnsupportedVaultVersionException, VaultCorruptException {
        VaultStore store = new VaultStore(dir);
        Path file = store.path("vault.bv");
        if (Files.exists(file)) {
            byte[] encoded = readBounded(file);
            try {
                VaultFile.Parsed parsed = VaultFile.parse(encoded);
                store.header = parsed.header();
                store.wrappedKey = parsed.wrappedKey();
                store.diskDigest = digest(encoded);
            } catch (UnsupportedVaultVersionException version) {
                throw version;
            } catch (Exception malformed) {
                throw new VaultCorruptException(malformed);
            } finally { SecureBytes.wipe(encoded); }
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
        if (masterPassword == null || masterPassword.length < 8) {
            throw new IllegalArgumentException("Master password must contain at least 8 characters");
        }
        byte[] salt = SecureBytes.random(16);
        try {
            int iterations = Kdf.calibrate(TARGET_KDF_MILLIS, VaultFile.MIN_KDF_ITERATIONS, VaultFile.MAX_KDF_ITERATIONS);
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
        VaultFile.Parsed parsed;
        try { parsed = VaultFile.parse(file); }
        catch (UnsupportedVaultVersionException version) { throw version; }
        catch (Exception malformed) { throw new VaultCorruptException(malformed); }
        if (masterPassword == null) throw new IllegalArgumentException("A master password is required");
        byte[] wrapAad = Arrays.copyOf(parsed.headerAad(), PREFIX_BYTES);
        MasterPasswordKek kek = masterKek(masterPassword, parsed.header().salt(), parsed.header().iterations(), wrapAad);
        byte[] candidate;
        try {
            candidate = kek.unwrap(parsed.wrappedKey());
        } catch (VaultAuthException wrongPassword) {
            throw new WrongMasterPasswordException(wrongPassword);
        } finally {
            kek.clear();
        }
        try { activateKey(parsed, candidate); diskDigest = digest(file); }
        catch (VaultAuthException damaged) { throw new VaultCorruptException(damaged); }
        catch (IllegalArgumentException malformed) { throw new VaultCorruptException(malformed); }
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
            try (FileChannel channel = openPrivate(temporary)) {
                java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(copy);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            Files.move(temporary, trusted, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            fsyncDirectory(directory);
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
            activateKey(parsed, candidate);
            candidate = null;
            diskDigest = digest(file);
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
            clearVaultPasswords(vault);
            SecureBytes.wipe(vaultKey);
            vaultKey = candidate;
            header = parsed.header();
            wrappedKey = parsed.wrappedKey();
            vault = decoded;
        } catch (Exception e) {
            SecureBytes.wipe(candidate);
            throw e;
        } finally {
            SecureBytes.wipe(plaintext);
        }
    }

    /** Refreshes decrypted state from the atomically replaced vault file for another game instance's writes. */
    public void reload() throws Exception {
        if (vaultKey == null) throw new IllegalStateException("Vault is locked");
        byte[] encoded = readVaultSnapshot();
        byte[] plaintext = null;
        try {
            byte[] currentDigest = digest(encoded);
            if (diskDigest != null && Arrays.equals(diskDigest, currentDigest)) return;
            VaultFile.Parsed parsed = VaultFile.parse(encoded);
            plaintext = AesGcm.decrypt(vaultKey, parsed.dataNonce(), parsed.headerAad(), parsed.ciphertext());
            Vault refreshed = VaultJson.read(plaintext);
            clearVaultPasswords(vault);
            header = parsed.header();
            wrappedKey = parsed.wrappedKey();
            vault = refreshed;
            diskDigest = currentDigest;
        } finally {
            SecureBytes.wipe(plaintext);
            SecureBytes.wipe(encoded);
        }
    }

    private byte[] readVaultSnapshot() throws IOException {
        Files.createDirectories(directory);
        setPermissions(directory, true);
        try (FileChannel lockChannel = FileChannel.open(path("vault.bv.lock"), StandardOpenOption.CREATE,
                StandardOpenOption.WRITE);
             FileLock lock = lockChannel.lock()) {
            if (!lock.isValid()) throw new IOException("Could not acquire vault lock");
            setPermissions(path("vault.bv.lock"), false);
            return readBounded(path("vault.bv"));
        }
    }

    /** Returns the unlocked vault or throws while locked. */
    public Vault vault() {
        if (vault == null) throw new IllegalStateException("Vault is locked");
        return vault;
    }

    /** Exports a byte-for-byte copy of the encrypted vault; destination must not already exist. */
    public void exportEncrypted(Path destination) throws IOException {
        if (vaultKey == null) throw new IllegalStateException("Vault is locked");
        Path target = destination.toAbsolutePath().normalize();
        if (!Files.isRegularFile(path("vault.bv")) || Files.exists(target)
                || (target.getParent() != null && target.getParent().equals(directory)
                && target.getFileName().toString().startsWith("vault.bv."))) {
            throw new IOException("Invalid encrypted vault export destination");
        }
        Path parent = target.getParent();
        if (parent == null) throw new IOException("Export destination has no parent directory");
        byte[] encrypted = readBounded(path("vault.bv"));
        boolean created = false;
        try {
            Files.createDirectories(parent);
            try (FileChannel channel = FileChannel.open(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                created = true;
                setPermissions(target, false);
                java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(encrypted);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            setPermissions(target, false);
        } catch (IOException failure) {
            if (created) Files.deleteIfExists(target);
            throw failure;
        }
        finally { SecureBytes.wipe(encrypted); }
    }

    /** Decrypts an encrypted export using its master password, merges it, and re-encrypts locally. */
    public void mergeEncrypted(Path source, char[] masterPassword) throws Exception {
        if (vaultKey == null) throw new IllegalStateException("Vault is locked");
        Path normalized = source.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalized) || normalized.equals(path("vault.bv"))) {
            throw new IOException("Invalid encrypted vault import source");
        }
        byte[] encoded = readBounded(normalized);
        // Always keep a restore point of the current vault before foreign data is merged in.
        withLock(() -> { rotateBackupsNow(); return null; });
        Path temporaryDirectory = Files.createTempDirectory("blockvault-import-");
        VaultStore imported = null;
        try {
            setPermissions(temporaryDirectory, true);
            Path importedFile = temporaryDirectory.resolve("vault.bv");
            Files.write(importedFile, encoded, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            setPermissions(importedFile, false);
            imported = VaultStore.open(temporaryDirectory);
            imported.unlock(masterPassword);
            byte[] mergedJson = VaultJson.write(VaultMerger.merge(vault, imported.vault(), System.currentTimeMillis()));
            try {
                Vault independent = VaultJson.read(mergedJson);
                save(independent);
            } finally {
                SecureBytes.wipe(mergedJson);
            }
        } finally {
            SecureBytes.wipe(encoded);
            if (imported != null) imported.lock();
            Files.deleteIfExists(temporaryDirectory.resolve("vault.bv"));
            Files.deleteIfExists(temporaryDirectory.resolve("vault.bv.lock"));
            Files.deleteIfExists(temporaryDirectory);
        }
    }

    /** Atomically encrypts and persists the supplied vault, rotating at most one backup per day. */
    public void save(Vault value) throws IOException {
        save(value, true);
    }

    /** Saves a deliberate local edit only if the on-disk vault has not changed since reload. */
    public void saveExplicit(Vault value) throws IOException {
        save(value, false);
    }

    private void save(Vault value, boolean mergeWithDisk) throws IOException {
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
            if (!mergeWithDisk && diskDigest != null && !Files.exists(path("vault.bv"))) {
                throw new IOException("Vault changed on disk; reload before applying this edit");
            }
            if (Files.exists(path("vault.bv"))) {
                byte[] diskBytes = readBounded(path("vault.bv"));
                byte[] currentDigest = digest(diskBytes);
                if (!mergeWithDisk && (diskDigest == null || !Arrays.equals(diskDigest, currentDigest))) {
                    SecureBytes.wipe(diskBytes);
                    throw new IOException("Vault changed on disk; reload before applying this edit");
                }
                if (!mergeWithDisk) SecureBytes.wipe(diskBytes);
                if (mergeWithDisk) {
                    try {
                        VaultFile.Parsed disk = VaultFile.parse(diskBytes);
                        byte[] diskJson = AesGcm.decrypt(vaultKey, disk.dataNonce(), disk.headerAad(), disk.ciphertext());
                        try {
                            toSave = VaultMerger.merge(toSave, VaultJson.read(diskJson), System.currentTimeMillis());
                            // Another instance may have changed the master password since we last looked.
                            header = disk.header();
                            wrappedKey = disk.wrappedKey();
                        } finally {
                            SecureBytes.wipe(diskJson);
                        }
                    } catch (Exception e) {
                        throw new IOException("Existing vault could not be authenticated; file was left untouched", e);
                    } finally {
                        SecureBytes.wipe(diskBytes);
                    }
                }
            }
            toSave = VaultTrash.purgeExpired(toSave, System.currentTimeMillis());
            json = VaultJson.write(toSave);
            encoded = VaultFile.encode(header, wrappedKey, vaultKey, json);
            rotateBackupsIfDue();
            atomicWrite(path("vault.bv"), encoded);
            vault = toSave;
            diskDigest = digest(encoded);
        } finally {
            SecureBytes.wipe(json);
            SecureBytes.wipe(encoded);
        }
    }

    private static byte[] digest(byte[] value) throws IOException {
        try { return MessageDigest.getInstance("SHA-256").digest(value); }
        catch (NoSuchAlgorithmException impossible) { throw new IOException("SHA-256 is unavailable", impossible); }
    }

    /** Returns all available automatic and manual backups, newest first within each group. */
    public List<BackupSummary> backups() throws IOException {
        if (vaultKey == null) throw new IllegalStateException("Vault is locked");
        List<BackupSummary> result = new ArrayList<>();
        Files.createDirectories(directory);
        Path lockPath = path("vault.bv.lock");
        try (FileChannel lockChannel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock lock = lockChannel.lock()) {
            if (!lock.isValid()) throw new IOException("Could not acquire vault lock");
            setPermissions(lockPath, false);
            addBackupSummaries(result, false, AUTO_BACKUP_SLOTS);
            addBackupSummaries(result, true, MANUAL_BACKUP_SLOTS);
        }
        result.sort(Comparator.comparingLong(BackupSummary::createdAt).reversed());
        return List.copyOf(result);
    }

    /** Lists recovery points without requiring the main vault to exist or unlock. */
    public static List<BackupSummary> availableBackups(Path directory) throws IOException {
        VaultStore store = new VaultStore(directory);
        List<BackupSummary> result = new ArrayList<>();
        store.addBackupSummaries(result, false, AUTO_BACKUP_SLOTS);
        store.addBackupSummaries(result, true, MANUAL_BACKUP_SLOTS);
        result.sort(Comparator.comparingLong(BackupSummary::createdAt).reversed());
        return List.copyOf(result);
    }

    /** Validates and restores an encrypted backup directly when the main vault is missing or damaged. */
    public static void restoreFromBackup(Path directory, BackupSummary backupInfo, char[] password) throws Exception {
        VaultStore store = new VaultStore(directory);
        Path backup = store.backupPath(backupInfo);
        byte[] bytes = store.readBackup(backupInfo, backup);
        byte[] plaintext = null;
        byte[] candidateKey = null;
        try {
            VaultFile.Parsed parsed;
            try { parsed = VaultFile.parse(bytes); }
            catch (UnsupportedVaultVersionException version) { throw version; }
            catch (Exception malformed) { throw new VaultCorruptException(malformed); }
            if (password == null || password.length == 0) throw new IllegalArgumentException("A backup password is required");
            byte[] wrapAad = java.util.Arrays.copyOf(parsed.headerAad(), PREFIX_BYTES);
            MasterPasswordKek kek = masterKek(password, parsed.header().salt(), parsed.header().iterations(), wrapAad);
            try {
                candidateKey = kek.unwrap(parsed.wrappedKey());
            } catch (VaultAuthException wrongPassword) {
                throw new WrongMasterPasswordException(wrongPassword);
            } finally { kek.clear(); }
            try {
                plaintext = AesGcm.decrypt(candidateKey, parsed.dataNonce(), parsed.headerAad(), parsed.ciphertext());
                Vault decoded = VaultJson.read(plaintext);
                clearVaultPasswords(decoded);
            } catch (Exception invalidBackup) {
                throw new VaultCorruptException(invalidBackup);
            }
            final byte[] restoredBytes = bytes;
            store.withLock(() -> {
                Path current = store.path("vault.bv");
                // Keep whatever is being replaced so a wrong recovery choice is not destructive.
                if (Files.isRegularFile(current)) {
                    copyAtomic(current, store.path("vault.bv.damaged"));
                }
                atomicWrite(current, restoredBytes);
                // The old trusted-device key may belong to a different vault key; a password unlock recreates it.
                Files.deleteIfExists(store.path("vault.bv.trust"));
                return null;
            });
        } finally {
            SecureBytes.wipe(bytes);
            SecureBytes.wipe(plaintext);
            SecureBytes.wipe(candidateKey);
        }
    }

    /** Makes a manually requested encrypted snapshot, retaining the ten newest. */
    public void createManualBackup() throws IOException {
        if (vaultKey == null) throw new IllegalStateException("Vault is locked");
        Files.createDirectories(directory);
        Path lockPath = path("vault.bv.lock");
        try (FileChannel lockChannel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock lock = lockChannel.lock()) {
            if (!lock.isValid()) throw new IOException("Could not acquire vault lock");
            setPermissions(lockPath, false);
            rotateManualBackups();
        }
    }

    /** Decrypts one backup for in-game inspection. The returned account passwords belong to the caller. */
    public Vault inspectBackup(BackupSummary backup) throws Exception {
        if (vaultKey == null) throw new IllegalStateException("Vault is locked");
        Path file = backupPath(backup);
        byte[] bytes = readBackup(backup, file);
        byte[] plaintext = null;
        try {
            VaultFile.Parsed parsed = VaultFile.parse(bytes);
            plaintext = AesGcm.decrypt(vaultKey, parsed.dataNonce(), parsed.headerAad(), parsed.ciphertext());
            return VaultJson.read(plaintext);
        } finally {
            SecureBytes.wipe(bytes);
            SecureBytes.wipe(plaintext);
        }
    }

    /** Restores an automatic or manual backup while retaining the current master-password wrapper. */
    public void restoreBackup(BackupSummary backupInfo) throws Exception {
        if (vaultKey == null) throw new IllegalStateException("Vault is locked");
        Path backup = backupPath(backupInfo);
        byte[] bytes = readBackup(backupInfo, backup);
        byte[] plaintext = null;
        Vault restored;
        try {
            VaultFile.Parsed parsed = VaultFile.parse(bytes);
            plaintext = AesGcm.decrypt(vaultKey, parsed.dataNonce(), parsed.headerAad(), parsed.ciphertext());
            restored = VaultJson.read(plaintext);
        } catch (UnsupportedVaultVersionException version) {
            throw version;
        } catch (Exception invalid) {
            throw new VaultCorruptException(invalid);
        } finally {
            SecureBytes.wipe(bytes);
            SecureBytes.wipe(plaintext);
        }
        byte[] json = VaultJson.write(restored);
        byte[] encoded = null;
        Path lockPath = path("vault.bv.lock");
        try {
            encoded = VaultFile.encode(header, wrappedKey, vaultKey, json);
            Files.createDirectories(directory);
            try (FileChannel lockChannel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 FileLock lock = lockChannel.lock()) {
                if (!lock.isValid()) throw new IOException("Could not acquire vault lock");
                setPermissions(lockPath, false);
                // An explicit restore first captures the state being replaced, even if a daily
                // backup was just taken, so a mistaken restore can be undone.
                rotateBackupsNow();
                atomicWrite(path("vault.bv"), encoded);
                clearVaultPasswords(vault);
                vault = restored;
                diskDigest = digest(encoded);
            }
        } finally {
            SecureBytes.wipe(json);
            SecureBytes.wipe(encoded);
        }
    }

    /** Wipes the in-memory vault key and drops decrypted account data. */
    public void lock() {
        SecureBytes.wipe(vaultKey);
        clearVaultPasswords(vault);
        vaultKey = null;
        vault = null;
    }

    /**
     * Changes the master password and rotates the vault key.
     *
     * <p>A brand-new random vault key is generated, the vault and every recovery point are re-encrypted with it,
     * and an existing trusted-device key file is rewritten. Copies of {@code vault.bv} made earlier (exports,
     * cloud-sync copies) keep their old key and old password but can no longer be merged silently into this vault's
     * lineage. Recovery points that cannot be re-encrypted are left untouched and reported through
     * {@link BackupReencryptionException} after the change has been committed.
     */
    public void changeMasterPassword(char[] oldPassword, char[] newPassword) throws Exception {
        if (vaultKey == null) throw new IllegalStateException("Vault is locked");
        if (newPassword == null || newPassword.length < 8) throw new IllegalArgumentException("Master password must contain at least 8 characters");
        if (oldPassword == null) throw new IllegalArgumentException("The current master password is required");
        if (java.util.Arrays.equals(oldPassword, newPassword)) {
            throw new IllegalArgumentException("New master password must differ from the current password");
        }
        MasterPasswordKek oldProvider = masterKek(oldPassword, header.salt(), header.iterations(), prefix(header));
        byte[] verifiedKey;
        try {
            verifiedKey = oldProvider.unwrap(wrappedKey);
        } catch (VaultAuthException wrongPassword) {
            throw new WrongMasterPasswordException(wrongPassword);
        } finally {
            oldProvider.clear();
        }
        boolean sameKey = java.security.MessageDigest.isEqual(verifiedKey, vaultKey);
        SecureBytes.wipe(verifiedKey);
        if (!sameKey) throw new IllegalArgumentException("Master password does not match");

        byte[] salt = SecureBytes.random(16);
        final byte[] oldKey = vaultKey;
        final byte[] newKey = SecureBytes.random(32);
        boolean committed = false;
        try {
            int iterations = Kdf.calibrate(TARGET_KDF_MILLIS, VaultFile.MIN_KDF_ITERATIONS, VaultFile.MAX_KDF_ITERATIONS);
            final VaultFile.Header nextHeader = new VaultFile.Header(KeyMode.MASTER_PASSWORD, 1, 0, iterations, salt);
            MasterPasswordKek provider = masterKek(newPassword, salt, iterations, prefix(nextHeader));
            final byte[] nextWrapped;
            try { nextWrapped = provider.wrap(newKey); }
            finally { provider.clear(); }

            Rotation rotation = withLock(() -> commitRotation(oldKey, newKey, nextHeader, nextWrapped));
            committed = true;
            clearVaultPasswords(vault);
            vault = rotation.merged();
            header = nextHeader;
            wrappedKey = nextWrapped;
            vaultKey = newKey;
            diskDigest = rotation.digest();
            SecureBytes.wipe(oldKey);
            if (Files.isRegularFile(path("vault.bv.trust"), java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                trustThisDevice();
            }
            if (!rotation.failedBackups().isEmpty()) {
                throw new BackupReencryptionException(new IOException(rotation.failedBackups().size()
                        + " recovery point(s) could not be re-encrypted: " + rotation.failedBackups()));
            }
        } finally {
            SecureBytes.wipe(salt);
            if (!committed) SecureBytes.wipe(newKey);
        }
    }

    private record Rotation(Vault merged, byte[] digest, List<String> failedBackups) { }

    /** Writes the vault under the new key, then re-encrypts recovery points. Runs under the vault lock. */
    private Rotation commitRotation(byte[] oldKey, byte[] newKey, VaultFile.Header nextHeader, byte[] nextWrapped)
            throws IOException {
        Vault merged = vault;
        Path file = path("vault.bv");
        if (Files.isRegularFile(file)) {
            byte[] diskBytes = readBounded(file);
            try {
                VaultFile.Parsed disk = VaultFile.parse(diskBytes);
                byte[] diskJson = AesGcm.decrypt(oldKey, disk.dataNonce(), disk.headerAad(), disk.ciphertext());
                try { merged = VaultMerger.merge(vault, VaultJson.read(diskJson), System.currentTimeMillis()); }
                finally { SecureBytes.wipe(diskJson); }
            } catch (Exception e) {
                throw new IOException("Existing vault could not be authenticated; file was left untouched", e);
            } finally {
                SecureBytes.wipe(diskBytes);
            }
        }
        byte[] json = VaultJson.write(merged);
        byte[] encoded = VaultFile.encode(nextHeader, nextWrapped, newKey, json);
        try {
            rotateBackupsNow();
            atomicWrite(file, encoded);
            List<String> failed = new ArrayList<>();
            for (int slot = 1; slot <= AUTO_BACKUP_SLOTS; slot++) reencryptBackup(path("vault.bv.bak" + slot), oldKey, newKey, nextHeader, nextWrapped, failed);
            for (int slot = 1; slot <= MANUAL_BACKUP_SLOTS; slot++) reencryptBackup(manualBackupPath(slot), oldKey, newKey, nextHeader, nextWrapped, failed);
            return new Rotation(merged, digest(encoded), failed);
        } finally {
            SecureBytes.wipe(json);
            SecureBytes.wipe(encoded);
        }
    }

    /** Re-encrypts one recovery point; any failure is recorded and the file is left as it was. */
    private static void reencryptBackup(Path backup, byte[] oldKey, byte[] newKey, VaultFile.Header nextHeader,
            byte[] nextWrapped, List<String> failed) {
        if (!Files.isRegularFile(backup)) return;
        byte[] encoded = null;
        byte[] plaintext = null;
        byte[] replacement = null;
        try {
            java.nio.file.attribute.FileTime createdAt = Files.getLastModifiedTime(backup);
            encoded = readBounded(backup);
            VaultFile.Parsed parsed = VaultFile.parse(encoded);
            plaintext = AesGcm.decrypt(oldKey, parsed.dataNonce(), parsed.headerAad(), parsed.ciphertext());
            replacement = VaultFile.encode(nextHeader, nextWrapped, newKey, plaintext);
            atomicWrite(backup, replacement);
            Files.setLastModifiedTime(backup, createdAt);
        } catch (Exception unreadable) {
            failed.add(backup.getFileName().toString());
        } finally {
            SecureBytes.wipe(encoded);
            SecureBytes.wipe(plaintext);
            SecureBytes.wipe(replacement);
        }
    }

    /** Permanently removes trash entries older than 60 days and saves if anything was removed. */
    public void purgeExpiredTrash() throws IOException {
        Vault current = vault();
        Vault purged = VaultTrash.purgeExpired(current, System.currentTimeMillis());
        if (purged != current) saveExplicit(purged);
    }

    private void rotateBackupsIfDue() throws IOException {
        Path current = path("vault.bv");
        if (!Files.isRegularFile(current)) return;
        if (vault == null || vault.servers().stream().allMatch(server -> server.accounts().isEmpty())) return;
        Path newest = path("vault.bv.bak1");
        long now = System.currentTimeMillis();
        if (Files.isRegularFile(newest)) {
            long age = now - Files.getLastModifiedTime(newest).toMillis();
            // A negative age means the clock moved backwards; do not let that suppress backups for days.
            if (age >= 0 && age < BACKUP_INTERVAL_MILLIS) return;
        }
        rotateBackupsNow();
    }

    private void rotateBackupsNow() throws IOException {
        Path current = path("vault.bv");
        if (!Files.isRegularFile(current)) return;
        Path newest = path("vault.bv.bak1");
        long previousTimestamp = Files.isRegularFile(newest) ? Files.getLastModifiedTime(newest).toMillis() : 0;
        for (int i = AUTO_BACKUP_SLOTS; i >= 2; i--) {
            Path from = path("vault.bv.bak" + (i - 1));
            if (Files.exists(from)) Files.move(from, path("vault.bv.bak" + i), StandardCopyOption.REPLACE_EXISTING);
        }
        copyAtomic(current, newest);
        stampBackup(newest, previousTimestamp);
    }

    private void rotateManualBackups() throws IOException {
        Path current = path("vault.bv");
        if (!Files.isRegularFile(current)) throw new IOException("Vault file is missing");
        Path newest = manualBackupPath(1);
        long previousTimestamp = Files.isRegularFile(newest) ? Files.getLastModifiedTime(newest).toMillis() : 0;
        for (int slot = MANUAL_BACKUP_SLOTS; slot >= 2; slot--) {
            Path from = manualBackupPath(slot - 1);
            if (Files.exists(from)) Files.move(from, manualBackupPath(slot), StandardCopyOption.REPLACE_EXISTING);
        }
        copyAtomic(current, newest);
        stampBackup(newest, previousTimestamp);
    }

    private static void stampBackup(Path backup, long previousTimestamp) throws IOException {
        long nextTimestamp = Math.max(System.currentTimeMillis(), previousTimestamp + 1);
        Files.setLastModifiedTime(backup, java.nio.file.attribute.FileTime.fromMillis(nextTimestamp));
    }

    private void addBackupSummaries(List<BackupSummary> result, boolean manual, int slots) throws IOException {
        for (int slot = 1; slot <= slots; slot++) {
            Path file = manual ? manualBackupPath(slot) : path("vault.bv.bak" + slot);
            if (Files.isRegularFile(file)) {
                result.add(new BackupSummary(manual, slot, Files.getLastModifiedTime(file).toMillis(), Files.size(file)));
            }
        }
    }

    private Path backupPath(BackupSummary backup) {
        if (backup == null) throw new IllegalArgumentException("Backup is required");
        if (backup.manual()) {
            if (backup.slot() < 1 || backup.slot() > MANUAL_BACKUP_SLOTS) {
                throw new IllegalArgumentException("Manual backup slot is invalid");
            }
            return manualBackupPath(backup.slot());
        }
        checkBackupSlot(backup.slot());
        return path("vault.bv.bak" + backup.slot());
    }

    private byte[] readBackup(BackupSummary summary, Path file) throws IOException {
        Path lockPath = path("vault.bv.lock");
        try (FileChannel lockChannel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock lock = lockChannel.lock()) {
            if (!lock.isValid()) throw new IOException("Could not acquire vault lock");
            setPermissions(lockPath, false);
            if (summary.createdAt() > 0 && Files.getLastModifiedTime(file).toMillis() != summary.createdAt()) {
                throw new IOException("Backup changed after it was listed");
            }
            return readBounded(file);
        }
    }

    private Path manualBackupPath(int slot) { return path("vault.bv.manual" + slot); }

    private static void checkBackupSlot(int slot) {
        if (slot < 1 || slot > AUTO_BACKUP_SLOTS) {
            throw new IllegalArgumentException("Backup slot must be between 1 and " + AUTO_BACKUP_SLOTS);
        }
    }

    private Path path(String name) { return directory.resolve(name); }

    private static void clearVaultPasswords(Vault value) {
        if (value == null) return;
        for (ServerEntry server : value.servers()) for (Account account : server.accounts()) account.clearPassword();
        for (TrashEntry entry : value.trash()) {
            for (Account account : entry.server().accounts()) account.clearPassword();
        }
    }

    /** Runs {@code action} while holding the cross-process vault lock. */
    private <T> T withLock(LockedAction<T> action) throws IOException {
        Files.createDirectories(directory);
        Path lockPath = path("vault.bv.lock");
        try (FileChannel lockChannel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock lock = lockChannel.lock()) {
            if (!lock.isValid()) throw new IOException("Could not acquire vault lock");
            setPermissions(lockPath, false);
            return action.run();
        }
    }

    /** Writes {@code data} to a private temp file next to {@code target}, fsyncs, then atomically replaces it. */
    private static void atomicWrite(Path target, byte[] data) throws IOException {
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            try (FileChannel channel = openPrivate(temp)) {
                java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(data);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            try { Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            fsyncDirectory(target.getParent());
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    /** Opens a file for writing, created owner-only from the start where the platform supports it. */
    private static FileChannel openPrivate(Path file) throws IOException {
        java.util.Set<StandardOpenOption> options = java.util.Set.of(StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        try {
            return FileChannel.open(file, options, java.nio.file.attribute.PosixFilePermissions.asFileAttribute(
                    Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)));
        } catch (UnsupportedOperationException windows) {
            return FileChannel.open(file, options);
        }
    }

    private static void fsyncDirectory(Path dir) {
        if (dir == null) return;
        try (FileChannel channel = FileChannel.open(dir, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (IOException | UnsupportedOperationException ignored) {
            // Directories cannot be opened for fsync on every platform (for example Windows).
        }
    }

    /** Copies {@code from} to {@code to} through a temp file so a crash never leaves a truncated backup. */
    private static void copyAtomic(Path from, Path to) throws IOException {
        Path temp = to.resolveSibling(to.getFileName() + ".tmp");
        try {
            Files.copy(from, temp, StandardCopyOption.REPLACE_EXISTING);
            setPermissions(temp, false);
            try { Files.move(temp, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                Files.move(temp, to, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

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
