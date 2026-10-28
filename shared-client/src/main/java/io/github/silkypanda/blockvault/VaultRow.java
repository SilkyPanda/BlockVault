package io.github.silkypanda.blockvault;

import io.github.silkypanda.blockvault.core.vault.Account;

/** Secret-free account metadata used to render the vault list. */
record VaultRow(String serverName, String host, String username, Account.Status status,
                boolean autofill, String templateId, boolean serverOnly) { }
}
