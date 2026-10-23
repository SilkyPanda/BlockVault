package io.github.silkypanda.blockvault.core.vault;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class KeyModeTest {
    @Test
    void acceptsOnlyMasterPasswordMode() {
        assertEquals(KeyMode.MASTER_PASSWORD, KeyMode.fromId(2));
        assertThrows(IllegalArgumentException.class, () -> KeyMode.fromId(1));
        assertThrows(IllegalArgumentException.class, () -> KeyMode.fromId(3));
    }
}
