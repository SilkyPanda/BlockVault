package io.github.silkypanda.blockvault.core.vault;

/** Indicates a vault file is malformed or its encrypted contents fail authentication. */
public final class VaultCorruptException extends Exception {
    private static final long serialVersionUID = 1L;

    public VaultCorruptException(Throwable cause) {
        super("Vault data is damaged or cannot be authenticated", cause);
    }
}
