package io.github.silkypanda.blockvault.core.vault;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

final class VaultJsonTest {
    @Test
    void currentSchemaRoundTripPreservesCredentialAndMasksAccountString() {
        Account account = new Account("Alex", "secret".toCharArray(), Account.Status.VERIFIED, 1, 2, 3, null);
        ServerEntry server = new ServerEntry("id", "server", List.of("example.org:25565"), false, true, "authme", List.of(account));
        Vault result = VaultJson.read(VaultJson.write(new Vault(Vault.VERSION, List.of(server))));
        assertTrue(VaultJson.write(result).length > 0);
        assertEquals("secret", new String(result.servers().getFirst().accounts().getFirst().password()));
        assertEquals("example.org:25565", result.servers().getFirst().hosts().getFirst());
        org.junit.jupiter.api.Assertions.assertFalse(account.toString().contains("secret"));
    }

}
