package io.github.silkypanda.blockvault.core.totp;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.GeneralSecurityException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** RFC 6238 time-based one-time password generation with dependency-free Base32 decoding. */
public final class Totp {
    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final int STEP_SECONDS = 30;

    private Totp() { }

    /** Generates a six digit RFC 6238 code using the given epoch seconds and HMAC algorithm. */
    public static String code(String base32Secret, long epochSeconds) {
        return code(base32Secret, epochSeconds, "HmacSHA1");
    }

    /** Generates a six digit RFC 6238 code using HMAC-SHA1, SHA256, or SHA512. */
    public static String code(String base32Secret, long epochSeconds, String algorithm) {
        if (epochSeconds < 0 || !(algorithm.equals("HmacSHA1") || algorithm.equals("HmacSHA256") || algorithm.equals("HmacSHA512"))) {
            throw new IllegalArgumentException("Invalid TOTP parameters");
        }
        byte[] key = decodeBase32(base32Secret);
        byte[] message = ByteBuffer.allocate(Long.BYTES).order(ByteOrder.BIG_ENDIAN).putLong(epochSeconds / STEP_SECONDS).array();
        try {
            Mac mac = Mac.getInstance(algorithm);
            mac.init(new SecretKeySpec(key, algorithm));
            byte[] hash = mac.doFinal(message);
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24) | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8) | (hash[offset + 3] & 0xff);
            return String.format(java.util.Locale.ROOT, "%06d", binary % 1_000_000);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("TOTP algorithm unavailable", e);
        } finally {
            java.util.Arrays.fill(key, (byte) 0);
            java.util.Arrays.fill(message, (byte) 0);
        }
    }

    /** Decodes an unpadded or padded Base32 secret. */
    public static byte[] decodeBase32(String encoded) {
        if (encoded == null || encoded.isBlank()) throw new IllegalArgumentException("Base32 secret is empty");
        encoded = encoded.replace(" ", "").replace("-", "");
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        int buffer = 0;
        int bits = 0;
        for (int i = 0; i < encoded.length(); i++) {
            char c = Character.toUpperCase(encoded.charAt(i));
            if (c == '=') break;
            int value = BASE32.indexOf(c);
            if (value < 0) throw new IllegalArgumentException("Invalid Base32 secret");
            buffer = (buffer << 5) | value;
            bits += 5;
            if (bits >= 8) {
                bits -= 8;
                output.write((buffer >> bits) & 0xff);
            }
        }
        if (output.size() == 0) throw new IllegalArgumentException("Base32 secret is empty");
        return output.toByteArray();
    }
}
