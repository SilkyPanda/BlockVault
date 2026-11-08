package io.github.silkypanda.blockvault.core.vault;

import io.github.silkypanda.blockvault.core.validation.InputRules;

/**
 * Immutable account with a defensively copied password.
 *
 * <p>{@code autofill} and {@code templateId} are per-account login behavior. {@code invalidRaw} is non-null only for
 * a placeholder that stands in for a malformed record: the original JSON text is kept so it is written back
 * unchanged instead of being silently dropped, and the placeholder is never used for logins.
 */
public record Account(String username, char[] password, Status status, long createdAt, long modifiedAt,
                      long lastUsedAt, boolean autofill, String templateId, String invalidRaw) {
    /** Template id meaning "work out the command format from the server's own prompts". */
    public static final String AUTO_TEMPLATE = "auto";

    /** Account verification state persisted across crashes. */
    public enum Status { VERIFIED, PENDING }

    public Account {
        if (username == null || password == null || status == null || templateId == null) {
            throw new IllegalArgumentException("Account fields are invalid");
        }
        password = password.clone();
    }

    /** Creates a normal account that autofills and detects its command format automatically. */
    public Account(String username, char[] password, Status status, long createdAt, long modifiedAt, long lastUsedAt) {
        this(username, password, status, createdAt, modifiedAt, lastUsedAt, true, AUTO_TEMPLATE, null);
    }

    /** Creates a placeholder that preserves a malformed record's original JSON text. */
    public static Account invalid(String rawJson) {
        return new Account("", new char[0], Status.PENDING, 0, 0, 0, false, AUTO_TEMPLATE, rawJson);
    }

    @Override
    public char[] password() { return password.clone(); }

    /** Best-effort zeroing of the account's retained password characters. */
    public void clearPassword() { java.util.Arrays.fill(password, '\0'); }

    /** Invalid data remains readable and visible, but is never eligible for credential use. */
    public boolean isValidForUse() {
        return invalidRaw == null && Usernames.isValid(username) && InputRules.isPassword(password);
    }

    /** Returns an independent copy (including its own password array). */
    public Account copy() { return withUsername(username); }

    /** Returns a copy under a different spelling of the username. */
    public Account withUsername(String spelling) {
        return new Account(spelling, password, status, createdAt, modifiedAt, lastUsedAt, autofill, templateId, invalidRaw);
    }

    /** Returns a copy with a new password, status and timestamps; the account's settings carry over. */
    public Account withCredential(char[] newPassword, Status newStatus, long newModifiedAt, long newLastUsedAt) {
        return new Account(username, newPassword, newStatus, createdAt, newModifiedAt, newLastUsedAt,
                autofill, templateId, null);
    }

    /** Returns a copy marked verified and used at the given time. */
    public Account verifiedAt(long usedAt) {
        return new Account(username, password, Status.VERIFIED, createdAt, modifiedAt, usedAt, autofill, templateId,
                invalidRaw);
    }

    public Account withAutofill(boolean enabled) {
        return new Account(username, password, status, createdAt, modifiedAt, lastUsedAt, enabled, templateId, invalidRaw);
    }

    public Account withTemplate(String newTemplateId) {
        return new Account(username, password, status, createdAt, modifiedAt, lastUsedAt, autofill, newTemplateId,
                invalidRaw);
    }

    @Override
    public String toString() {
        return "Account[username=" + username + ", password=****, status=" + status + "]";
    }
}
