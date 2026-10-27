package io.github.silkypanda.blockvault.core.vault;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Merges two vault snapshots.
 *
 * <p>Conflict rule: two accounts are the same if they belong to the same server (the two server entries share at
 * least one host) and have the same username. The one with the newer {@code modifiedAt} wins; on an exact tie the
 * {@code current} copy wins. Timestamps in the future are treated as "now" so an imported file cannot win forever.
 * Entries that exist on only one side are kept, so merging never deletes anything; deleting is done by moving
 * entries to the trash. Trash entries are unioned by id and expire after 60 days.
 */
public final class VaultMerger {
    private VaultMerger() { }

    /** Merges {@code current} (wins ties) with {@code onDisk}. */
    public static Vault merge(Vault current, Vault onDisk, long nowMillis) {
        List<ServerEntry> servers = mergeServers(current.servers(), onDisk.servers(), nowMillis);
        Map<String, TrashEntry> trash = new LinkedHashMap<>();
        for (TrashEntry entry : onDisk.trash()) trash.put(entry.id(), entry);
        for (TrashEntry entry : current.trash()) trash.put(entry.id(), entry);
        Vault merged = new Vault(Math.max(current.v(), onDisk.v()), servers, new ArrayList<>(trash.values()), current.extra());
        return VaultTrash.purgeExpired(merged, nowMillis);
    }

    /** Server-level merge used by {@link #merge} and by restoring from the trash. */
    static List<ServerEntry> mergeServers(List<ServerEntry> current, List<ServerEntry> other, long nowMillis) {
        Map<String, ServerEntry> merged = new LinkedHashMap<>();
        for (ServerEntry server : other) merged.put(server.id(), server);
        for (ServerEntry server : current) {
            String matchingId = merged.containsKey(server.id()) ? server.id() : merged.values().stream()
                    .filter(candidate -> sharesHost(server, candidate)).map(ServerEntry::id).findFirst().orElse(null);
            ServerEntry match = matchingId == null ? null : merged.remove(matchingId);
            merged.put(server.id(), match == null ? server : mergeServer(server, match, nowMillis));
        }
        return new ArrayList<>(merged.values());
    }

    private static boolean sharesHost(ServerEntry left, ServerEntry right) {
        return left.hosts().stream().anyMatch(right.hosts()::contains);
    }

    private static ServerEntry mergeServer(ServerEntry current, ServerEntry other, long nowMillis) {
        Map<String, Account> accounts = new LinkedHashMap<>();
        for (Account account : other.accounts()) accounts.put(account.username(), account);
        for (Account account : current.accounts()) {
            Account old = accounts.get(account.username());
            if (old == null || effective(account, nowMillis) >= effective(old, nowMillis)) {
                accounts.put(account.username(), account);
            }
        }
        // Server settings and host list always come from the current side: servers carry no timestamp, and a
        // foreign file must not be able to add host aliases to an existing server.
        return current.withAccounts(new ArrayList<>(accounts.values()));
    }

    private static long effective(Account account, long nowMillis) {
        return Math.min(account.modifiedAt(), nowMillis);
    }

    /** Describes what {@link #merge} would change in {@code current} if {@code other} were merged in. */
    public static ImportPreview diff(Vault current, Vault other, long nowMillis) {
        List<ImportPreview.Change> changes = new ArrayList<>();
        for (ServerEntry incoming : other.servers()) {
            ServerEntry local = current.servers().stream()
                    .filter(candidate -> candidate.id().equals(incoming.id()) || sharesHost(candidate, incoming))
                    .findFirst().orElse(null);
            if (local == null) {
                changes.add(new ImportPreview.Change(ImportPreview.Kind.NEW_SERVER, incoming.name(), null));
                for (Account account : incoming.accounts()) {
                    changes.add(new ImportPreview.Change(ImportPreview.Kind.NEW_ACCOUNT, incoming.name(), account.username()));
                }
                continue;
            }
            for (Account account : incoming.accounts()) {
                Account mine = local.accounts().stream().filter(a -> a.username().equals(account.username()))
                        .findFirst().orElse(null);
                if (mine == null) {
                    changes.add(new ImportPreview.Change(ImportPreview.Kind.NEW_ACCOUNT, local.name(), account.username()));
                    continue;
                }
                char[] left = mine.password();
                char[] right = account.password();
                boolean same = java.util.Arrays.equals(left, right);
                java.util.Arrays.fill(left, '\0');
                java.util.Arrays.fill(right, '\0');
                if (same) continue;
                boolean importedWins = effective(mine, nowMillis) < effective(account, nowMillis);
                changes.add(new ImportPreview.Change(importedWins ? ImportPreview.Kind.UPDATED_ACCOUNT
                        : ImportPreview.Kind.KEPT_LOCAL, local.name(), account.username()));
            }
        }
        for (TrashEntry entry : other.trash()) {
            boolean known = current.trash().stream().anyMatch(t -> t.id().equals(entry.id()));
            boolean expired = nowMillis - entry.deletedAt() > Vault.TRASH_RETENTION_MILLIS;
            if (!known && !expired) {
                changes.add(new ImportPreview.Change(ImportPreview.Kind.NEW_TRASH, entry.server().name(), null));
            }
        }
        return new ImportPreview(changes);
    }
}
