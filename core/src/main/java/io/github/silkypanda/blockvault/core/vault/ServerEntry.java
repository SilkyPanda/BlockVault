package io.github.silkypanda.blockvault.core.vault;

import java.util.List;

/** Immutable saved server: its display name, the hosts it is reached through, and its accounts. */
public record ServerEntry(String id, String name, List<String> hosts, List<Account> accounts) {
    public ServerEntry {
        if (id == null || name == null || hosts == null || accounts == null) {
            throw new IllegalArgumentException("Server fields are invalid");
        }
        hosts = List.copyOf(hosts);
        accounts = List.copyOf(accounts);
    }

    public ServerEntry withAccounts(List<Account> newAccounts) {
        return new ServerEntry(id, name, hosts, newAccounts);
    }
}
