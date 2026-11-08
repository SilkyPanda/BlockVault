package io.github.silkypanda.blockvault.core.vault;

import io.github.silkypanda.blockvault.core.crypto.AesGcm;
import io.github.silkypanda.blockvault.core.crypto.SecureBytes;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Strict binary encoding and parsing for the authenticated BVLT v1 container. */
public final class VaultFile {
    public static final int MAX_BYTES = 8 * 1024 * 1024;
    public static final int MIN_KDF_ITERATIONS = 210_000;
    public static final int MAX_KDF_ITERATIONS = 2_000_000;
    // Fixed wire values of the container prefix (the vault is always master-password protected, PBKDF2-HMAC-SHA512).
    private static final int FORMAT_VERSION = 1;
    private static final int KEY_MODE_MASTER_PASSWORD = 2;
    private static final int KDF_PBKDF2_HMAC_SHA512 = 1;
    private static final byte[] MAGIC = "BVLT".getBytes(StandardCharsets.US_ASCII);
    private static final int PREFIX_BYTES = 28;

    private VaultFile() {
    }

    /** Header fields authenticated by the vault format. */
    public record Header(int iterations, byte[] salt) {
        public Header {
            if (salt == null || salt.length != 16 || iterations < MIN_KDF_ITERATIONS
                    || iterations > MAX_KDF_ITERATIONS) {
                throw new IllegalArgumentException("Invalid vault header");
            }
            salt = salt.clone();
        }

        @Override
        public byte[] salt() {
            return salt.clone();
        }
    }

    /** Parsed authenticated fields; ciphertext remains encrypted until unlocked. */
    public record Parsed(Header header, byte[] wrappedKey, byte[] dataNonce, byte[] ciphertext, byte[] headerAad) {
        public Parsed {
            wrappedKey = wrappedKey.clone();
            dataNonce = dataNonce.clone();
            ciphertext = ciphertext.clone();
            headerAad = headerAad.clone();
        }

        @Override
        public byte[] wrappedKey() { return wrappedKey.clone(); }
        @Override
        public byte[] dataNonce() { return dataNonce.clone(); }
        @Override
        public byte[] ciphertext() { return ciphertext.clone(); }
        @Override
        public byte[] headerAad() { return headerAad.clone(); }
    }

    /** Encodes encrypted JSON, authenticating all metadata before ciphertext. */
    public static byte[] encode(Header header, byte[] wrapped, byte[] vaultKey32, byte[] jsonUtf8) {
        long fileLength = jsonUtf8 == null || wrapped == null ? Long.MAX_VALUE
                : (long) PREFIX_BYTES + 2 + wrapped.length + 12 + 4 + jsonUtf8.length + 16;
        if (wrapped == null || wrapped.length > 65535 || jsonUtf8 == null || fileLength > MAX_BYTES) {
            throw new IllegalArgumentException("Invalid vault payload size");
        }
        byte[] nonce = SecureBytes.random(12);
        int cipherLength = Math.addExact(jsonUtf8.length, 16);
        ByteBuffer aad = ByteBuffer.allocate(PREFIX_BYTES + 2 + wrapped.length + 12 + 4).order(ByteOrder.BIG_ENDIAN);
        putPrefix(aad, header);
        aad.putShort((short) wrapped.length).put(wrapped).put(nonce).putInt(cipherLength);
        byte[] aadBytes = aad.array();
        byte[] encrypted = AesGcm.encrypt(vaultKey32, nonce, aadBytes, jsonUtf8);
        ByteBuffer result = ByteBuffer.allocate(aadBytes.length + encrypted.length).order(ByteOrder.BIG_ENDIAN);
        result.put(aadBytes).put(encrypted);
        SecureBytes.wipe(nonce);
        return result.array();
    }

    /** Parses a bounded file and rejects malformed lengths and unsupported versions. */
    public static Parsed parse(byte[] file) throws UnsupportedVaultVersionException {
        if (file == null || file.length < 62 || file.length > MAX_BYTES) {
            throw new IllegalArgumentException("Invalid vault file size");
        }
        ByteBuffer input = ByteBuffer.wrap(file).order(ByteOrder.BIG_ENDIAN);
        byte[] magic = new byte[4];
        input.get(magic);
        if (!Arrays.equals(magic, MAGIC)) {
            throw new IllegalArgumentException("Invalid vault magic");
        }
        int version = Byte.toUnsignedInt(input.get());
        if (version != FORMAT_VERSION) {
            throw new UnsupportedVaultVersionException();
        }
        int keyMode = Byte.toUnsignedInt(input.get());
        int kdfId = Byte.toUnsignedInt(input.get());
        int reserved = Byte.toUnsignedInt(input.get());
        int iterations = input.getInt();
        byte[] salt = new byte[16];
        input.get(salt);
        if (keyMode != KEY_MODE_MASTER_PASSWORD || kdfId != KDF_PBKDF2_HMAC_SHA512 || reserved != 0) {
            throw new IllegalArgumentException("Invalid vault header");
        }
        Header header = new Header(iterations, salt);
        int wrapLength = Short.toUnsignedInt(input.getShort());
        if (wrapLength > input.remaining() - 16 - 12 - 4) {
            throw new IllegalArgumentException("Invalid wrapped key length");
        }
        byte[] wrapped = new byte[wrapLength];
        input.get(wrapped);
        byte[] nonce = new byte[12];
        input.get(nonce);
        int dataLength = input.getInt();
        if (dataLength < 16 || dataLength > MAX_BYTES || dataLength != input.remaining()) {
            throw new IllegalArgumentException("Invalid encrypted data length");
        }
        // The input position is already at the end of the authenticated header.
        byte[] aad = Arrays.copyOf(file, input.position());
        byte[] ciphertext = new byte[dataLength];
        input.get(ciphertext);
        return new Parsed(header, wrapped, nonce, ciphertext, aad);
    }

    /** Returns the authenticated container prefix (magic, format fields, iterations, salt) for the header. */
    public static byte[] prefix(Header header) {
        ByteBuffer bytes = ByteBuffer.allocate(PREFIX_BYTES).order(ByteOrder.BIG_ENDIAN);
        putPrefix(bytes, header);
        return bytes.array();
    }

    private static void putPrefix(ByteBuffer out, Header header) {
        out.put(MAGIC).put((byte) FORMAT_VERSION).put((byte) KEY_MODE_MASTER_PASSWORD)
                .put((byte) KDF_PBKDF2_HMAC_SHA512).put((byte) 0).putInt(header.iterations()).put(header.salt());
    }
}
