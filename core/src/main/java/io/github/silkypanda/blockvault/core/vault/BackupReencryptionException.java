package io.github.silkypanda.blockvault.core.vault;

import java.io.IOException;

/** Indicates the master password changed but one or more backup files could not be re-encrypted. */
public final class BackupReencryptionException extends IOException {
    private static final long serialVersionUID = 1L;

    public BackupReencryptionException(Throwable cause) {
        super("The master password changed, but backup re-encryption did not finish", cause);
    }
}
