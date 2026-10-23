package io.github.silkypanda.blockvault.core.vault;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Hand-written Gson tree mapping that preserves unknown fields. */
public final class VaultJson {
    private static final Set<String> ROOT_KEYS = Set.of("v", "servers");
    private static final Set<String> SERVER_KEYS = Set.of("id", "name", "hosts", "neverSave", "autofill", "template", "accounts");
    private static final Set<String> ACCOUNT_KEYS = Set.of("user", "pass", "status", "created", "modified", "used", "totp");

    private VaultJson() { }

    /** Serializes model fields using Gson's JSON tree API. */
    public static byte[] write(Vault vault) {
        JsonObject root = vault.unknownFields();
        root.addProperty("v", vault.v());
        JsonArray servers = new JsonArray();
        for (ServerEntry server : vault.servers()) {
            JsonObject item = server.unknownFields();
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
                JsonObject value = account.unknownFields();
                value.addProperty("user", account.username());
                char[] password = account.password();
                try { value.addProperty("pass", new String(password)); }
                finally { java.util.Arrays.fill(password, '\0'); }
                value.addProperty("status", account.status().name());
                value.addProperty("created", account.createdAt());
                value.addProperty("modified", account.modifiedAt());
                value.addProperty("used", account.lastUsedAt());
                if (account.totpSecret() == null) value.remove("totp");
                else value.addProperty("totp", account.totpSecret());
                accounts.add(value);
            }
            item.add("accounts", accounts);
            servers.add(item);
        }
        root.add("servers", servers);
        return root.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** Parses a vault JSON tree without reflection and retains unrecognized fields. */
    public static Vault read(byte[] jsonUtf8) {
        JsonObject root = JsonParser.parseString(new String(jsonUtf8, StandardCharsets.UTF_8)).getAsJsonObject();
        List<ServerEntry> servers = new ArrayList<>();
        for (JsonElement element : root.getAsJsonArray("servers")) {
            JsonObject item = element.getAsJsonObject();
            List<String> hosts = new ArrayList<>();
            item.getAsJsonArray("hosts").forEach(host -> hosts.add(host.getAsString()));
            List<Account> accounts = new ArrayList<>();
            for (JsonElement accountElement : item.getAsJsonArray("accounts")) {
                JsonObject value = accountElement.getAsJsonObject();
                char[] password = value.get("pass").getAsString().toCharArray();
                try {
                    accounts.add(new Account(value.get("user").getAsString(), password,
                            Account.Status.valueOf(value.get("status").getAsString()), value.get("created").getAsLong(),
                            value.get("modified").getAsLong(), value.get("used").getAsLong(),
                            value.has("totp") ? value.get("totp").getAsString() : null, unknown(value, ACCOUNT_KEYS)));
                } finally {
                    java.util.Arrays.fill(password, '\0');
                }
            }
            servers.add(new ServerEntry(item.get("id").getAsString(), item.get("name").getAsString(), hosts,
                    item.get("neverSave").getAsBoolean(), item.get("autofill").getAsBoolean(),
                    item.get("template").getAsString(), accounts, unknown(item, SERVER_KEYS)));
        }
        return new Vault(root.get("v").getAsInt(), servers, unknown(root, ROOT_KEYS));
    }

    private static JsonObject unknown(JsonObject object, Set<String> known) {
        JsonObject result = object.deepCopy();
        known.forEach(result::remove);
        return result;
    }
}
