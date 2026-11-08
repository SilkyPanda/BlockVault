package io.github.silkypanda.blockvault.core.vault;

import io.github.silkypanda.blockvault.core.crypto.AesGcm;
import io.github.silkypanda.blockvault.core.crypto.SecureBytes;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;

/** Owns backup rotation and backup-content checks. Call every method on the background IO executor. */
final class VaultBackupRotation {
    private static final long BACKUP_INTERVAL_MILLIS = 24L * 60 * 60 * 1000;
    private final VaultStore store;

    VaultBackupRotation(VaultStore store) { this.store = store; }

    void rotateIfDue() throws IOException {
        Path current = store.path("vault.bv");
        if (!Files.isRegularFile(current)) return;
        Path newest = store.path("vault.bv.bak1");
        long now = System.currentTimeMillis();
        if (Files.isRegularFile(newest)) {
            long age = now - Files.getLastModifiedTime(newest).toMillis();
            // A negative age means the clock moved backwards; do not let that suppress backups for days.
            if (age >= 0 && age < BACKUP_INTERVAL_MILLIS) return;
            if (sameBackupContents(newest, current)) return;
        }
        if (!fileHasBackupData(current)) return;
        rotateNow();
    }

    private boolean sameBackupContents(Path backup, Path currentVault) {
        byte[] backupBytes = null;
        byte[] backupPlaintext = null;
        byte[] currentBytes = null;
        byte[] currentPlaintext = null;
        try {
            backupBytes = VaultFileAccess.readBounded(backup);
            currentBytes = VaultFileAccess.readBounded(currentVault);
            VaultFile.Parsed backupFile = VaultFile.parse(backupBytes);
            VaultFile.Parsed currentFile = VaultFile.parse(currentBytes);
            backupPlaintext = AesGcm.decrypt(store.vaultKey, backupFile.dataNonce(), backupFile.headerAad(), backupFile.ciphertext());
            currentPlaintext = AesGcm.decrypt(store.vaultKey, currentFile.dataNonce(), currentFile.headerAad(), currentFile.ciphertext());
            return MessageDigest.isEqual(backupPlaintext, currentPlaintext);
        } catch (Exception unreadableBackup) {
            return false;
        } finally {
            SecureBytes.wipe(backupBytes);
            SecureBytes.wipe(backupPlaintext);
            SecureBytes.wipe(currentBytes);
            SecureBytes.wipe(currentPlaintext);
        }
    }

    boolean fileHasBackupData(Path file) {
        byte[] encoded = null;
        byte[] plaintext = null;
        try {
            encoded = VaultFileAccess.readBounded(file);
            VaultFile.Parsed parsed = VaultFile.parse(encoded);
            plaintext = AesGcm.decrypt(store.vaultKey, parsed.dataNonce(), parsed.headerAad(), parsed.ciphertext());
            return hasBackupData(VaultJson.read(plaintext));
        } catch (Exception unreadableVault) {
            return false;
        } finally {
            SecureBytes.wipe(encoded);
            SecureBytes.wipe(plaintext);
        }
    }

    private static boolean hasBackupData(Vault value) {
        if (value == null) return false;
        if (value.servers().stream().anyMatch(server -> !server.accounts().isEmpty())) return true;
        return value.trash().stream().anyMatch(entry -> !entry.server().accounts().isEmpty());
    }

    void rotateNow() throws IOException {
        Path current = store.path("vault.bv");
        if (!Files.isRegularFile(current)) return;
        Path newest = store.path("vault.bv.bak1");
        long previousTimestamp = Files.isRegularFile(newest) ? Files.getLastModifiedTime(newest).toMillis() : 0;
        for (int i = VaultStore.AUTO_BACKUP_SLOTS; i >= 2; i--) {
            Path from = store.path("vault.bv.bak" + (i - 1));
            if (Files.exists(from)) Files.move(from, store.path("vault.bv.bak" + i), StandardCopyOption.REPLACE_EXISTING);
        }
        VaultFileAccess.copyAtomic(current, newest);
        stampBackup(newest, previousTimestamp);
    }

    void rotateManual() throws IOException {
        Path current = store.path("vault.bv");
        if (!Files.isRegularFile(current)) throw new IOException("Vault file is missing");
        Path newest = manualBackupPath(1);
        long previousTimestamp = Files.isRegularFile(newest) ? Files.getLastModifiedTime(newest).toMillis() : 0;
        for (int slot = VaultStore.MANUAL_BACKUP_SLOTS; slot >= 2; slot--) {
            Path from = manualBackupPath(slot - 1);
            if (Files.exists(from)) Files.move(from, manualBackupPath(slot), StandardCopyOption.REPLACE_EXISTING);
        }
        VaultFileAccess.copyAtomic(current, newest);
        stampBackup(newest, previousTimestamp);
    }

    private static void stampBackup(Path backup, long previousTimestamp) throws IOException {
        long nextTimestamp = Math.max(System.currentTimeMillis(), previousTimestamp + 1);
        Files.setLastModifiedTime(backup, java.nio.file.attribute.FileTime.fromMillis(nextTimestamp));
    }

    private Path manualBackupPath(int slot) { return store.path("vault.bv.manual" + slot); }
}
