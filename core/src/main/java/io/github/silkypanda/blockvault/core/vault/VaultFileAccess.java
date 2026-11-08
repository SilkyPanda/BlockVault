package io.github.silkypanda.blockvault.core.vault;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

/** Low-level vault file operations. Call every method on the background IO executor. */
final class VaultFileAccess {
    private VaultFileAccess() { }

    static byte[] readBounded(Path file) throws IOException {
        long size = Files.size(file);
        if (size > VaultFile.MAX_BYTES || size < 0) throw new IOException("Vault file exceeds the supported size");
        return Files.readAllBytes(file);
    }

    static void atomicWrite(Path target, byte[] data) throws IOException {
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            try (FileChannel channel = openPrivate(temp)) {
                java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(data);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            moveReplacing(temp, target);
            fsyncDirectory(target.getParent());
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    static void moveReplacing(Path temp, Path target) throws IOException {
        IOException last = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                try { Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
                }
                return;
            } catch (java.nio.file.FileSystemException sharing) {
                last = sharing;
                if (attempt == 2) break;
                try { Thread.sleep(50L * (attempt + 1)); }
                catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while retrying a vault save", interrupted);
                }
            }
        }
        throw last == null ? new IOException("Could not replace vault file") : last;
    }

    static FileChannel openPrivate(Path file) throws IOException {
        Set<StandardOpenOption> options = Set.of(StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        try {
            return FileChannel.open(file, options, java.nio.file.attribute.PosixFilePermissions.asFileAttribute(
                    Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)));
        } catch (UnsupportedOperationException windows) {
            return FileChannel.open(file, options);
        }
    }

    static void fsyncDirectory(Path dir) {
        if (dir == null) return;
        try (FileChannel channel = FileChannel.open(dir, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (IOException | UnsupportedOperationException ignored) {
            // Directories cannot be opened for fsync on every platform (for example Windows).
        }
    }

    static void copyAtomic(Path from, Path to) throws IOException {
        Path temp = to.resolveSibling(to.getFileName() + ".tmp");
        try {
            Files.copy(from, temp, StandardCopyOption.REPLACE_EXISTING);
            setPermissions(temp, false);
            try { Files.move(temp, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                Files.move(temp, to, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    static void setPermissions(Path path, boolean directory) throws IOException {
        try {
            Files.setPosixFilePermissions(path, directory
                    ? Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE)
                    : Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException ignored) {
            // Windows uses the current user's Local AppData ACL.
        }
    }

    static void requirePrivatePermissions(Path path) throws IOException {
        try {
            Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(path,
                    java.nio.file.LinkOption.NOFOLLOW_LINKS);
            if (!permissions.equals(Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE))) {
                throw new IOException("Trusted-device key permissions are not private");
            }
        } catch (UnsupportedOperationException ignored) {
            // Windows uses the current user's Local AppData ACL.
        }
    }
}
