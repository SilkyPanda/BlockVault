package io.github.silkypanda.blockvault.core.vault;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Gson tree mapping for the current vault schema (version 2: servers plus trash).
 *
 * <p>Fields this version does not know are kept (vault, server, trash entry and account level) and written back
 * unchanged, so a vault touched by a newer build is never silently trimmed by an older one.
 */
public final class VaultJson {
    private static final Set<String> ROOT_KEYS = Set.of("v", "servers", "trash");
    private static final Set<String> SERVER_KEYS = Set.of("id", "name", "hosts", "neverSave", "autofill", "template",
            "accounts");
    private static final Set<String> TRASH_KEYS = Set.of("id", "deletedAt", "whole", "server");
    private static final Set<String> ACCOUNT_KEYS = Set.of("user", "pass", "status", "created", "modified", "used",
            "blockvaultPrevious");

    private VaultJson() { }

    /** Serializes the vault, including any unknown fields that were read earlier. */
    public static byte[] write(Vault vault) {
        JsonObject root = new JsonObject();
        copyUnknown(vault.extra(), root, ROOT_KEYS);
        root.addProperty("v", vault.v());
        JsonArray servers = new JsonArray();
        for (ServerEntry server : vault.servers()) servers.add(writeServer(server));
        root.add("servers", servers);
        JsonArray trash = new JsonArray();
        for (TrashEntry entry : vault.trash()) {
            JsonObject item = new JsonObject();
            copyUnknown(entry.extra(), item, TRASH_KEYS);
            item.addProperty("id", entry.id());
            item.addProperty("deletedAt", entry.deletedAt());
            item.addProperty("whole", entry.wholeServer());
            item.add("server", writeServer(entry.server()));
            trash.add(item);
        }
        root.add("trash", trash);
        return root.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** Parses the current schema; missing required fields and unknown statuses are errors. */
    public static Vault read(byte[] jsonUtf8) {
        JsonObject root = JsonParser.parseString(new String(jsonUtf8, StandardCharsets.UTF_8)).getAsJsonObject();
        int version = root.get("v").getAsInt();
        if (version != Vault.VERSION) throw new IllegalArgumentException("Unsupported vault JSON version");
        List<ServerEntry> servers = new ArrayList<>();
        for (JsonElement element : root.getAsJsonArray("servers")) servers.add(readServer(element.getAsJsonObject()));
        List<TrashEntry> trash = new ArrayList<>();
        for (JsonElement element : root.getAsJsonArray("trash")) {
            JsonObject item = element.getAsJsonObject();
            trash.add(new TrashEntry(item.get("id").getAsString(), item.get("deletedAt").getAsLong(),
                    item.get("whole").getAsBoolean(), readServer(item.getAsJsonObject("server")),
                    unknown(item, TRASH_KEYS)));
        }
        return new Vault(version, servers, trash, unknown(root, ROOT_KEYS));
    }

    private static JsonObject writeServer(ServerEntry server) {
        JsonObject item = new JsonObject();
        copyUnknown(server.extra(), item, SERVER_KEYS);
        item.addProperty("id", server.id());
        item.addProperty("name", server.name());
        JsonArray hosts = new JsonArray();
        server.hosts().forEach(hosts::add);
        item.add("hosts", hosts);
        item.addProperty("neverSave", server.neverSave());
        item.addProperty("autofill", server.autofill());
        item.addProperty("template", server.templateId());
        JsonArray accounts = new JsonArray();
        for (Account account : server.accounts()) {
            JsonObject value = new JsonObject();
            JsonObject metadata = account.metadata();
            copyUnknown(metadata, value, ACCOUNT_KEYS);
            value.addProperty("user", account.username());
            char[] password = account.password();
            try { value.addProperty("pass", new String(password)); }
            finally { java.util.Arrays.fill(password, '\0'); }
            value.addProperty("status", account.status().name());
            value.addProperty("created", account.createdAt());
            value.addProperty("modified", account.modifiedAt());
            value.addProperty("used", account.lastUsedAt());
            if (metadata.has("blockvaultPrevious")) {
                value.add("blockvaultPrevious", metadata.get("blockvaultPrevious").deepCopy());
            }
            accounts.add(value);
        }
        item.add("accounts", accounts);
        return item;
    }

    private static ServerEntry readServer(JsonObject item) {
        List<String> hosts = new ArrayList<>();
        item.getAsJsonArray("hosts").forEach(host -> hosts.add(host.getAsString()));
        List<Account> accounts = new ArrayList<>();
        for (JsonElement accountElement : item.getAsJsonArray("accounts")) {
            JsonObject value = accountElement.getAsJsonObject();
            char[] password = value.get("pass").getAsString().toCharArray();
            try {
                accounts.add(new Account(value.get("user").getAsString(), password,
                        Account.Status.valueOf(value.get("status").getAsString()),
                        value.get("created").getAsLong(), value.get("modified").getAsLong(),
                        value.get("used").getAsLong(), readMetadata(value)));
            } finally {
                java.util.Arrays.fill(password, '\0');
            }
        }
        return new ServerEntry(item.get("id").getAsString(), item.get("name").getAsString(), hosts,
                item.get("neverSave").getAsBoolean(), item.get("autofill").getAsBoolean(),
                item.get("template").getAsString(), accounts, unknown(item, SERVER_KEYS));
    }

    /** Account metadata: the previous-password record plus any unknown account fields. */
    private static JsonObject readMetadata(JsonObject account) {
        JsonObject metadata = unknown(account, ACCOUNT_KEYS);
        if (account.has("blockvaultPrevious")) {
            JsonObject source = account.getAsJsonObject("blockvaultPrevious");
            JsonObject previous = new JsonObject();
            previous.addProperty("pass", source.get("pass").getAsString());
            previous.addProperty("status", source.get("status").getAsString());
            previous.addProperty("created", source.get("created").getAsLong());
            previous.addProperty("modified", source.get("modified").getAsLong());
            previous.addProperty("used", source.get("used").getAsLong());
            metadata.add("blockvaultPrevious", previous);
        }
        return metadata;
    }

    private static JsonObject unknown(JsonObject source, Set<String> known) {
        JsonObject extra = new JsonObject();
        for (Map.Entry<String, JsonElement> entry : source.entrySet()) {
            if (!known.contains(entry.getKey())) extra.add(entry.getKey(), entry.getValue().deepCopy());
        }
        return extra;
    }

    private static void copyUnknown(JsonObject from, JsonObject to, Set<String> known) {
        for (Map.Entry<String, JsonElement> entry : from.entrySet()) {
            if (!known.contains(entry.getKey())) to.add(entry.getKey(), entry.getValue().deepCopy());
        }
    }
}
