package io.github.silkypanda.blockvault.core.session;

import io.github.silkypanda.blockvault.core.vault.Account;

/** Account and login settings returned together for one host and username. */
public record SavedCredential(Account account, boolean autofill, CommandTemplate template, boolean templateExplicit) {
    /** Reads the former save-policy field for source compatibility; saving is now always allowed. */
    public SavedCredential(Account account, boolean autofill, boolean ignoredLegacySavePolicy, CommandTemplate template) {
        this(account, autofill, template, template != null);
    }
    /** Reads the former save-policy field for source compatibility; saving is now always allowed. */
    public SavedCredential(Account account, boolean autofill, boolean ignoredLegacySavePolicy,
                           CommandTemplate template, boolean templateExplicit) {
        this(account, autofill, template, templateExplicit);
    }
    /** Substitutes the default command template when absent; a null account represents server policy only. */
    public SavedCredential {
        if (template == null) template = CommandTemplate.builtins().get("authme");
    }

    @Override
    public String toString() {
        return "SavedCredential[account=" + account + ", autofill=" + autofill
                + ", template=" + template.id() + "]";
    }
}
