package io.github.silkypanda.blockvault.core.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class SecureBytesTest {
    @Test
    void wipesAndReturnsRequestedRandomLength() {
        byte[] bytes = {1, 2};
        char[] chars = {'x'};
        SecureBytes.wipe(bytes);
        SecureBytes.wipe(chars);
        assertEquals(0, bytes[1]);
        assertEquals('\0', chars[0]);
        assertEquals(17, SecureBytes.random(17).length);
    }
}
