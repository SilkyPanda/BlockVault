package io.github.silkypanda.blockvault.core.vault;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class VaultStoreTest {
    @TempDir Path directory;

    @Test
    void createsUnlocksAndLeavesFileUntouchedOnWrongPassword() throws Exception {
        VaultStore store = VaultStore.open(directory);
        store.create("correct horse".toCharArray());
        Path file = directory.resolve("vault.bv");
        byte[] before = Files.readAllBytes(file);
        store.lock();
        VaultStore reopened = VaultStore.open(directory);
        assertThrows(WrongMasterPasswordException.class, () -> reopened.unlock("wrong horse".toCharArray()));
        assertArrayEquals(before, Files.readAllBytes(file));
        reopened.unlock("correct horse".toCharArray());
        reopened.lock();
    }

    @Test
    void malformedCiphertextIsReportedAsCorruptInsteadOfWrongPassword() throws Exception {
        VaultStore store = VaultStore.open(directory);
        store.create("correct horse".toCharArray());
        store.lock();
        Path file = directory.resolve("vault.bv");
        byte[] bytes = Files.readAllBytes(file);
        bytes[bytes.length - 1] ^= 1;
        Files.write(file, bytes);
        VaultStore reopened = VaultStore.open(directory);
        assertThrows(VaultCorruptException.class, () -> reopened.unlock("correct horse".toCharArray()));
    }

    @Test
    void backupRotationIsAtMostDailyAndRestoreReplacesVaultContents() throws Exception {
        VaultStore store = VaultStore.open(directory);
        store.create("correct horse".toCharArray());
        ServerEntry before = new ServerEntry("one", "one", List.of("one.example"), false, true,
                "auto", List.of(new Account("Alex", "old-pass".toCharArray(), Account.Status.VERIFIED,
                1, 1, 1, null)));
        store.save(new Vault(1, List.of(before)));
        Path backup1 = directory.resolve("vault.bv.bak1");
        assertTrue(Files.isRegularFile(backup1));
        byte[] first = Files.readAllBytes(backup1);
        store.save(store.vault());
        assertArrayEquals(first, Files.readAllBytes(backup1));

        Files.setLastModifiedTime(backup1, FileTime.fromMillis(System.currentTimeMillis() - 2L * 24 * 60 * 60 * 1000));
        store.save(store.vault());
        assertArrayEquals(first, Files.readAllBytes(directory.resolve("vault.bv.bak2")));
        assertTrue(store.backupExists(2));
        ServerEntry after = new ServerEntry("two", "two", List.of("two.example"), false, true,
                "auto", List.of());
        store.save(new Vault(1, List.of(after)));
        store.restoreBackup(1);
        assertEquals(1, store.vault().servers().size());
        assertEquals("one.example", store.vault().servers().get(0).hosts().get(0));
        store.restoreBackup(1);
        assertTrue(store.vault().servers().stream().anyMatch(server -> server.hosts().contains("two.example")));
        assertThrows(IllegalArgumentException.class, () -> store.backupExists(4));
        store.lock();
        VaultStore reopened = VaultStore.open(directory);
        reopened.unlock("correct horse".toCharArray());
        assertTrue(reopened.vault().servers().stream().anyMatch(server -> server.hosts().contains("two.example")));
    }

    @Test
    void manualBackupsKeepTenSlotsAndCanBeInspected() throws Exception {
        VaultStore store = VaultStore.open(directory);
        store.create("correct horse".toCharArray());
        Account account = new Account("Alex", "backup-secret".toCharArray(), Account.Status.VERIFIED,
                10, 20, 30, null);
        ServerEntry server = new ServerEntry("id", "Example", List.of("example.org:25565"), false, true,
                "auto", List.of(account));
        store.save(new Vault(1, List.of(server)));

        store.createManualBackup();
        List<BackupSummary> backups = store.backups();
        List<BackupSummary> manual = backups.stream().filter(BackupSummary::manual).toList();
        assertEquals(1, manual.size());
        assertEquals("backup-secret", new String(store.inspectBackup(manual.getFirst()).servers().getFirst()
                .accounts().getFirst().password()));
        for (int i = 0; i < VaultStore.MANUAL_BACKUP_SLOTS; i++) store.createManualBackup();
        assertEquals(VaultStore.MANUAL_BACKUP_SLOTS,
                store.backups().stream().filter(BackupSummary::manual).count());
        assertTrue(Files.isRegularFile(directory.resolve("vault.bv.manual10")));
        BackupSummary selected = store.backups().stream().filter(BackupSummary::manual).findFirst().orElseThrow();
        com.google.gson.JsonObject deleted = new com.google.gson.JsonObject();
        deleted.addProperty("deleted", true);
        Account tombstone = new Account("Alex", new char[0], Account.Status.VERIFIED, 10, 40, 30, deleted);
        store.save(new Vault(1, List.of(new ServerEntry("id", "Example", List.of("example.org:25565"),
                false, true, "auto", List.of(tombstone)))));
        store.restoreBackup(selected);
        assertEquals("backup-secret", new String(store.vault().servers().getFirst().accounts().getFirst().password()));
    }

    @Test
    void masterPasswordCanBeChangedWithoutChangingVaultKey() throws Exception {
        VaultStore store = VaultStore.open(directory);
        store.create("old password".toCharArray());
        Account account = new Account("Alex", "retained-secret".toCharArray(), Account.Status.VERIFIED,
                1, 1, 1, null);
        store.save(new Vault(1, List.of(new ServerEntry("one", "one", List.of("one.example"), false,
                true, "auto", List.of(account)))));
        assertThrows(WrongMasterPasswordException.class,
                () -> store.changeMasterPassword("incorrect".toCharArray(), "new password".toCharArray()));
        store.changeMasterPassword("old password".toCharArray(), "new password".toCharArray());
        store.lock();
        VaultStore reopened = VaultStore.open(directory);
        assertThrows(WrongMasterPasswordException.class, () -> reopened.unlock("old password".toCharArray()));
        reopened.unlock("new password".toCharArray());
        assertEquals("retained-secret", new String(reopened.vault().servers().get(0).accounts().get(0).password()));
    }

    @Test
    void trustedDeviceKeyUnlocksWithoutStoringTheMasterPassword() throws Exception {
        VaultStore store = VaultStore.open(directory);
        store.create("correct horse".toCharArray());
        store.trustThisDevice();
        Path trustFile = directory.resolve("vault.bv.trust");
        assertTrue(Files.isRegularFile(trustFile));
        assertTrue(Files.size(trustFile) == 32);

        store.lock();
        VaultStore reopened = VaultStore.open(directory);
        reopened.unlockTrustedDevice();
        assertTrue(reopened.vault().servers().isEmpty());
        reopened.lock();
    }

    @Test
    void corruptTrustedDeviceKeyFallsBackWithoutChangingVault() throws Exception {
        VaultStore store = VaultStore.open(directory);
        store.create("correct horse".toCharArray());
        store.trustThisDevice();
        store.lock();
        Path vaultFile = directory.resolve("vault.bv");
        Path trustFile = directory.resolve("vault.bv.trust");
        byte[] before = Files.readAllBytes(vaultFile);
        Files.delete(trustFile);

        VaultStore reopened = VaultStore.open(directory);
        assertThrows(Exception.class, reopened::unlockTrustedDevice);
        assertTrue(java.util.Arrays.equals(before, Files.readAllBytes(vaultFile)));
        Files.write(trustFile, new byte[32]);
        assertThrows(Exception.class, reopened::unlockTrustedDevice);
        assertTrue(java.util.Arrays.equals(before, Files.readAllBytes(vaultFile)));
        reopened.unlock("correct horse".toCharArray());
    }

    @Test
    void disablingTrustRemovesTheLocalKeyFile() throws Exception {
        VaultStore store = VaultStore.open(directory);
        store.create("correct horse".toCharArray());
        store.trustThisDevice();
        store.forgetTrustedDevice();
        assertTrue(Files.notExists(directory.resolve("vault.bv.trust")));
    }
}
