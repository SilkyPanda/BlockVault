package io.github.silkypanda.blockvault.core.vault;

import java.util.List;

/** Immutable in-memory vault model. */
public record Vault(int v, List<ServerEntry> servers) {
    public Vault {
        if (v < 1) {
            throw new IllegalArgumentException("Vault version must be positive");
        }
        servers = List.copyOf(servers);
    }

    /** Returns a new empty v1 vault. */
    public static Vault empty() { return new Vault(1, List.of()); }
}
