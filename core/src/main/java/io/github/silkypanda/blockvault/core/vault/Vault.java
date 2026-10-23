package io.github.silkypanda.blockvault.core.vault;

import com.google.gson.JsonObject;
import java.util.List;

/** Immutable in-memory vault model. */
public record Vault(int v, List<ServerEntry> servers, JsonObject unknownFields) {
    public Vault {
        if (v < 1) {
            throw new IllegalArgumentException("Vault version must be positive");
        }
        servers = List.copyOf(servers);
        unknownFields = unknownFields == null ? new JsonObject() : unknownFields.deepCopy();
    }

    @Override
    public JsonObject unknownFields() { return unknownFields.deepCopy(); }

    /** Returns a new empty v1 vault. */
    public static Vault empty() { return new Vault(1, List.of(), new JsonObject()); }
}
