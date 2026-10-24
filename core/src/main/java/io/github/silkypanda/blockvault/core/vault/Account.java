package io.github.silkypanda.blockvault.core.vault;

import com.google.gson.JsonObject;

/** Immutable account metadata with a defensively copied password. */
public record Account(String username, char[] password, Status status, long createdAt, long modifiedAt,
                      long lastUsedAt, JsonObject metadata) {
    /** Account verification state persisted across crashes. */
    public enum Status { VERIFIED, PENDING }

    public Account {
        if (username == null || password == null || status == null) {
            throw new IllegalArgumentException("Account fields must not be null");
        }
        password = password.clone();
        metadata = metadata == null ? new JsonObject() : metadata.deepCopy();
    }

    @Override
    public char[] password() { return password.clone(); }

    @Override
    public JsonObject metadata() { return metadata.deepCopy(); }

    /** Best-effort zeroing of the account's retained password characters. */
    public void clearPassword() { java.util.Arrays.fill(password, '\0'); }

    @Override
    public String toString() {
        return "Account[username=" + username + ", password=****, status=" + status + "]";
    }
}
