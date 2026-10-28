package io.github.silkypanda.blockvault.core.vault;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
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

    @Test
    void deleteSupportsConfiguredDirectoryAndRemovesOwnedFilesOnly() throws Exception {
        Path parent = java.nio.file.Files.createTempDirectory("blockvault-delete");
        Path directory = java.nio.file.Files.createDirectory(parent.resolve("mydata"));
        String old = System.getProperty("blockvault.dataDir");
        System.setProperty("blockvault.dataDir", directory.toString());
        Path secret = java.nio.file.Files.writeString(directory.resolve("vault.bv"), "encrypted");
        Path patterns = java.nio.file.Files.writeString(directory.resolve("patterns.json"), "{}");
        Path exported = java.nio.file.Files.createDirectory(directory.resolve("exports")).resolve("blockvault-export-a.bv");
        java.nio.file.Files.writeString(exported, "encrypted export");
        Path outside = java.nio.file.Files.writeString(parent.resolve("keep.txt"), "keep");
        Path ownersFile = java.nio.file.Files.writeString(directory.resolve("notes.txt"), "mine");
        try {
            DataDirs.delete(directory);
            assertFalse(java.nio.file.Files.exists(secret));
            assertFalse(java.nio.file.Files.exists(patterns));
            assertFalse(java.nio.file.Files.exists(exported));
            assertEquals("mine", java.nio.file.Files.readString(ownersFile), "files that are not BlockVault's stay");
            java.nio.file.Files.delete(ownersFile);
            java.nio.file.Files.delete(directory);
            assertEquals("keep", java.nio.file.Files.readString(outside));
            assertThrows(java.io.IOException.class, () -> DataDirs.delete(parent));
        } finally {
            if (old == null) System.clearProperty("blockvault.dataDir");
            else System.setProperty("blockvault.dataDir", old);
            java.nio.file.Files.deleteIfExists(outside);
            java.nio.file.Files.deleteIfExists(parent);
        }
    }
}
