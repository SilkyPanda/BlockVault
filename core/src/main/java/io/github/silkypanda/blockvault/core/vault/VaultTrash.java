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
            servers.add(server.withAccounts(remaining));
        }
        if (entry == null) return vault;
        List<TrashEntry> trash = new ArrayList<>(vault.trash());
        trash.add(entry);
        return new Vault(vault.v(), servers, trash, vault.extra());
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
        for (TrashEntry entry : vault.trash()) {
            if (nowMillis - entry.deletedAt() <= Vault.TRASH_RETENTION_MILLIS) kept.add(entry);
        }
        return kept.size() == vault.trash().size() ? vault : vault.withTrash(kept);
    }

    private static ServerEntry snapshot(ServerEntry server, List<Account> accounts) {
        return server.withAccounts(accounts);
    }

    /**
     * Removes every account matching one in {@code removed} (same host, username and password) from the live
     * servers and from trash snapshots of {@code target}. Used to scrub backups after "delete forever".
     */
    public static Vault purgeAccounts(Vault target, List<ServerEntry> removed) {
        boolean[] changed = {false};
        List<ServerEntry> servers = new ArrayList<>();
        for (ServerEntry server : target.servers()) servers.add(scrub(server, removed, changed));
        List<TrashEntry> trash = new ArrayList<>();
        for (TrashEntry entry : target.trash()) {
            trash.add(new TrashEntry(entry.id(), entry.deletedAt(), entry.wholeServer(),
                    scrub(entry.server(), removed, changed), entry.extra()));
        }
        return changed[0] ? new Vault(target.v(), servers, trash, target.extra()) : target;
    }

    private static ServerEntry scrub(ServerEntry server, List<ServerEntry> removed, boolean[] changed) {
        List<Account> kept = new ArrayList<>();
        for (Account account : server.accounts()) {
            if (matchesAny(server, account, removed)) changed[0] = true;
            else kept.add(account);
        }
        return kept.size() == server.accounts().size() ? server : server.withAccounts(kept);
    }

    private static boolean matchesAny(ServerEntry server, Account account, List<ServerEntry> removed) {
        char[] password = account.password();
        try {
            for (ServerEntry gone : removed) {
                if (gone.hosts().stream().noneMatch(server.hosts()::contains)) continue;
                for (Account other : gone.accounts()) {
                    char[] otherPassword = other.password();
                    try {
                        if (other.username().equals(account.username()) && java.util.Arrays.equals(otherPassword, password)) {
                            return true;
                        }
                    } finally { java.util.Arrays.fill(otherPassword, '\0'); }
                }
            }
            return false;
        } finally { java.util.Arrays.fill(password, '\0'); }
    }
}
