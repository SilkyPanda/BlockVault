package io.github.silkypanda.blockvault.core.vault;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Pure functions that move vault entries into and out of the trash. */
public final class VaultTrash {
    private VaultTrash() { }

    /** Moves one account into the trash. Returns the vault unchanged if the account does not exist. */
    public static Vault trashAccount(Vault vault, String serverId, String username, long nowMillis) {
        List<ServerEntry> servers = new ArrayList<>();
        TrashEntry entry = null;
        for (ServerEntry server : vault.servers()) {
            if (!server.id().equals(serverId) || entry != null) {
                servers.add(server);
                continue;
            }
            Account found = server.accounts().stream().filter(a -> a.username().equals(username)).findFirst()
                    .orElse(null);
            if (found == null) {
                servers.add(server);
                continue;
            }
            entry = new TrashEntry(UUID.randomUUID().toString(), nowMillis, false, snapshot(server, List.of(found)));
            List<Account> remaining = new ArrayList<>(server.accounts());
            remaining.removeIf(a -> a.username().equals(username));
            servers.add(new ServerEntry(server.id(), server.name(), server.hosts(), server.neverSave(),
                    server.autofill(), server.templateId(), remaining));
        }
        if (entry == null) return vault;
        List<TrashEntry> trash = new ArrayList<>(vault.trash());
        trash.add(entry);
        return new Vault(vault.v(), servers, trash);
    }

    /** Moves a whole server, with all its accounts, into the trash. */
    public static Vault trashServer(Vault vault, String serverId, long nowMillis) {
        ServerEntry removed = vault.servers().stream().filter(s -> s.id().equals(serverId)).findFirst().orElse(null);
        if (removed == null) return vault;
        List<ServerEntry> servers = new ArrayList<>(vault.servers());
        servers.removeIf(s -> s.id().equals(serverId));
        List<TrashEntry> trash = new ArrayList<>(vault.trash());
        trash.add(new TrashEntry(UUID.randomUUID().toString(), nowMillis, true, removed));
        return new Vault(vault.v(), servers, trash);
    }

    /**
     * Puts a trash entry back. It is merged with whatever is live now using the normal rule: same server (shared
     * host) and same username, newer {@code modifiedAt} wins, live copy wins a tie.
     */
    public static Vault restore(Vault vault, String trashId, long nowMillis) {
        TrashEntry entry = vault.trash().stream().filter(t -> t.id().equals(trashId)).findFirst().orElse(null);
        if (entry == null) return vault;
        List<TrashEntry> trash = new ArrayList<>(vault.trash());
        trash.removeIf(t -> t.id().equals(trashId));
        List<ServerEntry> servers = VaultMerger.mergeServers(vault.servers(), List.of(entry.server()), nowMillis);
        return new Vault(vault.v(), servers, trash);
    }

    /** Permanently removes one trash entry. */
    public static Vault deleteForever(Vault vault, String trashId) {
        List<TrashEntry> trash = new ArrayList<>(vault.trash());
        trash.removeIf(t -> t.id().equals(trashId));
        return vault.withTrash(trash);
    }

    /** Permanently removes everything in the trash. */
    public static Vault emptyTrash(Vault vault) { return vault.withTrash(List.of()); }

    /** Removes trash entries older than {@link Vault#TRASH_RETENTION_MILLIS}. */
    public static Vault purgeExpired(Vault vault, long nowMillis) {
        List<TrashEntry> kept = new ArrayList<>();
        for (TrashEntry entry : vault.trash()) {
            if (nowMillis - entry.deletedAt() <= Vault.TRASH_RETENTION_MILLIS) kept.add(entry);
        }
        return kept.size() == vault.trash().size() ? vault : vault.withTrash(kept);
    }

    private static ServerEntry snapshot(ServerEntry server, List<Account> accounts) {
        return new ServerEntry(server.id(), server.name(), server.hosts(), server.neverSave(), server.autofill(),
                server.templateId(), accounts);
    }
}
