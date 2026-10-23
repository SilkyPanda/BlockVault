package io.github.silkypanda.blockvault.core.vault;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class VaultStoreTest {
    @TempDir Path directory;

    @Test
    void createsUnlocksAndLeavesFileUntouchedOnWrongPassword() throws Exception {
        VaultStore store = VaultStore.open(directory);
        store.create("correct horse".toCharArray());
        Path file = directory.resolve("vault.bv");
        byte[] before = Files.readAllBytes(file);
        store.lock();
        VaultStore reopened = VaultStore.open(directory);
        assertThrows(Exception.class, () -> reopened.unlock("wrong horse".toCharArray()));
        assertArrayEquals(before, Files.readAllBytes(file));
        reopened.unlock("correct horse".toCharArray());
        reopened.lock();
    }

    @Test
    void trustedDeviceKeyUnlocksWithoutStoringTheMasterPassword() throws Exception {
        VaultStore store = VaultStore.open(directory);
        store.create("correct horse".toCharArray());
        store.trustThisDevice();
        Path trustFile = directory.resolve("vault.bv.trust");
        assertTrue(Files.isRegularFile(trustFile));
        assertTrue(Files.size(trustFile) == 32);

        store.lock();
        VaultStore reopened = VaultStore.open(directory);
        reopened.unlockTrustedDevice();
        assertTrue(reopened.vault().servers().isEmpty());
        reopened.lock();
    }

    @Test
    void corruptTrustedDeviceKeyFallsBackWithoutChangingVault() throws Exception {
        VaultStore store = VaultStore.open(directory);
        store.create("correct horse".toCharArray());
        store.trustThisDevice();
        store.lock();
        Path vaultFile = directory.resolve("vault.bv");
        Path trustFile = directory.resolve("vault.bv.trust");
        byte[] before = Files.readAllBytes(vaultFile);
        Files.delete(trustFile);

        VaultStore reopened = VaultStore.open(directory);
        assertThrows(Exception.class, reopened::unlockTrustedDevice);
        assertTrue(java.util.Arrays.equals(before, Files.readAllBytes(vaultFile)));
        Files.write(trustFile, new byte[32]);
        assertThrows(Exception.class, reopened::unlockTrustedDevice);
        assertTrue(java.util.Arrays.equals(before, Files.readAllBytes(vaultFile)));
        reopened.unlock("correct horse".toCharArray());
    }

    @Test
    void disablingTrustRemovesTheLocalKeyFile() throws Exception {
        VaultStore store = VaultStore.open(directory);
        store.create("correct horse".toCharArray());
        store.trustThisDevice();
        store.forgetTrustedDevice();
        assertTrue(Files.notExists(directory.resolve("vault.bv.trust")));
    }
}
