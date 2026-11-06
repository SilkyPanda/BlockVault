package io.github.silkypanda.blockvault.core.vault;

/** The master password was changed successfully, but the optional trusted-device key could not be rewritten. */
public final class TrustedDeviceUpdateException extends Exception {
    private static final long serialVersionUID = 1L;

    public TrustedDeviceUpdateException(Throwable cause) {
        super("Master password changed, but the trusted-device key could not be updated", cause);
    }
}
