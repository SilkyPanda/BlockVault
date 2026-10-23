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

    static void load(Consumer<Settings> result) {
        BackgroundExecutor.submit(() -> {
            if (!loaded) {
                Path path = DataDirs.resolve().resolve("config.json");
                if (Files.isRegularFile(path)) {
                    try { current = read(JsonParser.parseString(Files.readString(path)).getAsJsonObject()); }
                    catch (Exception failure) { LOGGER.warn("BlockVault could not read its settings; defaults are active"); }
                }
                loaded = true;
            }
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

    private static void loadOnBackgroundThread() throws Exception {
        Path path = DataDirs.resolve().resolve("config.json");
        if (Files.isRegularFile(path)) current = read(JsonParser.parseString(Files.readString(path)).getAsJsonObject());
        loaded = true;
    }

    private static Settings read(JsonObject json) {
        Settings defaults = Settings.defaults();
        return new Settings(bool(json, "autofill", defaults.autofill()), integer(json, "autofillDelayMillis", 400, 5000,
                defaults.autofillDelayMillis()), integer(json, "autoLockMinutes", 0, 1440, defaults.autoLockMinutes()),
                integer(json, "cardDurationSeconds", 1, 60, defaults.cardDurationSeconds()),
                string(json, "cardPosition", defaults.cardPosition()), bool(json, "streamerMode", defaults.streamerMode()),
                string(json, "generator", defaults.generator()), bool(json, "scrubHistory", defaults.scrubHistory()),
                bool(json, "trustDevice", defaults.trustDevice()));
    }

    private static void write(Path path, Settings settings) throws Exception {
        Files.createDirectories(path.getParent());
        JsonObject json = new JsonObject();
        json.addProperty("autofill", settings.autofill());
        json.addProperty("autofillDelayMillis", settings.autofillDelayMillis());
        json.addProperty("autoLockMinutes", settings.autoLockMinutes());
        json.addProperty("cardDurationSeconds", settings.cardDurationSeconds());
        json.addProperty("cardPosition", settings.cardPosition());
        json.addProperty("streamerMode", settings.streamerMode());
        json.addProperty("generator", settings.generator());
        json.addProperty("scrubHistory", settings.scrubHistory());
        json.addProperty("trustDevice", settings.trustDevice());
        Files.writeString(path, json.toString());
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

    record Settings(boolean autofill, int autofillDelayMillis, int autoLockMinutes, int cardDurationSeconds,
                    String cardPosition, boolean streamerMode, String generator, boolean scrubHistory,
                    boolean trustDevice) {
        static Settings defaults() { return new Settings(true, 400, 15, 10, "top_right", false, "defaults", true, true); }
        Settings withAutofill(boolean value) { return new Settings(value, autofillDelayMillis, autoLockMinutes, cardDurationSeconds, cardPosition, streamerMode, generator, scrubHistory, trustDevice); }
        Settings withDelay(int value) { return new Settings(autofill, value, autoLockMinutes, cardDurationSeconds, cardPosition, streamerMode, generator, scrubHistory, trustDevice); }
        Settings withAutoLock(int value) { return new Settings(autofill, autofillDelayMillis, value, cardDurationSeconds, cardPosition, streamerMode, generator, scrubHistory, trustDevice); }
        Settings withDuration(int value) { return new Settings(autofill, autofillDelayMillis, autoLockMinutes, value, cardPosition, streamerMode, generator, scrubHistory, trustDevice); }
        Settings withPosition(String value) { return new Settings(autofill, autofillDelayMillis, autoLockMinutes, cardDurationSeconds, value, streamerMode, generator, scrubHistory, trustDevice); }
        Settings withStreamer(boolean value) { return new Settings(autofill, autofillDelayMillis, autoLockMinutes, cardDurationSeconds, cardPosition, value, generator, scrubHistory, trustDevice); }
        Settings withGenerator(String value) { return new Settings(autofill, autofillDelayMillis, autoLockMinutes, cardDurationSeconds, cardPosition, streamerMode, value, scrubHistory, trustDevice); }
        Settings withScrubHistory(boolean value) { return new Settings(autofill, autofillDelayMillis, autoLockMinutes, cardDurationSeconds, cardPosition, streamerMode, generator, value, trustDevice); }
        Settings withTrustDevice(boolean value) { return new Settings(autofill, autofillDelayMillis, autoLockMinutes, cardDurationSeconds, cardPosition, streamerMode, generator, scrubHistory, value); }
    }
}
