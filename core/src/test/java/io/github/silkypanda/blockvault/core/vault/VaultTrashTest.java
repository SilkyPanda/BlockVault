package io.github.silkypanda.blockvault.core.vault;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

final class VaultTrashTest {
    private static final long DAY = 24L * 60 * 60 * 1000;

    @Test
    void deletedAccountMovesToTrashWithItsPasswordAndCanBeRestored() {
        Vault vault = vault(account("Alex", "secret", 10), account("Sam", "other", 10));
        Vault trashed = VaultTrash.trashAccount(vault, "id", "Alex", 1000);
        assertEquals(1, trashed.servers().getFirst().accounts().size());
        assertEquals(1, trashed.trash().size());
        TrashEntry entry = trashed.trash().getFirst();
        assertEquals(false, entry.wholeServer());
        assertEquals("secret", new String(entry.server().accounts().getFirst().password()));

        Vault restored = VaultTrash.restore(trashed, entry.id(), 2000);
        assertEquals(0, restored.trash().size());
        assertEquals(2, restored.servers().getFirst().accounts().size());
    }

    @Test
    void deletingTheLastAccountLeavesItsServerSettingsLive() {
        ServerEntry server = vault(account("Alex", "a", 1)).servers().getFirst();
        Vault trashed = VaultTrash.trashAccount(vault(account("Alex", "a", 1)), "id", "Alex", 1000);
        assertEquals(0, trashed.servers().getFirst().accounts().size());
        assertEquals(server.id(), trashed.servers().getFirst().id());
        assertEquals(server.name(), trashed.servers().getFirst().name());
        assertEquals(server.hosts(), trashed.servers().getFirst().hosts());
        assertEquals(server.autofill(), trashed.servers().getFirst().autofill());
        assertEquals(server.neverSave(), trashed.servers().getFirst().neverSave());
        assertEquals(1, trashed.trash().getFirst().server().accounts().size());
    }

    @Test
    void legacyWholeServerTrashEntriesCanStillBeRestored() {
        ServerEntry oldServer = vault(account("Alex", "a", 1), account("Sam", "b", 1)).servers().getFirst();
        Vault trashed = new Vault(Vault.VERSION, List.of(), List.of(new TrashEntry("legacy", 1000, true, oldServer)));
        assertTrue(trashed.trash().getFirst().wholeServer());
        assertEquals(2, trashed.trash().getFirst().server().accounts().size());
        Vault restored = VaultTrash.restore(trashed, "legacy", 2000);
        assertEquals(1, restored.servers().size());
        assertEquals(2, restored.servers().getFirst().accounts().size());
    }

    @Test
    void restoringOverALiveAccountUsesTheNewerOne() {
        Vault trashed = VaultTrash.trashAccount(vault(account("Alex", "old", 10)), "id", "Alex", 1000);
        // The user registered Alex again, with a newer password, while the old one sat in the trash.
        Vault live = new Vault(Vault.VERSION, List.of(new ServerEntry("id", "name", List.of("host:25565"), false, true,
                "auto", List.of(account("Alex", "new", 50)))), trashed.trash());
        Vault restored = VaultTrash.restore(live, trashed.trash().getFirst().id(), 2000);
        assertEquals("new", new String(restored.servers().getFirst().accounts().getFirst().password()));
        assertEquals(0, restored.trash().size());
    }

    @Test
    void entriesExpireAfterSixtyDaysAndNotBefore() {
        Vault trashed = VaultTrash.trashAccount(vault(account("Alex", "a", 1)), "id", "Alex", 0);
        assertSame(trashed, VaultTrash.purgeExpired(trashed, 60 * DAY));
        assertEquals(0, VaultTrash.purgeExpired(trashed, 60 * DAY + 1).trash().size());
    }

    @Test
    void deleteForeverAndEmptyTrash() {
        Vault v = VaultTrash.trashAccount(vault(account("Alex", "a", 1), account("Sam", "b", 1)), "id", "Alex", 1);
        v = VaultTrash.trashAccount(v, "id", "Sam", 2);
        assertEquals(2, v.trash().size());
        assertEquals(1, VaultTrash.deleteForever(v, v.trash().getFirst().id()).trash().size());
        assertEquals(0, VaultTrash.emptyTrash(v).trash().size());
    }

    @Test
    void unknownAccountLeavesTheVaultUnchanged() {
        Vault vault = vault(account("Alex", "a", 1));
        assertSame(vault, VaultTrash.trashAccount(vault, "id", "Nobody", 1));
    }

    private static Account account(String user, String password, long modified) {
        return new Account(user, password.toCharArray(), Account.Status.VERIFIED, 1, modified, 0, null);
    }

    private static Vault vault(Account... accounts) {
        return new Vault(Vault.VERSION, List.of(new ServerEntry("id", "name", List.of("host:25565"), false, true,
                "auto", List.of(accounts))));
    }
}
