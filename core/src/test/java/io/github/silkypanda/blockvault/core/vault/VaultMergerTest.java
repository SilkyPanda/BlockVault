package io.github.silkypanda.blockvault.core.vault;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

final class VaultMergerTest {
    private static final long NOW = 1_000_000_000L;

    @Test
    void sameServerAndUserKeepsTheNewerCopy() {
        Vault merged = VaultMerger.merge(vault(account("Alex", "old", 10)), vault(account("Alex", "new", 20),
                account("Sam", "two", 5)), NOW);
        assertEquals(2, merged.servers().getFirst().accounts().size());
        assertEquals("new", password(merged, "Alex"));
        assertEquals("two", password(merged, "Sam"));
    }

    @Test
    void currentCopyWinsAnExactTie() {
        Vault merged = VaultMerger.merge(vault(account("Alex", "mine", 10)), vault(account("Alex", "theirs", 10)), NOW);
        assertEquals("mine", password(merged, "Alex"));
    }

    @Test
    void differentUsersOnTheSameServerAreBothKept() {
        Vault merged = VaultMerger.merge(vault(account("Alex", "a", 1)), vault(account("Sam", "s", 1)), NOW);
        assertEquals(2, merged.servers().getFirst().accounts().size());
    }

    @Test
    void futureTimestampsDoNotWinForever() {
        // An imported copy claims to be modified far in the future, but so does the local one; both clamp to now,
        // so the tie goes to the local copy.
        Vault merged = VaultMerger.merge(vault(account("Alex", "mine", NOW + 5)),
                vault(account("Alex", "theirs", NOW + 999_999)), NOW);
        assertEquals("mine", password(merged, "Alex"));
    }

    @Test
    void importedHostAliasesAreNotAddedToAnExistingServer() {
        ServerEntry mine = new ServerEntry("id", "name", List.of("host:25565"), false, true, "auto", List.of());
        ServerEntry theirs = new ServerEntry("other", "name", List.of("host:25565", "evil.example:25565"), false, true,
                "auto", List.of());
        Vault merged = VaultMerger.merge(new Vault(Vault.VERSION, List.of(mine)), new Vault(Vault.VERSION, List.of(theirs)),
                NOW);
        assertEquals(List.of("host:25565"), merged.servers().getFirst().hosts());
    }

    @Test
    void trashIsUnionedAndExpiredEntriesAreDropped() {
        long day = 24L * 60 * 60 * 1000;
        Vault a = VaultTrash.trashAccount(vault(account("Alex", "x", 1)), "id", "Alex", NOW - 70 * day);
        Vault b = VaultTrash.trashAccount(vault(account("Sam", "y", 1)), "id", "Sam", NOW - 5 * day);
        Vault merged = VaultMerger.merge(a, b, NOW);
        assertEquals(1, merged.trash().size());
        assertEquals("Sam", merged.trash().getFirst().server().accounts().getFirst().username());
    }

    @Test
    void mergingNeverDeletesAnything() {
        Vault withAccount = vault(account("Alex", "x", 1));
        Vault trashed = VaultTrash.trashAccount(withAccount, "id", "Alex", NOW);
        // The older copy still has the account, so it comes back. That is the accepted trade-off.
        Vault merged = VaultMerger.merge(trashed, withAccount, NOW);
        assertEquals("x", password(merged, "Alex"));
    }

    private static String password(Vault vault, String user) {
        return vault.servers().getFirst().accounts().stream().filter(a -> a.username().equals(user)).findFirst()
                .map(a -> new String(a.password())).orElseThrow();
    }

    private static Account account(String user, String password, long modified) {
        return new Account(user, password.toCharArray(), Account.Status.PENDING, 1, modified, 0, null);
    }

    private static Vault vault(Account... accounts) {
        ServerEntry server = new ServerEntry("id", "name", List.of("host:25565"), false, true, "auto",
                List.of(accounts));
        return new Vault(Vault.VERSION, List.of(server));
    }
}
