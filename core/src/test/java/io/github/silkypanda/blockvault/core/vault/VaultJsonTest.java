package io.github.silkypanda.blockvault.core.vault;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

final class VaultJsonTest {
    @Test
    void currentSchemaRoundTripPreservesCredentialAndMasksAccountString() {
        Account account = new Account("Alex", "secret".toCharArray(), Account.Status.VERIFIED, 1, 2, 3);
        ServerEntry server = new ServerEntry("id", "server", List.of("example.org:25565"), List.of(account));
        Vault result = VaultJson.read(VaultJson.write(new Vault(Vault.VERSION, List.of(server))));
        assertTrue(VaultJson.write(result).length > 0);
        assertEquals("secret", new String(result.servers().getFirst().accounts().getFirst().password()));
        assertEquals("example.org:25565", result.servers().getFirst().hosts().getFirst());
        org.junit.jupiter.api.Assertions.assertFalse(account.toString().contains("secret"));
    }


    @Test
    void perAccountSettingsRoundTrip() {
        Account account = new Account("Alex", "secret".toCharArray(), Account.Status.PENDING, 1, 2, 3, false, "authme", null);
        ServerEntry server = new ServerEntry("id", "server", List.of("example.org:25565"), List.of(account));
        Account read = VaultJson.read(VaultJson.write(new Vault(Vault.VERSION, List.of(server))))
                .servers().getFirst().accounts().getFirst();
        assertEquals(Account.Status.PENDING, read.status());
        assertEquals(false, read.autofill());
        assertEquals("authme", read.templateId());
    }

    @Test
    void malformedAccountBecomesAnUnusablePlaceholderAndIsWrittenBackUnchanged() {
        String json = "{\"v\":1,\"servers\":[{\"id\":\"s\",\"name\":\"n\",\"hosts\":[\"h:25565\"],\"accounts\":["
                + "{\"user\":\"Alex\",\"pass\":\"pw-12345\",\"status\":\"VERIFIED\",\"created\":1,\"modified\":2,"
                + "\"used\":3,\"autofill\":true,\"template\":\"auto\"},"
                + "{\"user\":\"Broken\",\"status\":\"NOT_A_STATUS\"}]}],\"trash\":[]}";
        Vault vault = VaultJson.read(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        List<Account> accounts = vault.servers().getFirst().accounts();
        assertEquals(2, accounts.size());
        assertTrue(accounts.get(0).isValidForUse());
        org.junit.jupiter.api.Assertions.assertFalse(accounts.get(1).isValidForUse());
        String written = new String(VaultJson.write(vault), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(written.contains("NOT_A_STATUS"));
    }

    @Test
    void otherSchemaVersionsAreRejected() {
        byte[] other = "{\"v\":2,\"servers\":[],\"trash\":[]}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedVaultVersionException.class, () -> VaultJson.read(other));
    }
}
