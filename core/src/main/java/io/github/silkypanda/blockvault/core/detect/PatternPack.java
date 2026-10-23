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
        JsonObject user = userFileOrNull != null && Files.isRegularFile(userFileOrNull)
                ? JsonParser.parseString(Files.readString(userFileOrNull)).getAsJsonObject() : new JsonObject();
        Map<PromptKind, List<Pattern>> prompts = new EnumMap<>(PromptKind.class);
        addPatterns(prompts, PromptKind.LOGIN, getArray(base, "prompt", "login"), ignored -> { });
        addPatterns(prompts, PromptKind.REGISTER, getArray(base, "prompt", "register"), ignored -> { });
        addPatterns(prompts, PromptKind.LOGIN, getArray(user, "prompt", "login"), warning);
        addPatterns(prompts, PromptKind.REGISTER, getArray(user, "prompt", "register"), warning);
        Map<ResultKind, List<Pattern>> results = new EnumMap<>(ResultKind.class);
        addPatterns(results, ResultKind.SUCCESS, getArray(base, "result", "success"), ignored -> { });
        addPatterns(results, ResultKind.FAILURE, getArray(base, "result", "failure"), ignored -> { });
        addPatterns(results, ResultKind.PASSWORD_RULE, getArray(base, "result", "passwordRules"), ignored -> { });
        addPatterns(results, ResultKind.SUCCESS, getArray(user, "result", "success"), warning);
        addPatterns(results, ResultKind.FAILURE, getArray(user, "result", "failure"), warning);
        addPatterns(results, ResultKind.PASSWORD_RULE, getArray(user, "result", "passwordRules"), warning);
        Map<String, Set<String>> names = new java.util.HashMap<>();
        for (String kind : List.of("login", "register")) {
            JsonArray values = getArray(base, "commandNames", kind);
            values.forEach(v -> names.computeIfAbsent(kind, ignored -> new java.util.HashSet<>()).add(v.getAsString().toLowerCase(Locale.ROOT)));
            getArray(user, "commandNames", kind).forEach(v -> names.computeIfAbsent(kind, ignored -> new java.util.HashSet<>()).add(v.getAsString().toLowerCase(Locale.ROOT)));
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
            String regex = value.getAsString();
            try { list.add(Pattern.compile(regex)); }
            catch (PatternSyntaxException e) { warning.accept("Invalid BlockVault pattern skipped"); }
        }
        map.put(key, List.copyOf(list));
    }

    private static JsonArray getArray(JsonObject object, String parent, String key) {
        if (!object.has(parent) || !object.getAsJsonObject(parent).has(key)) return new JsonArray();
        return object.getAsJsonObject(parent).getAsJsonArray(key);
    }
}
