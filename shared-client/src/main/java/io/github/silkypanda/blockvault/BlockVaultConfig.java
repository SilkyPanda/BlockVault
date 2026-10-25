package io.github.silkypanda.blockvault;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.silkypanda.blockvault.core.vault.DataDirs;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Lazily loaded, non-secret client preferences stored outside the game directory. */
final class BlockVaultConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("BlockVault");
    private static volatile Settings current = Settings.defaults();
    private static volatile boolean loaded;

    private BlockVaultConfig() { }

    static Settings current() { return current; }

    static void resetAfterDataErasure() {
        current = Settings.defaults();
        loaded = false;
    }

    static void load(Consumer<Settings> result) {
        BackgroundExecutor.submit(() -> {
            if (!loaded) loadOnBackgroundThread();
            BackgroundExecutor.onGameThread(() -> result.accept(current));
            return null;
        });
    }

    static void update(UnaryOperator<Settings> change, Consumer<Boolean> result) {
        BackgroundExecutor.submit(() -> {
            try {
                if (!loaded) loadOnBackgroundThread();
                Settings next = change.apply(current);
                write(DataDirs.resolve().resolve("config.json"), next);
                current = next;
                BackgroundExecutor.onGameThread(() -> result.accept(true));
            } catch (Exception failure) {
                LOGGER.warn("BlockVault could not save its settings");
                BackgroundExecutor.onGameThread(() -> result.accept(false));
            }
            return null;
        });
    }

    /**
     * Reads {@code config.json}. An unreadable file is moved aside to {@code config.json.bad} and defaults are used
     * (with trusted-device unlock off for safety), so the problem is logged once and settings stay changeable.
     */
    private static void loadOnBackgroundThread() {
        Path path;
        try { path = DataDirs.resolve().resolve("config.json"); }
        catch (RuntimeException failure) { current = Settings.defaults().withTrustDevice(false); loaded = true; return; }
        try {
            if (Files.isRegularFile(path)) current = read(JsonParser.parseString(Files.readString(path)).getAsJsonObject());
        } catch (Exception failure) {
            current = Settings.defaults().withTrustDevice(false);
            LOGGER.warn("BlockVault could not read its settings; using defaults with trusted-device unlock off");
            try {
                Files.move(path, path.resolveSibling("config.json.bad"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (java.io.IOException | RuntimeException ignored) {
                // If it cannot be moved aside the next save simply overwrites it.
            }
        }
        loaded = true;
    }

    private static Settings read(JsonObject json) {
        Settings defaults = Settings.defaults();
        String position = string(json, "cardPosition", defaults.cardPosition());
        if (!java.util.Set.of("top_left", "top_right", "bottom_left", "bottom_right").contains(position)) position = defaults.cardPosition();
        String generator = string(json, "generator", defaults.generator());
        if (!java.util.Set.of("defaults", "pickyServer", "max", "memorable").contains(generator)) generator = defaults.generator();
        return new Settings(bool(json, "autofill", defaults.autofill()), integer(json, "autofillDelayMillis", 200, 5000,
                defaults.autofillDelayMillis()),
                integer(json, "cardDurationSeconds", 1, 60, defaults.cardDurationSeconds()),
                position, bool(json, "streamerMode", defaults.streamerMode()),
                generator, bool(json, "scrubHistory", defaults.scrubHistory()),
                bool(json, "trustDevice", defaults.trustDevice()));
    }

    private static void write(Path path, Settings settings) throws Exception {
        Files.createDirectories(path.getParent());
        JsonObject json = new JsonObject();
        json.addProperty("autofill", settings.autofill());
        json.addProperty("autofillDelayMillis", settings.autofillDelayMillis());
        json.addProperty("cardDurationSeconds", settings.cardDurationSeconds());
        json.addProperty("cardPosition", settings.cardPosition());
        json.addProperty("streamerMode", settings.streamerMode());
        json.addProperty("generator", settings.generator());
        json.addProperty("scrubHistory", settings.scrubHistory());
        json.addProperty("trustDevice", settings.trustDevice());
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            Files.writeString(temporary, json.toString(), java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.TRUNCATE_EXISTING, java.nio.file.StandardOpenOption.WRITE);
            try { Files.move(temporary, path, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
            catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(temporary); }
    }

    private static boolean bool(JsonObject json, String key, boolean fallback) {
        try { return json.has(key) ? json.get(key).getAsBoolean() : fallback; }
        catch (RuntimeException ignored) { return fallback; }
    }

    private static int integer(JsonObject json, String key, int min, int max, int fallback) {
        try { return json.has(key) ? Math.max(min, Math.min(max, json.get(key).getAsInt())) : fallback; }
        catch (RuntimeException ignored) { return fallback; }
    }

    private static String string(JsonObject json, String key, String fallback) {
        try { return json.has(key) ? json.get(key).getAsString() : fallback; }
        catch (RuntimeException ignored) { return fallback; }
    }

    /** Maps a stored generator preset name to its password policy (unknown names use the default). */
    static io.github.silkypanda.blockvault.core.gen.PasswordGenerator.Policy generatorPolicy(String preset) {
        return switch (preset) {
            case "pickyServer" -> io.github.silkypanda.blockvault.core.gen.PasswordGenerator.Policy.pickyServer();
            case "max" -> io.github.silkypanda.blockvault.core.gen.PasswordGenerator.Policy.max();
            case "memorable" -> io.github.silkypanda.blockvault.core.gen.PasswordGenerator.Policy.memorable();
            default -> io.github.silkypanda.blockvault.core.gen.PasswordGenerator.Policy.defaults();
        };
    }

    record Settings(boolean autofill, int autofillDelayMillis, int cardDurationSeconds,
                    String cardPosition, boolean streamerMode, String generator, boolean scrubHistory,
                    boolean trustDevice) {
        static Settings defaults() { return new Settings(true, 400, 10, "top_right", false, "defaults", true, true); }
        Settings withAutofill(boolean value) { return new Settings(value, autofillDelayMillis, cardDurationSeconds, cardPosition, streamerMode, generator, scrubHistory, trustDevice); }
        Settings withDelay(int value) { return new Settings(autofill, value, cardDurationSeconds, cardPosition, streamerMode, generator, scrubHistory, trustDevice); }
        Settings withDuration(int value) { return new Settings(autofill, autofillDelayMillis, value, cardPosition, streamerMode, generator, scrubHistory, trustDevice); }
        Settings withPosition(String value) { return new Settings(autofill, autofillDelayMillis, cardDurationSeconds, value, streamerMode, generator, scrubHistory, trustDevice); }
        Settings withStreamer(boolean value) { return new Settings(autofill, autofillDelayMillis, cardDurationSeconds, cardPosition, value, generator, scrubHistory, trustDevice); }
        Settings withGenerator(String value) { return new Settings(autofill, autofillDelayMillis, cardDurationSeconds, cardPosition, streamerMode, value, scrubHistory, trustDevice); }
        Settings withScrubHistory(boolean value) { return new Settings(autofill, autofillDelayMillis, cardDurationSeconds, cardPosition, streamerMode, generator, value, trustDevice); }
        Settings withTrustDevice(boolean value) { return new Settings(autofill, autofillDelayMillis, cardDurationSeconds, cardPosition, streamerMode, generator, scrubHistory, value); }
    }
}
