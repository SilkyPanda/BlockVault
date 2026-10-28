package io.github.silkypanda.blockvault.core.vault;

/** Indicates a vault uses a file format newer than this implementation understands. */
public final class UnsupportedVaultVersionException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public UnsupportedVaultVersionException() {
        super("Unsupported vault format version");
    }
}
