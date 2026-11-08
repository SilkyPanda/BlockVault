package io.github.silkypanda.blockvault.core.vault;

import io.github.silkypanda.blockvault.core.crypto.AesGcm;
import io.github.silkypanda.blockvault.core.crypto.SecureBytes;
import io.github.silkypanda.blockvault.core.crypto.VaultAuthException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/** Owns import preview, commit, and temporary decrypted import data. Call methods on the background IO executor. */
final class VaultImportManager {
    private final VaultStore store;

    VaultImportManager(VaultStore store) { this.store = store; }

    ImportPreview preview(Path source, char[] masterPassword) throws Exception {
        if (store.vaultKey == null) throw new IllegalStateException("Vault is locked");
        Path normalized = source.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalized) || normalized.equals(store.path("vault.bv"))) {
            throw new IOException("Invalid encrypted vault import source");
        }
        byte[] encoded = VaultFileAccess.readBounded(normalized);
        try {
            Vault imported = decryptExternal(encoded, masterPassword);
            discard();
            store.pendingImport = imported;
            return VaultMerger.diff(store.vault, imported, System.currentTimeMillis());
        } finally {
            SecureBytes.wipe(encoded);
        }
    }

    void commit() throws Exception {
        if (store.vaultKey == null) throw new IllegalStateException("Vault is locked");
        if (store.pendingImport == null) throw new IllegalStateException("No import is waiting");
        Vault imported = store.pendingImport;
        store.pendingImport = null;
        byte[] mergedJson = null;
        try {
            store.withLock(() -> { store.rotateBackupsNow(); return null; });
            mergedJson = VaultJson.write(VaultMerger.merge(store.vault, imported, System.currentTimeMillis()));
            // Re-read so the saved vault does not share password arrays with the import that is wiped below.
            // save() also merges with whatever another instance wrote to disk in the meantime.
            store.save(VaultJson.read(mergedJson));
        } finally {
            SecureBytes.wipe(mergedJson);
            VaultStore.clearVaultPasswords(imported);
        }
    }

    void discard() {
        VaultStore.clearVaultPasswords(store.pendingImport);
        store.pendingImport = null;
    }

    static Vault decryptExternal(byte[] encoded, char[] masterPassword) throws Exception {
        if (masterPassword == null) throw new IllegalArgumentException("A master password is required");
        VaultFile.Parsed parsed;
        try { parsed = VaultFile.parse(encoded); }
        catch (UnsupportedVaultVersionException version) { throw version; }
        catch (Exception malformed) { throw new VaultCorruptException(malformed); }
        byte[] wrapAad = Arrays.copyOf(parsed.headerAad(), VaultStore.PREFIX_BYTES);
        MasterPasswordKek kek = new MasterPasswordKek(masterPassword, parsed.header().salt(),
                parsed.header().iterations(), wrapAad);
        byte[] key;
        try { key = kek.unwrap(parsed.wrappedKey()); }
        catch (VaultAuthException wrongPassword) { throw new WrongMasterPasswordException(wrongPassword); }
        finally { kek.clear(); }
        byte[] plaintext = null;
        try {
            plaintext = AesGcm.decrypt(key, parsed.dataNonce(), parsed.headerAad(), parsed.ciphertext());
            return VaultJson.read(plaintext);
        } catch (VaultAuthException | IllegalArgumentException | IllegalStateException damaged) {
            throw new VaultCorruptException(damaged);
        } finally {
            SecureBytes.wipe(key);
            SecureBytes.wipe(plaintext);
        }
    }
}
