package io.github.silkypanda.blockvault.core.detect;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/** Cheap-prefiltered matching for plain system-message text. */
public final class PromptMatcher {
    private static final String[] KEYWORDS = {"login", "register", "password", "authenticate", "log in", "connexion", "senha", "contraseña"};
    private final PatternPack pack;

    public PromptMatcher(PatternPack pack) { this.pack = pack; }

    /** Matches a prompt after stripping residual legacy color codes. */
    public Optional<PromptKind> matchPrompt(String plain) {
        String text = clean(plain);
        String lower = text.toLowerCase(Locale.ROOT);
        boolean candidate = text.indexOf('/') >= 0;
        for (String keyword : KEYWORDS) candidate |= lower.contains(keyword);
        if (!candidate) return Optional.empty();
        if (matches(pack.prompt(PromptKind.REGISTER), text)) return Optional.of(PromptKind.REGISTER);
        if (matches(pack.prompt(PromptKind.LOGIN), text)) return Optional.of(PromptKind.LOGIN);
        return Optional.empty();
    }

    static boolean matches(java.util.List<Pattern> patterns, String text) {
        return patterns.stream().anyMatch(pattern -> pattern.matcher(text).find());
    }

    static String clean(String plain) {
        if (plain == null) return "";
        return plain.replaceAll("(?i)\\u00a7[0-9A-FK-OR]", "");
    }
}
