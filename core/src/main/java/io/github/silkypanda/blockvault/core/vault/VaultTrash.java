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
            Account found = server.accounts().stream().filter(a -> Usernames.same(a.username(), username)).findFirst()
                    .orElse(null);
            if (found == null) {
                servers.add(server);
                continue;
            }
            entry = new TrashEntry(UUID.randomUUID().toString(), nowMillis, snapshot(server, List.of(found)));
            List<Account> remaining = new ArrayList<>(server.accounts());
            remaining.removeIf(a -> Usernames.same(a.username(), username));
            if (!remaining.isEmpty()) servers.add(server.withAccounts(remaining));
        }
        if (entry == null) return vault;
        List<TrashEntry> trash = new ArrayList<>(vault.trash());
        trash.add(entry);
        return new Vault(vault.v(), servers, trash, vault.extra());
    }

    /**
     * Puts a trash entry back. The explicitly restored account replaces any live account with the same username
     * on a matching host, while unrelated accounts and current server settings are preserved.
     */
    public static Vault restore(Vault vault, String trashId, long nowMillis) {
        TrashEntry entry = vault.trash().stream().filter(t -> t.id().equals(trashId)).findFirst().orElse(null);
        if (entry == null) return vault;
        List<TrashEntry> trash = new ArrayList<>(vault.trash());
        trash.removeIf(t -> t.id().equals(trashId));
        List<ServerEntry> servers = new ArrayList<>(vault.servers());
        ServerEntry restoredServer = entry.server();
        boolean placed = false;
        for (int index = 0; index < servers.size(); index++) {
            ServerEntry live = servers.get(index);
            if (!live.id().equals(restoredServer.id())
                    && live.hosts().stream().noneMatch(restoredServer.hosts()::contains)) continue;
            List<Account> accounts = new ArrayList<>(live.accounts());
            for (Account restored : restoredServer.accounts()) {
                accounts.removeIf(account -> Usernames.same(account.username(), restored.username()));
                accounts.add(restored);
            }
            servers.set(index, live.withAccounts(accounts));
            placed = true;
            break;
        }
        if (!placed) servers.add(restoredServer);
        return new Vault(vault.v(), servers, trash, vault.extra());
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
        boolean changed = false;
        for (TrashEntry entry : vault.trash()) {
            long deletedAt = Math.min(entry.deletedAt(), nowMillis);
            if (nowMillis - deletedAt <= Vault.TRASH_RETENTION_MILLIS) {
                kept.add(deletedAt == entry.deletedAt() ? entry : new TrashEntry(entry.id(), deletedAt,
                        entry.server(), entry.extra()));
                changed |= deletedAt != entry.deletedAt();
            } else changed = true;
        }
        return changed ? vault.withTrash(kept) : vault;
    }

    private static ServerEntry snapshot(ServerEntry server, List<Account> accounts) {
        return server.withAccounts(accounts);
    }

}
