package io.github.silkypanda.blockvault.core.detect;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Classifies plain server responses and extracts password constraints. */
public final class ResultMatcher {
    private static final Pattern MIN = Pattern.compile("(?iu)(?:at least|minimum(?: of)?|min(?:imum)? length(?: of)?)\\s*(\\d+)");
    private static final Pattern MAX = Pattern.compile("(?iu)(?:maximum(?: of)?|at most|max(?:imum)? length(?: of)?)\\s*(\\d+)");
    private static final Pattern ALNUM = Pattern.compile("(?iu)(?:alphanumeric|letters and numbers only|letters and digits only)");
    private final PatternPack pack;

    public ResultMatcher(PatternPack pack) { this.pack = pack; }

    /** Success takes precedence over incidental constraints in verbose success messages. */
    public ResultKind classify(String plain) {
        String text = PromptMatcher.clean(plain);
        if (PromptMatcher.matches(pack.result(ResultKind.SUCCESS), text)) return ResultKind.SUCCESS;
        if (PromptMatcher.matches(pack.result(ResultKind.FAILURE), text)) return ResultKind.FAILURE;
        if (PromptMatcher.matches(pack.result(ResultKind.PASSWORD_RULE), text)) return ResultKind.PASSWORD_RULE;
        return ResultKind.NONE;
    }

    /** Extracts recognized length and alphanumeric-only constraints. */
    public Optional<ServerRuleHint> parseRuleHint(String plain) {
        String text = PromptMatcher.clean(plain).toLowerCase(Locale.ROOT);
        Integer min = number(MIN.matcher(text));
        Integer max = number(MAX.matcher(text));
        boolean alphanumeric = ALNUM.matcher(text).find();
        if (min == null && max == null && !alphanumeric) return Optional.empty();
        return Optional.of(new ServerRuleHint(min, max, alphanumeric));
    }

    private static Integer number(Matcher matcher) {
        if (!matcher.find()) return null;
        try { return Integer.valueOf(matcher.group(1)); }
        catch (NumberFormatException e) { return null; }
    }
}
