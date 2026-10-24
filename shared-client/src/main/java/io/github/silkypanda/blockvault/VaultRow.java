package io.github.silkypanda.blockvault;

import io.github.silkypanda.blockvault.core.vault.Account;

/** Secret-free account metadata used to render the vault list. */
record VaultRow(String serverName, String host, String username, Account.Status status,
                boolean autofill, boolean neverSave, String templateId, boolean serverOnly) {
    VaultRow(String serverName, String host, String username, Account.Status status,
             boolean autofill, boolean neverSave, String templateId) {
        this(serverName, host, username, status, autofill, neverSave, templateId, false);
    }
}
