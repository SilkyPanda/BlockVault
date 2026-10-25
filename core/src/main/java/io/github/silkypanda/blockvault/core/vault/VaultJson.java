package io.github.silkypanda.blockvault.core.vault;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Gson tree mapping for the current vault schema (version 2: servers plus trash). */
public final class VaultJson {
    private VaultJson() { }

    /** Serializes only fields used by the current vault format. */
    public static byte[] write(Vault vault) {
        JsonObject root = new JsonObject();
        root.addProperty("v", vault.v());
        JsonArray servers = new JsonArray();
        for (ServerEntry server : vault.servers()) servers.add(writeServer(server));
        root.add("servers", servers);
        JsonArray trash = new JsonArray();
        for (TrashEntry entry : vault.trash()) {
            JsonObject item = new JsonObject();
            item.addProperty("id", entry.id());
            item.addProperty("deletedAt", entry.deletedAt());
            item.addProperty("whole", entry.wholeServer());
            item.add("server", writeServer(entry.server()));
            trash.add(item);
        }
        root.add("trash", trash);
        return root.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** Parses the complete current schema; missing required fields and unknown statuses are errors. */
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
                    item.get("whole").getAsBoolean(), readServer(item.getAsJsonObject("server"))));
        }
        return new Vault(version, servers, trash);
    }

    private static JsonObject writeServer(ServerEntry server) {
        JsonObject item = new JsonObject();
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
            value.addProperty("user", account.username());
            char[] password = account.password();
            try { value.addProperty("pass", new String(password)); }
            finally { java.util.Arrays.fill(password, '\0'); }
            value.addProperty("status", account.status().name());
            value.addProperty("created", account.createdAt());
            value.addProperty("modified", account.modifiedAt());
            value.addProperty("used", account.lastUsedAt());
            JsonObject metadata = account.metadata();
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
                item.get("template").getAsString(), accounts);
    }

    private static JsonObject readMetadata(JsonObject account) {
        JsonObject metadata = new JsonObject();
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
}
