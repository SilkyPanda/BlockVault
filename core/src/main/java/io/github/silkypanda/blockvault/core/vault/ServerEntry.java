package io.github.silkypanda.blockvault.core.vault;

import com.google.gson.JsonObject;
import io.github.silkypanda.blockvault.core.host.HostKey;
import io.github.silkypanda.blockvault.core.validation.AsciiInput;
import java.util.List;

/**
 * Immutable saved server, host aliases, and its accounts.
 *
 * <p>{@code extra} holds fields written by newer versions that this version does not understand; they are kept
 * and written back unchanged so mixed versions never lose data.
 */
public record ServerEntry(String id, String name, List<String> hosts, boolean autofill,
                          String templateId, List<Account> accounts, JsonObject extra) {
    public ServerEntry {
        if (id == null || name == null || !AsciiInput.isPrintable(name) || templateId == null
                || hosts == null || hosts.stream().anyMatch(host -> HostKey.normalize(host).isEmpty())) {
            throw new IllegalArgumentException("Server fields are invalid");
        }
        hosts = List.copyOf(hosts);
        accounts = List.copyOf(accounts);
        extra = extra == null ? new JsonObject() : extra.deepCopy();
    }

    /** Creates a server with no unknown fields. */
    public ServerEntry(String id, String name, List<String> hosts, boolean autofill,
                       String templateId, List<Account> accounts) {
        this(id, name, hosts, autofill, templateId, accounts, null);
    }

    @Override
    public JsonObject extra() { return extra.deepCopy(); }

    public ServerEntry withAccounts(List<Account> newAccounts) {
        return new ServerEntry(id, name, hosts, autofill, templateId, newAccounts, extra);
    }

    public ServerEntry withTemplate(String newTemplateId) {
        return new ServerEntry(id, name, hosts, autofill, newTemplateId, accounts, extra);
    }

    public ServerEntry withAutofill(boolean newAutofill) {
        return new ServerEntry(id, name, hosts, newAutofill, templateId, accounts, extra);
    }
}
