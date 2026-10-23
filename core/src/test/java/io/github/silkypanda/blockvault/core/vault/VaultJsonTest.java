package io.github.silkypanda.blockvault.core.vault;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.util.List;
import org.junit.jupiter.api.Test;

final class VaultJsonTest {
    @Test
    void roundTripPreservesUnknownFieldsAndMasksAccountString() {
        JsonObject extra = new JsonObject(); extra.addProperty("future", "kept");
        Account account = new Account("Alex", "secret".toCharArray(), Account.Status.VERIFIED, 1, 2, 3, null, extra);
        ServerEntry server = new ServerEntry("id", "server", List.of("example.org:25565"), false, true, "authme", List.of(account), new JsonObject());
        Vault result = VaultJson.read(VaultJson.write(new Vault(1, List.of(server), new JsonObject())));
        assertTrue(VaultJson.write(result).length > 0);
        assertEquals("kept", result.servers().getFirst().accounts().getFirst().unknownFields().get("future").getAsString());
        org.junit.jupiter.api.Assertions.assertFalse(account.toString().contains("secret"));
    }
}
