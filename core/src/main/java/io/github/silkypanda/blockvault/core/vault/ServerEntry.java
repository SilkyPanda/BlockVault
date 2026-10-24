package io.github.silkypanda.blockvault.core.vault;

import java.util.List;

/** Immutable saved server, host aliases, and its accounts. */
public record ServerEntry(String id, String name, List<String> hosts, boolean neverSave, boolean autofill,
                          String templateId, List<Account> accounts) {
    public ServerEntry {
        if (id == null || name == null || templateId == null) {
            throw new IllegalArgumentException("Server fields must not be null");
        }
        hosts = List.copyOf(hosts);
        accounts = List.copyOf(accounts);
    }
}
