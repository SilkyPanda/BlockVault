package io.github.silkypanda.blockvault.core.vault;

/** Indicates the supplied master password did not unwrap the vault key. */
public final class WrongMasterPasswordException extends Exception {
    private static final long serialVersionUID = 1L;

    public WrongMasterPasswordException(Throwable cause) {
        super("Master password is incorrect", cause);
    }
}
