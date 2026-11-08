package io.github.silkypanda.blockvault.core.session;

import java.util.function.Consumer;

/** Asynchronous vault operations used by the login state machine. */
public interface VaultAccess {
    /** Looks up a saved account without blocking the caller. */
    void find(String host, String username, Consumer<SavedCredential> result);
    /**
     * Like {@link #find(String, String, Consumer)}, but {@code cancelled} runs instead of {@code result} when the
     * owner dismisses the master-password prompt. Implementations that never prompt can ignore it.
     */
    default void find(String host, String username, Consumer<SavedCredential> result, Runnable cancelled) {
        find(host, username, result);
    }
    /** Persists a pending account before registration is sent. */
    void savePending(String host, String username, char[] password, Consumer<Boolean> saved);
    /** Marks a pending account verified, or refreshes last-used time for an existing account. */
    void markVerified(String host, String username);
    /** Moves an account to the trash (owner-confirmed removal or a successful /unregister). */
    void delete(String host, String username);
    /** Moves an account to trash and reports whether the operation completed. */
    default void delete(String host, String username, Consumer<Boolean> result) {
        delete(host, username);
        result.accept(true);
    }
    /** Saves a manually captured credential after successful authentication. */
    void saveVerified(String host, String username, char[] password);
    /** Saves a manually captured credential and reports whether it completed. */
    default void saveVerified(String host, String username, char[] password, Consumer<Boolean> result) {
        saveVerified(host, username, password);
        result.accept(true);
    }
}
