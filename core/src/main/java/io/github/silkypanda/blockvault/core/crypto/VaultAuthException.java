package io.github.silkypanda.blockvault.core.crypto;

/** Raised when encrypted vault data cannot be authenticated or decrypted. */
public final class VaultAuthException extends Exception {
    private static final long serialVersionUID = 1L;

    public VaultAuthException(String message, Throwable cause) {
        super(message, cause);
    }
}
