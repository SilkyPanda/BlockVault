package io.github.silkypanda.blockvault.core.vault;

import java.nio.file.Path;
import java.io.IOException;
import java.util.Comparator;
import java.util.Locale;
import java.util.stream.Stream;

/** Resolves BlockVault's user data directory independently of a Minecraft instance. */
public final class DataDirs {
    private DataDirs() { }

    /** Returns an absolute per-user data path, honoring the test override property. */
    public static Path resolve() {
        String override = System.getProperty("blockvault.dataDir");
        if (override != null && !override.isBlank()) return Path.of(override).toAbsolutePath().normalize();
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            // Local (not Roaming) AppData: the vault and its trusted-device key must never roam to a network profile.
            String localAppData = System.getenv("LOCALAPPDATA");
            if (localAppData == null || localAppData.isBlank()) {
                localAppData = Path.of(System.getProperty("user.home"), "AppData", "Local").toString();
            }
            return Path.of(localAppData, "BlockVault");
        }
        if (os.contains("mac")) return Path.of(System.getProperty("user.home"), "Library", "Application Support", "BlockVault");
        String xdg = System.getenv("XDG_DATA_HOME");
        return xdg == null || xdg.isBlank()
                ? Path.of(System.getProperty("user.home"), ".local", "share", "blockvault")
                : Path.of(xdg, "blockvault");
    }

    /** Opens the platform data directory path as an existing or newly-created directory. */
    public static Path createDirectory() throws IOException {
        Path directory = resolve();
        java.nio.file.Files.createDirectories(directory);
        return directory;
    }

    /** Deletes BlockVault-owned vault, settings, pattern and export files in its configured data directory. */
    public static void delete(Path directory) throws IOException {
        Path target = directory.toAbsolutePath().normalize();
        if (!target.equals(resolve().toAbsolutePath().normalize()) || target.getParent() == null
                || target.getNameCount() < 2) {
            throw new IOException("Refusing to delete a path outside the configured BlockVault data directory");
        }
        if (java.nio.file.Files.isSymbolicLink(target)) {
            throw new IOException("Refusing to delete a symbolic link as the data directory");
        }
        if (!java.nio.file.Files.exists(target)) return;
        // Only BlockVault's own files are removed; anything else the owner keeps in this folder is left alone,
        // and the folder itself is removed only if nothing else is in it.
        try (Stream<Path> paths = java.nio.file.Files.list(target)) {
            for (Path entry : paths.toList()) {
                String fileName = entry.getFileName().toString();
                if (java.nio.file.Files.isRegularFile(entry, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                        && OWN_FILES.matcher(fileName).matches()) {
                    java.nio.file.Files.delete(entry);
                } else if ("exports".equals(fileName)
                        && java.nio.file.Files.isDirectory(entry, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                        && !java.nio.file.Files.isSymbolicLink(entry)) {
                    deleteExportFiles(entry);
                }
            }
        }
        try { java.nio.file.Files.delete(target); }
        catch (java.nio.file.DirectoryNotEmptyException keepsOtherFiles) { /* leave the owner's files */ }
    }

    private static final java.util.regex.Pattern OWN_FILES =
            java.util.regex.Pattern.compile("vault\\.bv(\\..+)?|config\\.json(\\..+)?|patterns\\.json(\\..+)?");

    private static void deleteExportFiles(Path exports) throws IOException {
        try (Stream<Path> files = java.nio.file.Files.list(exports)) {
            for (Path file : files.toList()) {
                if (java.nio.file.Files.isRegularFile(file, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                        && file.getFileName().toString().matches("blockvault-export-.*\\.bv")) {
                    java.nio.file.Files.delete(file);
                }
            }
        }
        try { java.nio.file.Files.delete(exports); }
        catch (java.nio.file.DirectoryNotEmptyException keepsOtherFiles) { /* preserve unrelated user files */ }
    }
}
