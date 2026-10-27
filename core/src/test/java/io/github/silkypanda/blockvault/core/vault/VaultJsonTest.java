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


    @org.junit.jupiter.api.Test
    void unknownFieldsAtEveryLevelSurviveReadAndWrite() {
        String json = "{\"v\":2,\"futureRoot\":{\"a\":1},\"servers\":[{\"id\":\"s\",\"name\":\"n\",\"hosts\":[\"h:25565\"],"
                + "\"neverSave\":false,\"autofill\":true,\"template\":\"auto\",\"futureServer\":[1,2],"
                + "\"accounts\":[{\"user\":\"Alex\",\"pass\":\"pw\",\"status\":\"VERIFIED\",\"created\":1,"
                + "\"modified\":2,\"used\":3,\"note\":\"keep me\"}]}],"
                + "\"trash\":[{\"id\":\"t\",\"deletedAt\":5,\"whole\":false,\"futureTrash\":true,\"server\":{\"id\":\"s\","
                + "\"name\":\"n\",\"hosts\":[\"h:25565\"],\"neverSave\":false,\"autofill\":true,\"template\":\"auto\","
                + "\"accounts\":[]}}]}";
        Vault vault = VaultJson.read(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String written = new String(VaultJson.write(vault), java.nio.charset.StandardCharsets.UTF_8);
        com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(written).getAsJsonObject();
        org.junit.jupiter.api.Assertions.assertEquals(1, root.getAsJsonObject("futureRoot").get("a").getAsInt());
        com.google.gson.JsonObject server = root.getAsJsonArray("servers").get(0).getAsJsonObject();
        org.junit.jupiter.api.Assertions.assertEquals(2, server.getAsJsonArray("futureServer").size());
        org.junit.jupiter.api.Assertions.assertEquals("keep me",
                server.getAsJsonArray("accounts").get(0).getAsJsonObject().get("note").getAsString());
        org.junit.jupiter.api.Assertions.assertTrue(
                root.getAsJsonArray("trash").get(0).getAsJsonObject().get("futureTrash").getAsBoolean());
    }
}
