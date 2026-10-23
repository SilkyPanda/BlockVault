package io.github.silkypanda.blockvault.core.crypto;

import java.security.SecureRandom;
import java.util.Arrays;

/** Best-effort secret wiping and cryptographically secure random bytes. */
public final class SecureBytes {
    private static final SecureRandom RANDOM = new SecureRandom();

    private SecureBytes() {
    }

    /** Fills a byte array with zeroes. */
    public static void wipe(byte[] value) {
        if (value != null) {
            Arrays.fill(value, (byte) 0);
        }
    }

    /** Fills a character array with zeroes. */
    public static void wipe(char[] value) {
        if (value != null) {
            Arrays.fill(value, '\0');
        }
    }

    /** Returns {@code n} unpredictable bytes. */
    public static byte[] random(int n) {
        if (n < 0) {
            throw new IllegalArgumentException("Length must not be negative");
        }
        byte[] result = new byte[n];
        RANDOM.nextBytes(result);
        return result;
    }
}
