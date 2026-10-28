package io.github.silkypanda.blockvault.core.crypto;

import java.security.GeneralSecurityException;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** PBKDF2 key derivation for master-password vault keys. */
public final class Kdf {
    private Kdf() {
    }

    /** Derives a key using PBKDF2-HMAC-SHA512 and clears the provider password copy. */
    public static byte[] pbkdf2HmacSha512(char[] password, byte[] salt, int iterations, int dkLenBytes) {
        if (password == null || salt == null || iterations <= 0 || dkLenBytes <= 0 || dkLenBytes > 1024) {
            throw new IllegalArgumentException("Invalid PBKDF2 parameters");
        }
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, Math.multiplyExact(dkLenBytes, 8));
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2-HMAC-SHA512 is unavailable", e);
        } finally {
            spec.clearPassword();
        }
    }

    /** Calibrates PBKDF2 to a duration, bounded by the supplied iteration limits. */
    public static int calibrate(long targetMillis, int floor, int cap) {
        if (targetMillis <= 0 || floor <= 0 || cap < floor) {
            throw new IllegalArgumentException("Invalid calibration bounds");
        }
        char[] password = "BlockVault calibration".toCharArray();
        byte[] salt = SecureBytes.random(16);
        long[] samples = new long[3];
        try {
            // Warm up the provider/JIT, then use a median to avoid calibrating from one cold or noisy sample.
            for (int warmup = 0; warmup < 2; warmup++) {
                byte[] probe = pbkdf2HmacSha512(password, salt, floor, 32);
                SecureBytes.wipe(probe);
            }
            for (int index = 0; index < samples.length; index++) {
                long start = System.nanoTime();
                byte[] probe = pbkdf2HmacSha512(password, salt, floor, 32);
                samples[index] = Math.max(1L, System.nanoTime() - start);
                SecureBytes.wipe(probe);
            }
            java.util.Arrays.sort(samples);
            double scaled = (double) floor * targetMillis * 1_000_000d / samples[1];
            return (int) Math.max(floor, Math.min((long) cap, scaled));
        } finally {
            SecureBytes.wipe(password);
            SecureBytes.wipe(salt);
        }
    }
}
