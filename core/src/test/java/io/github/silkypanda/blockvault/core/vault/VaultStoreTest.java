package io.github.silkypanda.blockvault.core.vault;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class VaultStoreTest {
    @TempDir Path directory;

    @Test
    void createsUnlocksAndLeavesFileUntouchedOnWrongPassword() throws Exception {
        VaultStore store = VaultStore.open(directory);
        store.create(KeyMode.MASTER_PASSWORD, "correct horse".toCharArray(), null);
        Path file = directory.resolve("vault.bv");
        byte[] before = Files.readAllBytes(file);
        store.lock();
        VaultStore reopened = VaultStore.open(directory);
        assertThrows(Exception.class, () -> reopened.unlock("wrong horse".toCharArray()));
        assertArrayEquals(before, Files.readAllBytes(file));
        reopened.unlock("correct horse".toCharArray());
        reopened.lock();
    }
}
