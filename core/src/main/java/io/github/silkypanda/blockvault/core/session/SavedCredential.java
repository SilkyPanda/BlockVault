package io.github.silkypanda.blockvault.core.session;

import io.github.silkypanda.blockvault.core.vault.Account;

/** Account and server policy returned together for one host and username. */
public record SavedCredential(Account account, boolean autofill, boolean neverSave, CommandTemplate template,
                              boolean templateExplicit) {
    public SavedCredential(Account account, boolean autofill, boolean neverSave, CommandTemplate template) {
        this(account, autofill, neverSave, template, template != null);
    }
    /** Substitutes the default command template when absent; a null account represents server policy only. */
    public SavedCredential {
        if (template == null) template = CommandTemplate.builtins().get("authme");
    }

    @Override
    public String toString() {
        return "SavedCredential[account=" + account + ", autofill=" + autofill
                + ", neverSave=" + neverSave + ", template=" + template.id() + "]";
    }
}
