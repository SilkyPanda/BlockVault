package io.github.silkypanda.blockvault.core.detect;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Compiled user-extensible prompt and result patterns loaded from JSON trees. */
public final class PatternPack {
    private final Map<PromptKind, List<Pattern>> prompts;
    private final Map<ResultKind, List<Pattern>> results;
    private final Map<String, Set<String>> commandNames;

    private PatternPack(Map<PromptKind, List<Pattern>> prompts, Map<ResultKind, List<Pattern>> results,
                        Map<String, Set<String>> commandNames) {
        this.prompts = Map.copyOf(prompts);
        this.results = Map.copyOf(results);
        this.commandNames = Map.copyOf(commandNames);
    }

    /** Loads built-in patterns and appends valid user patterns, reporting malformed regexes once each. */
    public static PatternPack load(InputStream defaults, Path userFileOrNull) throws IOException {
        return load(defaults, userFileOrNull, ignored -> { });
    }

    /** Loads a pattern pack and sends each invalid user regex to the warning consumer once. */
    public static PatternPack load(InputStream defaults, Path userFileOrNull, Consumer<String> warning) throws IOException {
        JsonObject base;
        try (InputStream stream = defaults) {
            base = JsonParser.parseReader(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        }
        JsonObject user = new JsonObject();
        if (userFileOrNull != null && Files.isRegularFile(userFileOrNull)) {
            try { user = JsonParser.parseString(Files.readString(userFileOrNull)).getAsJsonObject(); }
            catch (RuntimeException | IOException malformedUserFile) { warning.accept("Invalid BlockVault pattern file ignored"); }
        }
        Map<PromptKind, List<Pattern>> prompts = new EnumMap<>(PromptKind.class);
        addPatterns(prompts, PromptKind.LOGIN, getArray(base, "prompt", "login", ignored -> { }), ignored -> { });
        addPatterns(prompts, PromptKind.REGISTER, getArray(base, "prompt", "register", ignored -> { }), ignored -> { });
        addPatterns(prompts, PromptKind.LOGIN, getArray(user, "prompt", "login", warning), warning);
        addPatterns(prompts, PromptKind.REGISTER, getArray(user, "prompt", "register", warning), warning);
        Map<ResultKind, List<Pattern>> results = new EnumMap<>(ResultKind.class);
        addPatterns(results, ResultKind.SUCCESS, getArray(base, "result", "success", ignored -> { }), ignored -> { });
        addPatterns(results, ResultKind.FAILURE, getArray(base, "result", "failure", ignored -> { }), ignored -> { });
        addPatterns(results, ResultKind.PASSWORD_RULE, getArray(base, "result", "passwordRules", ignored -> { }), ignored -> { });
        addPatterns(results, ResultKind.SUCCESS, getArray(user, "result", "success", warning), warning);
        addPatterns(results, ResultKind.FAILURE, getArray(user, "result", "failure", warning), warning);
        addPatterns(results, ResultKind.PASSWORD_RULE, getArray(user, "result", "passwordRules", warning), warning);
        Map<String, Set<String>> names = new java.util.HashMap<>();
        if (base.has("commandNames") && base.get("commandNames").isJsonObject()) {
            base.getAsJsonObject("commandNames").entrySet().forEach(entry ->
                    addCommandNames(names, entry.getKey(), entry.getValue()));
        }
        if (user.has("commandNames") && user.get("commandNames").isJsonObject()) {
            user.getAsJsonObject("commandNames").entrySet().forEach(entry ->
                    addCommandNames(names, entry.getKey(), entry.getValue()));
        }
        Map<String, Set<String>> frozen = new java.util.HashMap<>();
        names.forEach((key, value) -> frozen.put(key, Set.copyOf(value)));
        return new PatternPack(prompts, results, frozen);
    }

    /** Returns the compiled prompt expressions. */
    public List<Pattern> prompt(PromptKind kind) { return prompts.getOrDefault(kind, List.of()); }

    /** Returns the compiled result expressions. */
    public List<Pattern> result(ResultKind kind) { return results.getOrDefault(kind, List.of()); }

    /** Returns configured aliases for a command group. */
    public Set<String> commandNames(String kind) { return commandNames.getOrDefault(kind, Set.of()); }

    private static <T> void addPatterns(Map<T, List<Pattern>> map, T key, JsonArray values, Consumer<String> warning) {
        List<Pattern> list = new ArrayList<>(map.getOrDefault(key, List.of()));
        for (JsonElement value : values) {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                warning.accept("Invalid BlockVault pattern skipped");
                continue;
            }
            String regex = value.getAsString();
            if (regex.length() > 256) {
                warning.accept("BlockVault pattern exceeds the 256-character limit");
                continue;
            }
            try { list.add(Pattern.compile(regex)); }
            catch (PatternSyntaxException e) { warning.accept("Invalid BlockVault pattern skipped"); }
        }
        map.put(key, List.copyOf(list));
    }

    private static JsonArray getArray(JsonObject object, String parent, String key, Consumer<String> warning) {
        if (!object.has(parent)) return new JsonArray();
        JsonElement group = object.get(parent);
        if (!group.isJsonObject()) {
            warning.accept("Invalid BlockVault pattern group ignored");
            return new JsonArray();
        }
        JsonObject values = group.getAsJsonObject();
        if (!values.has(key)) return new JsonArray();
        JsonElement array = values.get(key);
        if (!array.isJsonArray()) {
            warning.accept("Invalid BlockVault pattern list ignored");
            return new JsonArray();
        }
        return array.getAsJsonArray();
    }

    private static void addCommandNames(Map<String, Set<String>> names, String kind, JsonElement value) {
        if (!value.isJsonArray()) return;
        String key = kind.toLowerCase(Locale.ROOT);
        value.getAsJsonArray().forEach(alias -> {
            if (alias.isJsonPrimitive() && alias.getAsJsonPrimitive().isString()) {
                String name = alias.getAsString().trim().toLowerCase(Locale.ROOT);
                if (!name.isEmpty() && name.matches("[\\p{L}0-9_]+")) {
                    names.computeIfAbsent(key, ignored -> new java.util.HashSet<>()).add(name);
                }
            }
        });
    }
}
