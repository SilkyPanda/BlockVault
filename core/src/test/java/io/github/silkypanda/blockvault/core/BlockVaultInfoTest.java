package io.github.silkypanda.blockvault.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class BlockVaultInfoTest {
    @Test
    void exposesStableModId() {
        assertEquals("blockvault", BlockVaultInfo.MOD_ID);
    }
}
