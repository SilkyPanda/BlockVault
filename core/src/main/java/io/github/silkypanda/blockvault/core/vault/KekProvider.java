package io.github.silkypanda.blockvault.core.vault;

/** Protects the random vault encryption key with a device or password key. */
public interface KekProvider {
    /** Returns the provider's stable identifier. */
    int id();

    /** Reports whether the provider can currently protect keys. */
    boolean isAvailable();

    /** Protects a 32-byte vault key. */
    byte[] wrap(byte[] vaultKey32);

    /** Unprotects a wrapped vault key. */
    byte[] unwrap(byte[] blob) throws Exception;
}
