package io.github.silkypanda.blockvault.core.crypto;

import java.security.GeneralSecurityException;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** AES-256-GCM helpers using a 128-bit authentication tag. */
public final class AesGcm {
    private static final int KEY_BYTES = 32;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    private AesGcm() {
    }

    /** Encrypts bytes with the supplied key, nonce and authenticated header. */
    public static byte[] encrypt(byte[] key32, byte[] nonce12, byte[] aad, byte[] plaintext) {
        validate(key32, nonce12);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key32, "AES"), new GCMParameterSpec(TAG_BITS, nonce12));
            cipher.updateAAD(aad);
            return cipher.doFinal(plaintext);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("AES-GCM encryption failed", e);
        }
    }

    /** Decrypts and authenticates bytes; authentication failures never return plaintext. */
    public static byte[] decrypt(byte[] key32, byte[] nonce12, byte[] aad, byte[] ciphertext) throws VaultAuthException {
        validate(key32, nonce12);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key32, "AES"), new GCMParameterSpec(TAG_BITS, nonce12));
            cipher.updateAAD(aad);
            return cipher.doFinal(ciphertext);
        } catch (GeneralSecurityException e) {
            throw new VaultAuthException("Vault authentication failed", e);
        }
    }

    private static void validate(byte[] key, byte[] nonce) {
        if (key == null || key.length != KEY_BYTES) {
            throw new IllegalArgumentException("AES-256 key must be exactly 32 bytes");
        }
        if (nonce == null || nonce.length != NONCE_BYTES) {
            throw new IllegalArgumentException("AES-GCM nonce must be exactly 12 bytes");
        }
    }
}
