package io.github.silkypanda.blockvault.core.vault;

import java.util.List;

/** Immutable in-memory vault model: live servers plus the trash. */
public record Vault(int v, List<ServerEntry> servers, List<TrashEntry> trash, com.google.gson.JsonObject extra) {
    /** Current on-disk schema version. */
    public static final int VERSION = 2;
    /** Trash entries older than this are removed automatically (60 days). */
    public static final long TRASH_RETENTION_MILLIS = 60L * 24 * 60 * 60 * 1000;

    public Vault {
        if (v < 1) {
            throw new IllegalArgumentException("Vault version must be positive");
        }
        servers = List.copyOf(servers);
        trash = List.copyOf(trash);
        extra = extra == null ? new com.google.gson.JsonObject() : extra.deepCopy();
    }

    /** Creates a vault with no unknown top-level fields. */
    public Vault(int v, List<ServerEntry> servers, List<TrashEntry> trash) { this(v, servers, trash, null); }

    @Override
    public com.google.gson.JsonObject extra() { return extra.deepCopy(); }

    /** Creates a vault with an empty trash. */
    public Vault(int v, List<ServerEntry> servers) { this(v, servers, List.of()); }

    /** Returns a new empty vault in the current schema. */
    public static Vault empty() { return new Vault(VERSION, List.of(), List.of(), null); }

    /** Returns a copy with the given servers and the same trash. */
    public Vault withServers(List<ServerEntry> newServers) { return new Vault(v, newServers, trash, extra); }

    /** Returns a copy with the given trash and the same servers. */
    public Vault withTrash(List<TrashEntry> newTrash) { return new Vault(v, servers, newTrash, extra); }
}
