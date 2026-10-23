package io.github.silkypanda.blockvault.core.vault;

import io.github.silkypanda.blockvault.core.crypto.AesGcm;
import io.github.silkypanda.blockvault.core.crypto.Kdf;
import io.github.silkypanda.blockvault.core.crypto.SecureBytes;

/** PBKDF2-HMAC-SHA512 and AES-GCM provider for master-password vaults. */
public final class MasterPasswordKek {
    private final char[] password;
    private final byte[] salt;
    private final int iterations;
    private final byte[] aad;

    /** Creates a provider from caller-owned secret and header parameters. */
    public MasterPasswordKek(char[] password, byte[] salt, int iterations, byte[] aad) {
        if (password == null || salt == null || salt.length != 16 || iterations <= 0) {
            throw new IllegalArgumentException("Invalid master-password key parameters");
        }
        this.password = password.clone();
        this.salt = salt.clone();
        this.iterations = iterations;
        this.aad = aad.clone();
    }

    public byte[] wrap(byte[] vaultKey32) {
        byte[] key = derive();
        byte[] nonce = SecureBytes.random(12);
        try {
            byte[] encrypted = AesGcm.encrypt(key, nonce, aad, vaultKey32);
            byte[] result = new byte[nonce.length + encrypted.length];
            System.arraycopy(nonce, 0, result, 0, nonce.length);
            System.arraycopy(encrypted, 0, result, nonce.length, encrypted.length);
            return result;
        } finally {
            SecureBytes.wipe(key);
            SecureBytes.wipe(nonce);
        }
    }

    public byte[] unwrap(byte[] blob) throws Exception {
        if (blob.length != 60) {
            throw new IllegalArgumentException("Invalid wrapped key length");
        }
        byte[] nonce = new byte[12];
        byte[] ciphertext = new byte[blob.length - nonce.length];
        System.arraycopy(blob, 0, nonce, 0, nonce.length);
        System.arraycopy(blob, nonce.length, ciphertext, 0, ciphertext.length);
        byte[] key = derive();
        try {
            return AesGcm.decrypt(key, nonce, aad, ciphertext);
        } finally {
            SecureBytes.wipe(key);
            SecureBytes.wipe(nonce);
            SecureBytes.wipe(ciphertext);
        }
    }

    private byte[] derive() {
        return Kdf.pbkdf2HmacSha512(password, salt, iterations, 32);
    }

    /** Clears the retained password copy. */
    public void clear() {
        SecureBytes.wipe(password);
        SecureBytes.wipe(salt);
        SecureBytes.wipe(aad);
    }

    public String toString() {
        return "MasterPasswordKek[masked]";
    }
}
