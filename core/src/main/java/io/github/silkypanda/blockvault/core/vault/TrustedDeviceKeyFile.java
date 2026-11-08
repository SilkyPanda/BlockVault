package io.github.silkypanda.blockvault.core.vault;

import io.github.silkypanda.blockvault.core.crypto.SecureBytes;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/** Owns trusted-device key file reads and writes. Call every method on the background IO executor. */
final class TrustedDeviceKeyFile {
    private TrustedDeviceKeyFile() { }

    static void write(Path directory, byte[] vaultKey) throws IOException {
        Path temporary = directory.resolve("vault.bv.trust.tmp");
        Path trusted = directory.resolve("vault.bv.trust");
        byte[] copy = vaultKey.clone();
        try {
            try (FileChannel channel = VaultFileAccess.openPrivate(temporary)) {
                java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(copy);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            Files.move(temporary, trusted, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            VaultFileAccess.fsyncDirectory(directory);
        } finally {
            SecureBytes.wipe(copy);
            Files.deleteIfExists(temporary);
        }
    }

    static void delete(Path directory) throws IOException {
        Files.deleteIfExists(directory.resolve("vault.bv.trust"));
        Files.deleteIfExists(directory.resolve("vault.bv.trust.tmp"));
    }

    static byte[] read(Path directory) throws IOException {
        Path trusted = directory.resolve("vault.bv.trust");
        if (!Files.isRegularFile(trusted, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                || Files.size(trusted) != 32) {
            throw new IOException("Trusted-device key is missing or invalid");
        }
        VaultFileAccess.requirePrivatePermissions(trusted);
        return Files.readAllBytes(trusted);
    }
}
