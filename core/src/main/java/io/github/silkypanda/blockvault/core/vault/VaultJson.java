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
        if (root.has("v") && root.get("v").getAsInt() != 1) throw new IllegalArgumentException("Unsupported vault JSON version");
        List<ServerEntry> servers = new ArrayList<>();
        JsonArray serverArray = root.has("servers") && root.get("servers").isJsonArray()
                ? root.getAsJsonArray("servers") : new JsonArray();
        for (JsonElement element : serverArray) {
            JsonObject item = element.getAsJsonObject();
            List<String> hosts = new ArrayList<>();
            if (item.has("hosts") && item.get("hosts").isJsonArray()) item.getAsJsonArray("hosts").forEach(host -> hosts.add(host.getAsString()));
            List<Account> accounts = new ArrayList<>();
            JsonArray accountArray = item.has("accounts") && item.get("accounts").isJsonArray()
                    ? item.getAsJsonArray("accounts") : new JsonArray();
            for (JsonElement accountElement : accountArray) {
                JsonObject value = accountElement.getAsJsonObject();
                char[] password = value.has("pass") ? value.get("pass").getAsString().toCharArray() : new char[0];
                try {
                    Account.Status status;
                    try { status = Account.Status.valueOf(value.has("status") ? value.get("status").getAsString() : "PENDING"); }
                    catch (IllegalArgumentException unknownStatus) { status = Account.Status.PENDING; }
                    accounts.add(new Account(string(value, "user", ""), password, status,
                            number(value, "created", 0), number(value, "modified", 0), number(value, "used", 0),
                            value.has("totp") ? value.get("totp").getAsString() : null, unknown(value, ACCOUNT_KEYS)));
                } finally {
                    java.util.Arrays.fill(password, '\0');
                }
            }
            String template = string(item, "template", "auto");
            // The pre-review schema wrote authme even when the owner had not chosen a template.
            if ("authme".equals(template)) template = "auto";
            servers.add(new ServerEntry(string(item, "id", java.util.UUID.randomUUID().toString()),
                    string(item, "name", "Unknown server"), hosts, bool(item, "neverSave", false),
                    bool(item, "autofill", true), template, accounts,
                    unknown(item, SERVER_KEYS)));
        }
        return new Vault(root.has("v") ? root.get("v").getAsInt() : 1, servers, unknown(root, ROOT_KEYS));
    }

    private static String string(JsonObject object, String key, String fallback) {
        try { return object.has(key) ? object.get(key).getAsString() : fallback; }
        catch (RuntimeException ignored) { return fallback; }
    }

    private static long number(JsonObject object, String key, long fallback) {
        try { return object.has(key) ? object.get(key).getAsLong() : fallback; }
        catch (RuntimeException ignored) { return fallback; }
    }

    private static boolean bool(JsonObject object, String key, boolean fallback) {
        try { return object.has(key) ? object.get(key).getAsBoolean() : fallback; }
        catch (RuntimeException ignored) { return fallback; }
    }

    private static JsonObject unknown(JsonObject object, Set<String> known) {
        JsonObject result = object.deepCopy();
        known.forEach(result::remove);
        return result;
    }
}
