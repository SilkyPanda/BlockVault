package io.github.silkypanda.blockvault.core.totp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class TotpTest {
    @Test
    void matchesRfc6238Sha1Vector() {
        assertEquals("287082", Totp.code("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", 59));
    }
}
