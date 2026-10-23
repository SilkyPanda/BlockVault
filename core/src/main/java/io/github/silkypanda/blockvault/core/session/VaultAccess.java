package io.github.silkypanda.blockvault.core.session;

import io.github.silkypanda.blockvault.core.vault.Account;
import java.util.function.Consumer;

/** Asynchronous vault operations used by the login state machine. */
public interface VaultAccess {
    /** Looks up a saved account without blocking the caller. */
    void find(String host, String username, Consumer<Account> result);
    /** Persists a pending account before registration is sent. */
    void savePending(String host, String username, char[] password, Consumer<Boolean> saved);
    /** Marks the pending account verified after server success. */
    void markVerified(String host, String username);
    /** Removes a pending account after a failed registration. */
    void delete(String host, String username);
    /** Saves a manually captured credential after successful authentication. */
    void saveVerified(String host, String username, char[] password);
}
