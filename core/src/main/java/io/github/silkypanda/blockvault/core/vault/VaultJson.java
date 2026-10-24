package io.github.silkypanda.blockvault.core.vault;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Gson tree mapping for the current vault schema and its account deletion metadata. */
public final class VaultJson {
    private VaultJson() { }

    /** Serializes only fields used by the current vault format. */
    public static byte[] write(Vault vault) {
        JsonObject root = new JsonObject();
        root.addProperty("v", vault.v());
        JsonArray servers = new JsonArray();
        for (ServerEntry server : vault.servers()) {
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
                if (metadata.has("deleted")) value.addProperty("deleted", metadata.get("deleted").getAsBoolean());
                if (metadata.has("blockvaultPrevious")) {
                    value.add("blockvaultPrevious", metadata.get("blockvaultPrevious").deepCopy());
                }
                accounts.add(value);
            }
            item.add("accounts", accounts);
            servers.add(item);
        }
        root.add("servers", servers);
        return root.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** Parses the complete current schema; missing required fields and unknown statuses are errors. */
    public static Vault read(byte[] jsonUtf8) {
        JsonObject root = JsonParser.parseString(new String(jsonUtf8, StandardCharsets.UTF_8)).getAsJsonObject();
        int version = root.get("v").getAsInt();
        if (version != 1) throw new IllegalArgumentException("Unsupported vault JSON version");
        JsonArray serverArray = root.getAsJsonArray("servers");
        List<ServerEntry> servers = new ArrayList<>();
        for (JsonElement element : serverArray) {
            JsonObject item = element.getAsJsonObject();
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
            servers.add(new ServerEntry(item.get("id").getAsString(), item.get("name").getAsString(), hosts,
                    item.get("neverSave").getAsBoolean(), item.get("autofill").getAsBoolean(),
                    item.get("template").getAsString(), accounts));
        }
        return new Vault(version, servers);
    }

    private static JsonObject readMetadata(JsonObject account) {
        JsonObject metadata = new JsonObject();
        if (account.has("deleted")) metadata.addProperty("deleted", account.get("deleted").getAsBoolean());
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
