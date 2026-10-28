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
        store.create("correcthorse".toCharArray());
        Path file = directory.resolve("vault.bv");
        byte[] before = Files.readAllBytes(file);
        store.lock();
        VaultStore reopened = VaultStore.open(directory);
        assertThrows(WrongMasterPasswordException.class, () -> reopened.unlock("wronghorse".toCharArray()));
        assertArrayEquals(before, Files.readAllBytes(file));
        reopened.unlock("correcthorse".toCharArray());
        reopened.lock();
    }

    @Test
    void malformedCiphertextIsReportedAsCorruptInsteadOfWrongPassword() throws Exception {
        VaultStore store = VaultStore.open(directory);
        store.create("correcthorse".toCharArray());
        store.lock();
        Path file = directory.resolve("vault.bv");
        byte[] bytes = Files.readAllBytes(file);
        bytes[bytes.length - 1] ^= 1;
        Files.write(file, bytes);
        VaultStore reopened = VaultStore.open(directory);
        assertThrows(VaultCorruptException.class, () -> reopened.unlock("correcthorse".toCharArray()));
    }

    @Test
    void vaultReadIoFailureIsNotReportedAsCorruption() throws Exception {
        Path vaultPath = directory.resolve("vault.bv");
        Files.createDirectory(vaultPath);
        assertThrows(java.io.IOException.class, () -> VaultStore.open(directory));
    }

    @Test
    void explicitEditsPreserveRollbackAndDeletionButRejectStaleWriters() throws Exception {
        VaultStore first = VaultStore.open(directory);
        first.create("correcthorse".toCharArray());
        Account verified = new Account("Alex", "old-pass".toCharArray(), Account.Status.VERIFIED,
                1, 10, 2, null);
        Vault initial = new Vault(Vault.VERSION, List.of(new ServerEntry("one", "one", List.of("one.example"),
                true, "auto", List.of(verified))));
        first.save(initial);
        first.reload();
        VaultStore stale = VaultStore.open(directory);
        stale.unlock("correcthorse".toCharArray());

        Account pending = new Account("Alex", "new-pass".toCharArray(), Account.Status.PENDING,
                1, 20, 2, null);
        first.saveExplicit(new Vault(Vault.VERSION, List.of(new ServerEntry("one", "one", List.of("one.example"),
                true, "auto", List.of(pending)))));
        Account rollback = new Account("Alex", "old-pass".toCharArray(), Account.Status.VERIFIED,
                1, 10, 2, null);
        first.reload();
        first.saveExplicit(new Vault(Vault.VERSION, List.of(new ServerEntry("one", "one", List.of("one.example"),
                true, "auto", List.of(rollback)))));
        assertEquals("old-pass", new String(first.vault().servers().getFirst().accounts().getFirst().password()));

        first.reload();
        first.saveExplicit(new Vault(Vault.VERSION, List.of()));
        assertTrue(first.vault().servers().isEmpty());
        assertThrows(java.io.IOException.class, () -> stale.saveExplicit(new Vault(Vault.VERSION, List.of())));
    }

    @Test
    void backupRotationIsAtMostDailyAndRestoreReplacesVaultContents() throws Exception {
        VaultStore store = VaultStore.open(directory);
        store.create("correcthorse".toCharArray());
        ServerEntry before = new ServerEntry("one", "one", List.of("one.example"), true,
                "auto", List.of(new Account("Alex", "old-pass".toCharArray(), Account.Status.VERIFIED,
                1, 1, 1, null)));
        store.save(new Vault(Vault.VERSION, List.of(before)));
        Path backup1 = directory.resolve("vault.bv.bak1");
        assertTrue(Files.notExists(backup1));
        store.save(store.vault());
        assertTrue(Files.isRegularFile(backup1));
        byte[] first = Files.readAllBytes(backup1);
        store.save(store.vault());
        assertArrayEquals(first, Files.readAllBytes(backup1));

        Files.setLastModifiedTime(backup1, FileTime.fromMillis(System.currentTimeMillis() - 2L * 24 * 60 * 60 * 1000));
        store.save(store.vault());
        assertArrayEquals(first, Files.readAllBytes(directory.resolve("vault.bv.bak2")));
        assertTrue(store.backups().stream().anyMatch(backup -> !backup.manual() && backup.slot() == 2));
        ServerEntry after = new ServerEntry("two", "two", List.of("two.example"), true,
                "auto", List.of());
        store.save(new Vault(Vault.VERSION, List.of(after)));
        BackupSummary firstSnapshot = store.backups().stream()
                .filter(backup -> !backup.manual() && backup.slot() == 1).findFirst().orElseThrow();
        store.restoreBackup(firstSnapshot);
        assertEquals(1, store.vault().servers().size());
        assertEquals("one.example", store.vault().servers().get(0).hosts().get(0));
        BackupSummary replacedState = store.backups().stream()
                .filter(backup -> !backup.manual() && backup.slot() == 1).findFirst().orElseThrow();
        store.restoreBackup(replacedState);
        assertTrue(store.vault().servers().stream().anyMatch(server -> server.hosts().contains("two.example")));
        assertThrows(IllegalArgumentException.class,
                () -> store.restoreBackup(new BackupSummary(false, 4, 0, 0)));
        store.lock();
        VaultStore reopened = VaultStore.open(directory);
        reopened.unlock("correcthorse".toCharArray());
        assertTrue(reopened.vault().servers().stream().anyMatch(server -> server.hosts().contains("two.example")));
    }

    @Test
    void manualBackupsKeepTenSlotsAndCanBeInspected() throws Exception {
        VaultStore store = VaultStore.open(directory);
        store.create("correcthorse".toCharArray());
        Account account = new Account("Alex", "backup-secret".toCharArray(), Account.Status.VERIFIED,
                10, 20, 30, null);
        ServerEntry server = new ServerEntry("id", "Example", List.of("example.org:25565"), true,
                "auto", List.of(account));
        store.save(new Vault(Vault.VERSION, List.of(server)));

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
        store.save(VaultTrash.trashAccount(store.vault(), "id", "Alex", 40));
        store.restoreBackup(selected);
        assertEquals("backup-secret", new String(store.vault().servers().getFirst().accounts().getFirst().password()));
    }

    @Test
    void damagedMainVaultCanBeRecoveredWithTheSelectedBackupsPassword() throws Exception {
        VaultStore store = VaultStore.open(directory);
        store.create("correcthorse".toCharArray());
        Account account = new Account("Alex", "backup-secret".toCharArray(), Account.Status.VERIFIED,
                10, 20, 30, null);
        store.save(new Vault(Vault.VERSION, List.of(new ServerEntry("id", "Example", List.of("example.org"), true, "auto", List.of(account)))));
        store.createManualBackup();
        BackupSummary selected = store.backups().stream().filter(BackupSummary::manual).findFirst().orElseThrow();
        Files.writeString(directory.resolve("vault.bv"), "damaged");
        assertThrows(WrongMasterPasswordException.class,
                () -> VaultStore.restoreFromBackup(directory, selected, "wronghorse".toCharArray()));
        VaultStore.restoreFromBackup(directory, selected, "correcthorse".toCharArray());
        VaultStore recovered = VaultStore.open(directory);
        recovered.unlock("correcthorse".toCharArray());
        assertEquals("backup-secret", new String(recovered.vault().servers().getFirst().accounts().getFirst().password()));
    }

    @Test
    void passwordChangeReencryptsAutomaticAndManualBackups() throws Exception {
        VaultStore store = VaultStore.open(directory);
        store.create("oldpassword".toCharArray());
        Account account = new Account("Alex", "backup-secret".toCharArray(), Account.Status.VERIFIED,
                1, 1, 1, null);
        store.save(new Vault(Vault.VERSION, List.of(new ServerEntry("one", "one", List.of("one.example"), true, "auto", List.of(account)))));
        store.createManualBackup();
        store.changeMasterPassword("oldpassword".toCharArray(), "newpassword".toCharArray());

        for (BackupSummary backup : store.backups()) {
            Path isolated = Files.createDirectory(directory.resolve("check-" + backup.manual() + "-" + backup.slot()));
            Files.copy(directory.resolve(backup.manual() ? "vault.bv.manual" + backup.slot()
                    : "vault.bv.bak" + backup.slot()), isolated.resolve("vault.bv"));
            VaultStore check = VaultStore.open(isolated);
            assertThrows(WrongMasterPasswordException.class, () -> check.unlock("oldpassword".toCharArray()));
            check.unlock("newpassword".toCharArray());
        }
    }

    @Test
    void masterPasswordChangeRotatesTheVaultKeyAndRefreshesTheTrustFile() throws Exception {
        VaultStore store = VaultStore.open(directory);
        store.create("oldpassword".toCharArray());
        Account account = new Account("Alex", "retained-secret".toCharArray(), Account.Status.VERIFIED,
                1, 1, 1, null);
        store.save(new Vault(Vault.VERSION, List.of(new ServerEntry("one", "one", List.of("one.example"), true, "auto", List.of(account)))));
        assertThrows(WrongMasterPasswordException.class,
                () -> store.changeMasterPassword("incorrect".toCharArray(), "newpassword".toCharArray()));
        assertThrows(IllegalArgumentException.class,
                () -> store.changeMasterPassword("oldpassword".toCharArray(), "short".toCharArray()));
        assertThrows(IllegalArgumentException.class,
                () -> store.changeMasterPassword("oldpassword".toCharArray(), "oldpassword".toCharArray()));
        store.trustThisDevice();
        byte[] oldKeyFile = Files.readAllBytes(directory.resolve("vault.bv.trust"));
        store.changeMasterPassword("oldpassword".toCharArray(), "newpassword".toCharArray());
        assertEquals("retained-secret", new String(store.vault().servers().get(0).accounts().get(0).password()),
                "passwords must remain live immediately after rotating the master password");
        List<ServerEntry> afterRotation = new java.util.ArrayList<>(store.vault().servers());
        afterRotation.add(new ServerEntry("two", "two", List.of("two.example"), true, "auto",
                List.of(new Account("Sam", "second-secret".toCharArray(), Account.Status.VERIFIED, 2, 2, 2, null))));
        store.save(new Vault(Vault.VERSION, afterRotation));
        assertTrue(!java.util.Arrays.equals(oldKeyFile, Files.readAllBytes(directory.resolve("vault.bv.trust"))),
                "the vault key must change with the password");
        store.lock();
        VaultStore viaTrust = VaultStore.open(directory);
        viaTrust.unlockTrustedDevice();
        assertEquals("retained-secret", new String(viaTrust.vault().servers().get(0).accounts().get(0).password()));
        viaTrust.lock();
        VaultStore reopened = VaultStore.open(directory);
        assertThrows(WrongMasterPasswordException.class, () -> reopened.unlock("oldpassword".toCharArray()));
        reopened.unlock("newpassword".toCharArray());
        assertEquals("retained-secret", new String(reopened.vault().servers().get(0).accounts().get(0).password()));
        assertEquals("second-secret", new String(reopened.vault().servers().get(1).accounts().get(0).password()));
    }

    @Test
    void trustedDeviceKeyUnlocksWithoutStoringTheMasterPassword() throws Exception {
        VaultStore store = VaultStore.open(directory);
        store.create("correcthorse".toCharArray());
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
        store.create("correcthorse".toCharArray());
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
        reopened.unlock("correcthorse".toCharArray());
    }

    @Test
    void disablingTrustRemovesTheLocalKeyFile() throws Exception {
        VaultStore store = VaultStore.open(directory);
        store.create("correcthorse".toCharArray());
        store.trustThisDevice();
        store.forgetTrustedDevice();
        assertTrue(Files.notExists(directory.resolve("vault.bv.trust")));
    }

    @Test
    void oneUnreadableBackupDoesNotStopTheOthersFromBeingReencrypted() throws Exception {
        VaultStore store = VaultStore.open(directory);
        store.create("oldpassword".toCharArray());
        store.save(new Vault(Vault.VERSION, List.of(new ServerEntry("one", "one", List.of("one.example"), true,
                "auto", List.of(new Account("Alex", "s".toCharArray(), Account.Status.VERIFIED, 1, 1, 1, null))))));
        store.createManualBackup();
        store.createManualBackup();
        Files.writeString(directory.resolve("vault.bv.manual1"), "not a vault");
        assertThrows(BackupReencryptionException.class,
                () -> store.changeMasterPassword("oldpassword".toCharArray(), "newpassword".toCharArray()));
        // The password change itself was committed, and the healthy backup was re-encrypted.
        Path isolated = Files.createDirectory(directory.resolve("check"));
        Files.copy(directory.resolve("vault.bv.manual2"), isolated.resolve("vault.bv"));
        VaultStore check = VaultStore.open(isolated);
        check.unlock("newpassword".toCharArray());
    }

    @Test
    void trashSurvivesSaveAndReopenAndExpiredEntriesArePurged() throws Exception {
        VaultStore store = VaultStore.open(directory);
        store.create("correcthorse".toCharArray());
        long day = 24L * 60 * 60 * 1000;
        Vault base = new Vault(Vault.VERSION, List.of(new ServerEntry("one", "one", List.of("one.example"), true,
                "auto", List.of(new Account("Alex", "a".toCharArray(), Account.Status.VERIFIED, 1, 1, 1, null),
                        new Account("Sam", "b".toCharArray(), Account.Status.VERIFIED, 1, 1, 1, null)))));
        long now = System.currentTimeMillis();
        Vault withTrash = VaultTrash.trashAccount(VaultTrash.trashAccount(base, "one", "Alex", now - 61 * day),
                "one", "Sam", now - 1 * day);
        store.saveExplicit(withTrash);
        // save() purges anything older than 60 days.
        assertEquals(1, store.vault().trash().size());
        store.lock();
        VaultStore reopened = VaultStore.open(directory);
        reopened.unlock("correcthorse".toCharArray());
        assertEquals("Sam", reopened.vault().trash().getFirst().server().accounts().getFirst().username());
    }

    @Test
    void importPreviewDescribesChangesAndCommitMergesThemWithARestorePoint() throws Exception {
        Path otherDirectory = Files.createDirectory(directory.resolve("other"));
        VaultStore other = VaultStore.open(otherDirectory);
        other.create("otherpassword".toCharArray());
        other.save(new Vault(Vault.VERSION, List.of(
                new ServerEntry("a", "Alpha", List.of("alpha.example:25565"), true, "auto", List.of(
                        new Account("Alex", "newer".toCharArray(), Account.Status.VERIFIED, 1, 500, 0, null),
                        new Account("Sam", "added".toCharArray(), Account.Status.VERIFIED, 1, 500, 0, null))),
                new ServerEntry("b", "Beta", List.of("beta.example:25565"), true, "auto", List.of(
                        new Account("Kim", "k".toCharArray(), Account.Status.VERIFIED, 1, 500, 0, null))))));
        Path exported = directory.resolve("vault.bv.export");
        other.exportEncrypted(exported);

        VaultStore store = VaultStore.open(directory.resolve("mine"));
        store.create("mypassword".toCharArray());
        store.save(new Vault(Vault.VERSION, List.of(new ServerEntry("a2", "Alpha", List.of("alpha.example:25565"),
                true, "auto", List.of(new Account("Alex", "older".toCharArray(), Account.Status.VERIFIED, 1, 100, 0, null))))));
        // exports must live inside the data directory of the exporting store
        Path copy = directory.resolve("mine").resolve("incoming.bv");
        Files.copy(exported, copy);

        ImportPreview preview = store.previewImport(copy, "otherpassword".toCharArray());
        assertEquals(1, preview.count(ImportPreview.Kind.NEW_SERVER));
        assertEquals(2, preview.count(ImportPreview.Kind.NEW_ACCOUNT));
        assertEquals(1, preview.count(ImportPreview.Kind.UPDATED_ACCOUNT));
        // Nothing has changed yet.
        assertEquals("older", new String(store.vault().servers().get(0).accounts().get(0).password()));

        store.commitImport();
        Vault merged = store.vault();
        assertEquals(2, merged.servers().size());
        assertEquals("newer", new String(merged.servers().stream().filter(sv -> sv.name().equals("Alpha")).findFirst()
                .orElseThrow().accounts().stream().filter(a -> a.username().equals("Alex")).findFirst().orElseThrow().password()));
        assertTrue(Files.isRegularFile(directory.resolve("mine").resolve("vault.bv.bak1")), "restore point taken");
    }

    @Test
    void wrongImportPasswordAndDiscardLeaveTheVaultUntouched() throws Exception {
        VaultStore a = VaultStore.open(directory.resolve("a"));
        a.create("passworda".toCharArray());
        Path exported = directory.resolve("exported-a.bv");
        a.exportEncrypted(exported);
        VaultStore b = VaultStore.open(directory.resolve("b"));
        b.create("passwordb".toCharArray());
        Path copy = directory.resolve("b").resolve("incoming.bv");
        Files.copy(exported, copy);
        assertThrows(WrongMasterPasswordException.class, () -> b.previewImport(copy, "nopenope".toCharArray()));
        b.previewImport(copy, "passworda".toCharArray());
        b.discardImport();
        assertThrows(IllegalStateException.class, b::commitImport);
    }

    @Test
    void manualBackupRemainsAnUnmodifiedSnapshotAfterLiveAccountChanges() throws Exception {
        VaultStore store = VaultStore.open(directory);
        store.create("purgepassword".toCharArray());
        ServerEntry server = new ServerEntry("s", "S", List.of("s.example:25565"), true, "auto", List.of(
                new Account("Alex", "gone".toCharArray(), Account.Status.VERIFIED, 1, 1, 0, null),
                new Account("Sam", "stay".toCharArray(), Account.Status.VERIFIED, 1, 1, 0, null)));
        store.save(new Vault(Vault.VERSION, List.of(server)));
        store.createManualBackup();
        store.save(new Vault(Vault.VERSION, List.of(server.withAccounts(List.of(server.accounts().get(1))))));
        Vault backup = store.inspectBackup(store.backups().stream().filter(BackupSummary::manual).findFirst().orElseThrow());
        assertEquals(2, backup.servers().get(0).accounts().size());
        assertEquals("Alex", backup.servers().get(0).accounts().get(0).username());
    }

    @Test
    void anInstanceWithAStaleKeyLocksItselfAndCanUnlockWithTheNewPassword() throws Exception {
        VaultStore first = VaultStore.open(directory);
        first.create("firstpassword".toCharArray());
        VaultStore second = VaultStore.open(directory);
        second.unlock("firstpassword".toCharArray());
        first.changeMasterPassword("firstpassword".toCharArray(), "secondpassword".toCharArray());
        assertThrows(Exception.class, second::reload);
        assertThrows(IllegalStateException.class, second::vault);
        second.unlock("secondpassword".toCharArray());
        assertEquals(0, second.vault().servers().size());
    }
}
