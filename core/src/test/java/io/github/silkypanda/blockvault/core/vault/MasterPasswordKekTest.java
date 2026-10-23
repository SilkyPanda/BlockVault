package io.github.silkypanda.blockvault.core.vault;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import io.github.silkypanda.blockvault.core.crypto.SecureBytes;
import org.junit.jupiter.api.Test;

final class MasterPasswordKekTest {
    @Test
    void wrapsAndUnwrapsVaultKey() throws Exception {
        byte[] salt = new byte[16];
        byte[] key = SecureBytes.random(32);
        MasterPasswordKek provider = new MasterPasswordKek("master".toCharArray(), salt, 3, new byte[] {1, 2});
        byte[] wrapped = provider.wrap(key);
        assertArrayEquals(key, provider.unwrap(wrapped));
        provider.clear();
        SecureBytes.wipe(key);
    }
}
