package io.github.silkypanda.blockvault.core.crypto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HexFormat;
import org.junit.jupiter.api.Test;

final class AesGcmTest {
    @Test
    void matchesNistEmptyMessageVector() {
        byte[] key = new byte[32];
        byte[] nonce = new byte[12];
        byte[] ciphertext = AesGcm.encrypt(key, nonce, new byte[0], new byte[0]);
        assertArrayEquals(HexFormat.of().parseHex("530f8afbc74536b9a963b4f1c4cb738b"), ciphertext);
    }

    @Test
    void detectsTampering() {
        byte[] key = new byte[32];
        byte[] nonce = new byte[12];
        byte[] encrypted = AesGcm.encrypt(key, nonce, new byte[] {1}, new byte[] {2, 3});
        encrypted[0] ^= 1;
        assertThrows(VaultAuthException.class, () -> AesGcm.decrypt(key, nonce, new byte[] {1}, encrypted));
    }
}
