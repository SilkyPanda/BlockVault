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
        VaultFile.Parsed parsed;
        try { parsed = VaultFile.parse(file); }
        catch (UnsupportedVaultVersionException version) { throw version; }
        catch (Exception malformed) { throw new VaultCorruptException(malformed); }
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
            clearVaultPasswords(vault);
            SecureBytes.wipe(vaultKey);
            vaultKey = candidate;
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
            json = VaultJson.write(toSave);
            encoded = VaultFile.encode(header, wrappedKey, vaultKey, json);
            rotateBackupsIfDue();
            Path temp = path("vault.bv.tmp");
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE)) {
                java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(encoded);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            setPermissions(temp, false);
            try { Files.move(temp, path("vault.bv"), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                Files.move(temp, path("vault.bv"), StandardCopyOption.REPLACE_EXISTING);
            }
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
            addBackupSummaries(result, false, 3);
            addBackupSummaries(result, true, MANUAL_BACKUP_SLOTS);
        }
        result.sort(Comparator.comparingLong(BackupSummary::createdAt).reversed());
        return List.copyOf(result);
    }

    /** Lists recovery points without requiring the main vault to exist or unlock. */
    public static List<BackupSummary> availableBackups(Path directory) throws IOException {
        VaultStore store = new VaultStore(directory);
        List<BackupSummary> result = new ArrayList<>();
        store.addBackupSummaries(result, false, 3);
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
            if (parsed.header().keyMode() != KeyMode.MASTER_PASSWORD) {
                throw new VaultCorruptException(new IllegalArgumentException("Backup key mode is unsupported"));
            }
            if (password == null || password.length == 0) throw new IllegalArgumentException("A backup password is required");
            byte[] wrapAad = java.util.Arrays.copyOf(parsed.headerAad(), 28);
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
            Files.createDirectories(store.directory);
            Path temporary = store.path("vault.bv.recovery.tmp");
            try {
                Files.write(temporary, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE);
                setPermissions(temporary, false);
                try { Files.move(temporary, store.path("vault.bv"), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                    Files.move(temporary, store.path("vault.bv"), StandardCopyOption.REPLACE_EXISTING);
                }
            } finally { Files.deleteIfExists(temporary); }
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
                Path temp = path("vault.bv.tmp");
                try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                    java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(encoded);
                    while (buffer.hasRemaining()) channel.write(buffer);
                    channel.force(true);
                }
                setPermissions(temp, false);
                try { Files.move(temp, path("vault.bv"), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                    Files.move(temp, path("vault.bv"), StandardCopyOption.REPLACE_EXISTING);
                }
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

    /** Re-wraps the same vault key with a new master password. */
    public void changeMasterPassword(char[] oldPassword, char[] newPassword) throws Exception {
        if (vaultKey == null || header.keyMode() != KeyMode.MASTER_PASSWORD) throw new IllegalStateException("Vault is not unlocked in master mode");
        if (newPassword == null || newPassword.length < 8) throw new IllegalArgumentException("Master password must contain at least 8 characters");
        if (oldPassword == null || java.util.Arrays.equals(oldPassword, newPassword)) {
            throw new IllegalArgumentException("New master password must differ from the current password");
        }
        byte[] oldAad = prefix(header);
        MasterPasswordKek oldProvider = masterKek(oldPassword, header.salt(), header.iterations(), oldAad);
        byte[] verifiedKey;
        try {
            verifiedKey = oldProvider.unwrap(wrappedKey);
        } catch (VaultAuthException wrongPassword) {
            throw new WrongMasterPasswordException(wrongPassword);
        } finally {
            oldProvider.clear();
        }
        if (!java.security.MessageDigest.isEqual(verifiedKey, vaultKey)) {
            SecureBytes.wipe(verifiedKey);
            throw new IllegalArgumentException("Master password does not match");
        }
        SecureBytes.wipe(verifiedKey);
        byte[] salt = SecureBytes.random(16);
        VaultFile.Header oldHeader = header;
        byte[] oldWrappedKey = wrappedKey;
        try {
            int iterations = Kdf.calibrate(250, 210_000, 2_000_000);
            VaultFile.Header nextHeader = new VaultFile.Header(KeyMode.MASTER_PASSWORD, 1, 0, iterations, salt);
            MasterPasswordKek provider = masterKek(newPassword, salt, iterations, prefix(nextHeader));
            byte[] nextWrapped;
            try { nextWrapped = provider.wrap(vaultKey); }
            finally { provider.clear(); }
            header = nextHeader;
            wrappedKey = nextWrapped;
            try { save(vault); }
            catch (Exception failure) {
                header = oldHeader;
                wrappedKey = oldWrappedKey;
                SecureBytes.wipe(nextWrapped);
                throw failure;
            }
            reencryptBackups();
        } finally {
            SecureBytes.wipe(salt);
        }
    }

    /** Re-encrypts every recovery point with the new master-password wrapper and current vault key. */
    private void reencryptBackups() throws BackupReencryptionException {
        Path lockPath = path("vault.bv.lock");
        try (FileChannel lockChannel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock lock = lockChannel.lock()) {
            if (!lock.isValid()) throw new IOException("Could not acquire vault lock");
            for (int slot = 1; slot <= 3; slot++) reencryptBackup(path("vault.bv.bak" + slot));
            for (int slot = 1; slot <= MANUAL_BACKUP_SLOTS; slot++) reencryptBackup(manualBackupPath(slot));
        } catch (Exception failure) {
            throw new BackupReencryptionException(failure);
        }
    }

    private void reencryptBackup(Path backup) throws Exception {
        if (!Files.isRegularFile(backup)) return;
        java.nio.file.attribute.FileTime createdAt = Files.getLastModifiedTime(backup);
        byte[] encoded = readBounded(backup);
        byte[] plaintext = null;
        byte[] replacement = null;
        try {
            VaultFile.Parsed parsed = VaultFile.parse(encoded);
            plaintext = AesGcm.decrypt(vaultKey, parsed.dataNonce(), parsed.headerAad(), parsed.ciphertext());
            replacement = VaultFile.encode(header, wrappedKey, vaultKey, plaintext);
            Path temporary = backup.resolveSibling(backup.getFileName() + ".tmp");
            try {
                Files.write(temporary, replacement, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE);
                setPermissions(temporary, false);
                try { Files.move(temporary, backup, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                    Files.move(temporary, backup, StandardCopyOption.REPLACE_EXISTING);
                }
                Files.setLastModifiedTime(backup, createdAt);
            } finally { Files.deleteIfExists(backup.resolveSibling(backup.getFileName() + ".tmp")); }
        } finally {
            SecureBytes.wipe(encoded);
            SecureBytes.wipe(plaintext);
            SecureBytes.wipe(replacement);
        }
    }

    private void rotateBackupsIfDue() throws IOException {
        Path current = path("vault.bv");
        if (!Files.isRegularFile(current)) return;
        if (vault == null || vault.servers().stream().allMatch(server -> server.accounts().isEmpty())) return;
        Path newest = path("vault.bv.bak1");
        long now = System.currentTimeMillis();
        if (Files.isRegularFile(newest) && now - Files.getLastModifiedTime(newest).toMillis() < BACKUP_INTERVAL_MILLIS) return;
        rotateBackupsNow();
    }

    private void rotateBackupsNow() throws IOException {
        Path current = path("vault.bv");
        if (!Files.isRegularFile(current)) return;
        Path newest = path("vault.bv.bak1");
        long previousTimestamp = Files.isRegularFile(newest) ? Files.getLastModifiedTime(newest).toMillis() : 0;
        for (int i = 3; i >= 2; i--) {
            Path from = path("vault.bv.bak" + (i - 1));
            if (Files.exists(from)) Files.move(from, path("vault.bv.bak" + i), StandardCopyOption.REPLACE_EXISTING);
        }
        Files.copy(current, newest, StandardCopyOption.REPLACE_EXISTING);
        setPermissions(newest, false);
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
        Files.copy(current, newest, StandardCopyOption.REPLACE_EXISTING);
        setPermissions(newest, false);
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
        if (slot < 1 || slot > 3) throw new IllegalArgumentException("Backup slot must be between 1 and 3");
    }

    private Path path(String name) { return directory.resolve(name); }

    private static void clearVaultPasswords(Vault value) {
        if (value == null) return;
        for (ServerEntry server : value.servers()) for (Account account : server.accounts()) account.clearPassword();
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
