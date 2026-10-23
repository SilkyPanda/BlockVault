package io.github.silkypanda.blockvault.core.vault;

import java.nio.file.Path;
import java.util.Locale;

/** Resolves BlockVault's user data directory independently of a Minecraft instance. */
public final class DataDirs {
    private DataDirs() { }

    /** Returns an absolute per-user data path, honoring the test override property. */
    public static Path resolve() {
        String override = System.getProperty("blockvault.dataDir");
        if (override != null && !override.isBlank()) return Path.of(override).toAbsolutePath().normalize();
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            if (appData == null || appData.isBlank()) throw new IllegalStateException("APPDATA is unavailable");
            return Path.of(appData, "BlockVault");
        }
        if (os.contains("mac")) return Path.of(System.getProperty("user.home"), "Library", "Application Support", "BlockVault");
        String xdg = System.getenv("XDG_DATA_HOME");
        return xdg == null || xdg.isBlank()
                ? Path.of(System.getProperty("user.home"), ".local", "share", "blockvault")
                : Path.of(xdg, "blockvault");
    }
}
