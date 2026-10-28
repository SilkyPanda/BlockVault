package io.github.silkypanda.blockvault.core.detect;

import java.util.Locale;
import java.util.Optional;
import java.util.EnumSet;
import java.util.Set;
import java.util.regex.Pattern;

/** Cheap-prefiltered matching for plain system-message text. */
public final class PromptMatcher {
    private static final String[] KEYWORDS = {"login", "register", "password", "authenticate", "log in", "connexion", "senha", "contraseña", "anmelden", "registrieren"};
    /** Legacy colour codes, including the 14-character "section-x" RGB form, compiled once. */
    private static final Pattern COLOR_CODES = Pattern.compile("(?i)\u00a7x(?:\u00a7[0-9a-f]){6}|\u00a7[0-9A-FK-OR]");
    private static final Pattern PLAYER_CHAT = Pattern.compile("(?iu)^\\s*<[^<>\\r\\n]{1,32}>\\s*");
    private static final Pattern DIRECTIVE = Pattern.compile("(?iu)\\b(?:please|must|need\\s+to|use|enter|type|authenticate|log\\s+in|login|register|debes|debe|inicia(?:r)?\\s+sesión|registra(?:r)?|anmelden|registrieren|bitte|musst|müssen|einloggen)\\b");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\s+<[^<>\\r\\n]{1,64}>");
    private final PatternPack pack;

    public PromptMatcher(PatternPack pack) { this.pack = pack; }

    /** Matches a prompt after stripping residual legacy color codes. */
    public Optional<PromptKind> matchPrompt(String plain) {
        Set<PromptKind> matches = matchingPrompts(plain);
        if (matches.contains(PromptKind.REGISTER)) return Optional.of(PromptKind.REGISTER);
        if (matches.contains(PromptKind.LOGIN)) return Optional.of(PromptKind.LOGIN);
        return Optional.empty();
    }

    /** Returns all matching prompt kinds so session state can resolve messages mentioning both. */
    public Set<PromptKind> matchingPrompts(String plain) {
        String text = clean(plain);
        if (PLAYER_CHAT.matcher(text).find()) return Set.of();
        String lower = text.toLowerCase(Locale.ROOT);
        boolean candidate = text.indexOf('/') >= 0;
        for (String keyword : KEYWORDS) candidate |= lower.contains(keyword);
        if (!candidate) return Set.of();
        EnumSet<PromptKind> result = EnumSet.noneOf(PromptKind.class);
        for (PromptKind kind : PromptKind.values()) {
            if (matches(pack.prompt(kind), text) && isDirectedPrompt(kind, text)) result.add(kind);
        }
        return Set.copyOf(result);
    }

    /** Prompt patterns must identify a configured command and an instruction or argument placeholder. */
    private boolean isDirectedPrompt(PromptKind kind, String text) {
        String group = kind == PromptKind.LOGIN ? "login" : "register";
        for (String alias : pack.commandNames(group)) {
            Pattern command = Pattern.compile("(?iu)/(?:[\\p{L}0-9_]+:)?" + Pattern.quote(alias) + "\\b");
            java.util.regex.Matcher found = command.matcher(text);
            while (found.find()) {
                if (PLACEHOLDER.matcher(text.substring(found.end())).lookingAt()) return true;
                String before = text.substring(Math.max(0, found.start() - 120), found.start());
                if (DIRECTIVE.matcher(before).find()) return true;
            }
        }
        return false;
    }

    /** Total matching time allowed for one message across all patterns. */
    private static final long MATCH_BUDGET_NANOS = 25_000_000L;

    /** Returns true if any pattern matches; a pattern that runs out of time counts as "no match". */
    static boolean matches(java.util.List<Pattern> patterns, String text) {
        CharSequence guarded = new DeadlineCharSequence(text, MATCH_BUDGET_NANOS);
        try {
            for (Pattern pattern : patterns) if (pattern.matcher(guarded).find()) return true;
            return false;
        } catch (DeadlineCharSequence.TimeoutException slowPattern) {
            return false;
        }
    }

    static String clean(String plain) {
        if (plain == null) return "";
        return COLOR_CODES.matcher(plain).replaceAll("");
    }
}
