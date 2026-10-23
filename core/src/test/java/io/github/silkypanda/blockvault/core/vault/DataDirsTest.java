package io.github.silkypanda.blockvault.core.vault;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class DataDirsTest {
    @Test
    void honorsOverride() {
        String old = System.getProperty("blockvault.dataDir");
        try {
            System.setProperty("blockvault.dataDir", "build/test-vault");
            assertEquals(java.nio.file.Path.of("build/test-vault").toAbsolutePath().normalize(), DataDirs.resolve());
        } finally {
            if (old == null) System.clearProperty("blockvault.dataDir");
            else System.setProperty("blockvault.dataDir", old);
        }
    }
}
