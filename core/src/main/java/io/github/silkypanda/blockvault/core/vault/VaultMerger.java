package io.github.silkypanda.blockvault.core.vault;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Merges vault snapshots while retaining newer account edits and deletion tombstones. */
public final class VaultMerger {
    private static final long TOMBSTONE_LIFETIME_MILLIS = 30L * 24 * 60 * 60 * 1000;

    private VaultMerger() { }

    /** Unions server and account records, choosing the larger modification time on conflict. */
    public static Vault merge(Vault current, Vault onDisk, long nowMillis) {
        Map<String, ServerEntry> merged = new LinkedHashMap<>();
        for (ServerEntry server : onDisk.servers()) merged.put(server.id(), server);
        for (ServerEntry server : current.servers()) {
            ServerEntry disk = merged.get(server.id());
            merged.put(server.id(), disk == null ? server : mergeServer(server, disk, nowMillis));
        }
        return new Vault(Math.max(current.v(), onDisk.v()), new ArrayList<>(merged.values()), mergeUnknown(current.unknownFields(), onDisk.unknownFields()));
    }

    private static ServerEntry mergeServer(ServerEntry current, ServerEntry disk, long nowMillis) {
        Map<String, Account> accounts = new LinkedHashMap<>();
        for (Account account : disk.accounts()) accounts.put(account.username(), account);
        for (Account account : current.accounts()) {
            Account old = accounts.get(account.username());
            if (old == null || account.modifiedAt() >= old.modifiedAt()) accounts.put(account.username(), account);
        }
        accounts.entrySet().removeIf(entry -> expiredTombstone(entry.getValue(), nowMillis));
        Set<String> hosts = new LinkedHashSet<>(disk.hosts());
        hosts.addAll(current.hosts());
        return new ServerEntry(current.id(), current.name(), List.copyOf(hosts), current.neverSave(), current.autofill(),
                current.templateId(), new ArrayList<>(accounts.values()), mergeUnknown(current.unknownFields(), disk.unknownFields()));
    }

    private static boolean expiredTombstone(Account account, long nowMillis) {
        JsonObject unknown = account.unknownFields();
        return unknown.has("deleted") && unknown.get("deleted").getAsBoolean()
                && nowMillis - account.modifiedAt() > TOMBSTONE_LIFETIME_MILLIS;
    }

    private static JsonObject mergeUnknown(JsonObject preferred, JsonObject older) {
        JsonObject result = older.deepCopy();
        preferred.entrySet().forEach(entry -> result.add(entry.getKey(), entry.getValue().deepCopy()));
        return result;
    }
}
