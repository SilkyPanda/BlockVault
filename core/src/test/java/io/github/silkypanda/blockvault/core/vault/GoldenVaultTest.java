package io.github.silkypanda.blockvault.core.vault;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Opens a vault file that was written once and checked in. If this fails, the on-disk format changed in a way
 * that would lock existing users out of their vaults. Change the format deliberately, bump the version, and
 * regenerate the fixture only on purpose.
 */
final class GoldenVaultTest {
    @TempDir Path directory;

    @Test
    void checkedInVaultStillOpensWithItsMasterPassword() throws Exception {
        try (InputStream golden = getClass().getResourceAsStream("/golden/vault-v1.bv")) {
            Files.copy(golden, directory.resolve("vault.bv"));
        }
        VaultStore store = VaultStore.open(directory);
        store.unlock("golden-master-pass".toCharArray());
        Account account = store.vault().servers().getFirst().accounts().getFirst();
        assertEquals("Alex", account.username());
        assertEquals("golden-secret-1", new String(account.password()));
        assertEquals("authme", account.templateId());
        assertThrows(WrongMasterPasswordException.class,
                () -> VaultStore.open(directory).unlock("not-the-password".toCharArray()));
    }
}
