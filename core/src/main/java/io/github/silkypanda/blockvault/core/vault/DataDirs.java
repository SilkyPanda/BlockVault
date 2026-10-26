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

    /** Deletes only the BlockVault data directory, without following symbolic links. */
    public static void delete(Path directory) throws IOException {
        Path target = directory.toAbsolutePath().normalize();
        Path name = target.getFileName();
        if (target.getParent() == null || target.getNameCount() < 2 || name == null
                || !"blockvault".equals(name.toString().toLowerCase(Locale.ROOT))) {
            throw new IOException("Refusing to delete a path outside the BlockVault data directory");
        }
        if (java.nio.file.Files.isSymbolicLink(target)) {
            throw new IOException("Refusing to delete a symbolic link as the data directory");
        }
        if (!java.nio.file.Files.exists(target)) return;
        try (Stream<Path> paths = java.nio.file.Files.walk(target)) {
            for (Path entry : paths.sorted(Comparator.reverseOrder()).toList()) {
                java.nio.file.Files.delete(entry);
            }
        }
    }
}
