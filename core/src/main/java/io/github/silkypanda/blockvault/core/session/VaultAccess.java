package io.github.silkypanda.blockvault.core.session;

import java.util.function.Consumer;

/** Asynchronous vault operations used by the login state machine. */
public interface VaultAccess {
    /** Looks up a saved account without blocking the caller. */
    void find(String host, String username, Consumer<SavedCredential> result);
    /** Persists a pending account before registration is sent. */
    void savePending(String host, String username, char[] password, Consumer<Boolean> saved);
    /** Marks a pending account verified, or refreshes last-used time for an existing account. */
    void markVerified(String host, String username);
    /** Removes a pending account after a failed registration. */
    void delete(String host, String username);
    /** Saves a manually captured credential after successful authentication. */
    void saveVerified(String host, String username, char[] password);
}
