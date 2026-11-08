package io.github.silkypanda.blockvault.core.vault;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Gson tree mapping for the vault schema (version 1: servers plus trash).
 *
 * <p>A malformed account record does not make the whole vault unreadable: it is read as an unusable placeholder
 * that keeps the original JSON text and is written back unchanged.
 */
public final class VaultJson {
    private VaultJson() { }

    /** Serializes the vault. */
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
            item.add("server", writeServer(entry.server()));
            trash.add(item);
        }
        root.add("trash", trash);
        return root.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** Parses the schema; missing required fields and unknown statuses are errors. */
    public static Vault read(byte[] jsonUtf8) {
        try { return readCurrent(jsonUtf8); }
        catch (UnsupportedVaultVersionException version) { throw version; }
        catch (RuntimeException malformed) { throw new IllegalArgumentException("Malformed vault JSON", malformed); }
    }

    private static Vault readCurrent(byte[] jsonUtf8) {
        JsonObject root = JsonParser.parseString(new String(jsonUtf8, StandardCharsets.UTF_8)).getAsJsonObject();
        int version = root.get("v").getAsInt();
        if (version != Vault.VERSION) throw new UnsupportedVaultVersionException();
        List<ServerEntry> servers = new ArrayList<>();
        for (JsonElement element : root.getAsJsonArray("servers")) servers.add(readServer(element.getAsJsonObject()));
        List<TrashEntry> trash = new ArrayList<>();
        for (JsonElement element : root.getAsJsonArray("trash")) {
            JsonObject item = element.getAsJsonObject();
            trash.add(new TrashEntry(item.get("id").getAsString(), item.get("deletedAt").getAsLong(),
                    readServer(item.getAsJsonObject("server"))));
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
        JsonArray accounts = new JsonArray();
        for (Account account : server.accounts()) accounts.add(writeAccount(account));
        item.add("accounts", accounts);
        return item;
    }

    private static JsonElement writeAccount(Account account) {
        if (account.invalidRaw() != null) return JsonParser.parseString(account.invalidRaw());
        JsonObject value = new JsonObject();
        value.addProperty("user", account.username());
        char[] password = account.password();
        try { value.addProperty("pass", new String(password)); }
        finally { java.util.Arrays.fill(password, '\0'); }
        value.addProperty("status", account.status().name());
        value.addProperty("created", account.createdAt());
        value.addProperty("modified", account.modifiedAt());
        value.addProperty("used", account.lastUsedAt());
        value.addProperty("autofill", account.autofill());
        value.addProperty("template", account.templateId());
        return value;
    }

    private static ServerEntry readServer(JsonObject item) {
        List<String> hosts = new ArrayList<>();
        item.getAsJsonArray("hosts").forEach(host -> hosts.add(host.getAsString()));
        List<Account> accounts = new ArrayList<>();
        for (JsonElement accountElement : item.getAsJsonArray("accounts")) accounts.add(readAccount(accountElement));
        return new ServerEntry(item.get("id").getAsString(), item.get("name").getAsString(), hosts, accounts);
    }

    private static Account readAccount(JsonElement element) {
        try {
            JsonObject value = element.getAsJsonObject();
            char[] password = value.get("pass").getAsString().toCharArray();
            try {
                return new Account(value.get("user").getAsString(), password,
                        Account.Status.valueOf(value.get("status").getAsString()),
                        value.get("created").getAsLong(), value.get("modified").getAsLong(),
                        value.get("used").getAsLong(), value.get("autofill").getAsBoolean(),
                        value.get("template").getAsString(), null);
            } finally { java.util.Arrays.fill(password, '\0'); }
        } catch (RuntimeException invalidAccount) {
            return Account.invalid(element.toString());
        }
    }
}
