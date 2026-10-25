package io.github.silkypanda.blockvault.core.detect;

import java.util.Locale;
import java.util.Optional;
import java.util.EnumSet;
import java.util.Set;
import java.util.regex.Pattern;

/** Cheap-prefiltered matching for plain system-message text. */
public final class PromptMatcher {
    private static final String[] KEYWORDS = {"login", "register", "password", "authenticate", "log in", "connexion", "senha", "contraseña"};
    /** Legacy colour codes, including the 14-character "section-x" RGB form, compiled once. */
    private static final Pattern COLOR_CODES = Pattern.compile("(?i)\u00a7x(?:\u00a7[0-9a-f]){6}|\u00a7[0-9A-FK-OR]");
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
        String lower = text.toLowerCase(Locale.ROOT);
        boolean candidate = text.indexOf('/') >= 0;
        for (String keyword : KEYWORDS) candidate |= lower.contains(keyword);
        if (!candidate) return Set.of();
        EnumSet<PromptKind> result = EnumSet.noneOf(PromptKind.class);
        for (PromptKind kind : PromptKind.values()) if (matches(pack.prompt(kind), text)) result.add(kind);
        return Set.copyOf(result);
    }

    static boolean matches(java.util.List<Pattern> patterns, String text) {
        return patterns.stream().anyMatch(pattern -> pattern.matcher(text).find());
    }

    static String clean(String plain) {
        if (plain == null) return "";
        return COLOR_CODES.matcher(plain).replaceAll("");
    }
}
