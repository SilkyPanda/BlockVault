package io.github.silkypanda.blockvault.core.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HexFormat;
import org.junit.jupiter.api.Test;

final class KdfTest {
    @Test
    void matchesPbkdf2HmacSha512Vector() {
        byte[] actual = Kdf.pbkdf2HmacSha512("password".toCharArray(), "salt".getBytes(java.nio.charset.StandardCharsets.US_ASCII), 1, 64);
        assertEquals("867f70cf1ade02cff3752599a3a53dc4af34c7a669815ae5d513554e1c8cf252c02d470a285a0501bad999bfe943c08f050235d7d68b1da55e63f73b60a57fce",
                HexFormat.of().formatHex(actual));
    }
}
