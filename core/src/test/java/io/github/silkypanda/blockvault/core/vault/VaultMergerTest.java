package io.github.silkypanda.blockvault.core.vault;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonObject;
import java.util.List;
import org.junit.jupiter.api.Test;

final class VaultMergerTest {
    @Test
    void unionsAccountsAndChoosesNewerModification() {
        Account old = account("Alex", "old", 10, false);
        Account newer = account("Alex", "new", 20, false);
        Account second = account("Sam", "two", 5, false);
        Vault merged = VaultMerger.merge(vault(old), vault(newer, second), 100);
        assertEquals(2, merged.servers().getFirst().accounts().size());
        assertEquals("new", new String(merged.servers().getFirst().accounts().getFirst().password()));
    }

    @Test
    void purgesExpiredTombstones() {
        long now = 40L * 24 * 60 * 60 * 1000;
        Vault merged = VaultMerger.merge(vault(account("Alex", "", 1, true)), vault(), now);
        assertEquals(0, merged.servers().getFirst().accounts().size());
    }

    private static Account account(String user, String password, long modified, boolean deleted) {
        JsonObject extra = new JsonObject();
        if (deleted) extra.addProperty("deleted", true);
        return new Account(user, password.toCharArray(), Account.Status.PENDING, 1, modified, 0, extra);
    }

    private static Vault vault(Account... accounts) {
        ServerEntry server = new ServerEntry("id", "name", List.of("host:25565"), false, true, "authme",
                List.of(accounts), new JsonObject());
        return new Vault(1, List.of(server), new JsonObject());
    }
}
