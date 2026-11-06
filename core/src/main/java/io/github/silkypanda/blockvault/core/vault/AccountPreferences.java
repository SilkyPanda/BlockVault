package io.github.silkypanda.blockvault.core.vault;

import com.google.gson.JsonObject;

/** Per-account behavior stored in the account's forward-compatible metadata. */
public final class AccountPreferences {
    private static final String AUTOFILL = "blockvault.autofill";
    private static final String TEMPLATE = "blockvault.template";

    private AccountPreferences() { }

    public static boolean autofill(Account account, boolean legacyDefault) {
        JsonObject metadata = account.metadata();
        return metadata.has(AUTOFILL) ? metadata.get(AUTOFILL).getAsBoolean() : legacyDefault;
    }

    public static boolean hasAutofill(Account account) { return account.metadata().has(AUTOFILL); }

    public static String template(Account account, String legacyDefault) {
        JsonObject metadata = account.metadata();
        return metadata.has(TEMPLATE) ? metadata.get(TEMPLATE).getAsString() : legacyDefault;
    }

    public static boolean hasTemplate(Account account) { return account.metadata().has(TEMPLATE); }

    public static Account withAutofill(Account account, boolean enabled) {
        JsonObject metadata = account.metadata();
        metadata.addProperty(AUTOFILL, enabled);
        return copy(account, metadata);
    }

    public static Account withTemplate(Account account, String templateId) {
        JsonObject metadata = account.metadata();
        metadata.addProperty(TEMPLATE, templateId);
        return copy(account, metadata);
    }

    private static Account copy(Account source, JsonObject metadata) {
        char[] password = source.password();
        try { return new Account(source.username(), password, source.status(), source.createdAt(), source.modifiedAt(),
                source.lastUsedAt(), metadata); }
        finally { java.util.Arrays.fill(password, '\0'); }
    }
}
