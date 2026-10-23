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
    private static final byte[] MAGIC = "BVLT".getBytes(StandardCharsets.US_ASCII);
    private static final int PREFIX_BYTES = 28;

    private VaultFile() {
    }

    /** Header fields authenticated by the vault format. */
    public record Header(KeyMode keyMode, int kdfId, int providerId, int iterations, byte[] salt) {
        public Header {
            if (salt == null || salt.length != 16 || kdfId < 0 || kdfId > 255 || providerId < 0 || providerId > 255
                    || iterations < 0) {
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
        if (wrapped == null || wrapped.length > 65535 || jsonUtf8 == null || jsonUtf8.length + 16 > MAX_BYTES) {
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
        if (version != 1) {
            throw new UnsupportedVaultVersionException();
        }
        KeyMode mode;
        try {
            mode = KeyMode.fromId(Byte.toUnsignedInt(input.get()));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid vault key mode", e);
        }
        int kdfId = Byte.toUnsignedInt(input.get());
        int providerId = Byte.toUnsignedInt(input.get());
        int iterations = input.getInt();
        byte[] salt = new byte[16];
        input.get(salt);
        if (iterations < 0 || kdfId > 1 || providerId > 3
                || (kdfId == 0 && iterations != 0) || (kdfId == 1 && iterations == 0)
                || (mode == KeyMode.MASTER_PASSWORD && kdfId != 1)
                || (mode == KeyMode.DEVICE && kdfId != 0)) {
            throw new IllegalArgumentException("Invalid KDF header");
        }
        if (kdfId == 0) {
            for (byte value : salt) if (value != 0) throw new IllegalArgumentException("Invalid empty KDF salt");
        }
        Header header = new Header(mode, kdfId, providerId, iterations, salt);
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
        byte[] aad = Arrays.copyOf(file, input.position() - dataLength);
        byte[] ciphertext = new byte[dataLength];
        input.get(ciphertext);
        return new Parsed(header, wrapped, nonce, ciphertext, aad);
    }

    private static void putPrefix(ByteBuffer out, Header header) {
        out.put(MAGIC).put((byte) 1).put((byte) header.keyMode().id()).put((byte) header.kdfId())
                .put((byte) header.providerId()).putInt(header.iterations()).put(header.salt());
    }
}
