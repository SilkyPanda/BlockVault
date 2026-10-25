package io.github.silkypanda.blockvault.core.detect;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Classifies plain server responses and extracts password constraints. */
public final class ResultMatcher {
    private static final String LEN = "\\s*:?\\s*(\\d{1,3})";
    private static final Pattern RANGE = Pattern.compile("(?iu)(?:between|from)\\s+(\\d{1,3})\\s*(?:and|to|-|\u2013)\\s*(\\d{1,3})"
            + "|\\b(\\d{1,3})\\s*(?:-|\u2013|to)\\s*(\\d{1,3})\\s+(?:characters?|chars?|letters?)");
    private static final Pattern MIN = Pattern.compile("(?iu)(?:at\\s+least|minimum(?:\\s+length)?(?:\\s+of)?"
            + "|min\\.?(?:\\s+length)?(?:\\s+of)?)" + LEN);
    private static final Pattern MIN_EXCLUSIVE = Pattern.compile("(?iu)(?:longer|more)\\s+than" + LEN + "\\s+(?:characters?|chars?)");
    private static final Pattern MAX = Pattern.compile("(?iu)(?:maximum(?:\\s+length)?(?:\\s+of)?|max\\.?(?:\\s+length)?(?:\\s+of)?"
            + "|at\\s+most|no\\s+more\\s+than|not\\s+(?:be\\s+)?(?:longer|more)\\s+than|up\\s+to)" + LEN);
    private static final Pattern MAX_EXCLUSIVE = Pattern.compile("(?iu)(?:shorter|less|fewer)\\s+than" + LEN);
    /** "non-alphanumeric" asks for a symbol, the opposite of "alphanumeric only", so it must not match. */
    private static final Pattern ALNUM = Pattern.compile("(?iu)(?<!non-)(?<!non )(?<!non)(?:alphanumeric"
            + "|letters\\s+and\\s+(?:numbers|digits)\\s+only|only\\s+(?:letters|alphanumeric))");
    private static final Pattern NEGATED_SUCCESS = Pattern.compile("(?iu)\\b(?:un\\s*successful(?:ly)?|unsuccessful(?:ly)?|not\\s+successful(?:ly)?)\\b");
    private final PatternPack pack;

    public ResultMatcher(PatternPack pack) { this.pack = pack; }

    /** Success takes precedence over incidental constraints in verbose success messages. */
    public ResultKind classify(String plain) {
        String text = PromptMatcher.clean(plain);
        if (NEGATED_SUCCESS.matcher(text).find()) return ResultKind.FAILURE;
        if (PromptMatcher.matches(pack.result(ResultKind.SUCCESS), text)) return ResultKind.SUCCESS;
        if (PromptMatcher.matches(pack.result(ResultKind.FAILURE), text)) return ResultKind.FAILURE;
        if (PromptMatcher.matches(pack.result(ResultKind.PASSWORD_RULE), text)) return ResultKind.PASSWORD_RULE;
        return ResultKind.NONE;
    }

    /**
     * Extracts recognized length and alphanumeric-only constraints. When several numbers appear the strictest
     * reading is used: the largest minimum and the smallest maximum.
     */
    public Optional<ServerRuleHint> parseRuleHint(String plain) {
        String text = PromptMatcher.clean(plain).toLowerCase(Locale.ROOT);
        Integer min = null;
        Integer max = null;
        Matcher range = RANGE.matcher(text);
        while (range.find()) {
            Integer low = parse(range.group(1) != null ? range.group(1) : range.group(3));
            Integer high = parse(range.group(2) != null ? range.group(2) : range.group(4));
            min = largest(min, low);
            max = smallest(max, high);
        }
        min = largest(min, extreme(MIN.matcher(text), true, 0));
        min = largest(min, extreme(MIN_EXCLUSIVE.matcher(text), true, 1));
        max = smallest(max, extreme(MAX.matcher(text), false, 0));
        max = smallest(max, extreme(MAX_EXCLUSIVE.matcher(text), false, -1));
        boolean alphanumeric = ALNUM.matcher(text).find();
        if (min == null && max == null && !alphanumeric) return Optional.empty();
        return Optional.of(new ServerRuleHint(min, max, alphanumeric));
    }

    private static Integer extreme(Matcher matcher, boolean largest, int offset) {
        Integer best = null;
        while (matcher.find()) {
            Integer value = parse(matcher.group(1));
            if (value == null) continue;
            value += offset;
            best = largest ? largest(best, value) : smallest(best, value);
        }
        return best;
    }

    private static Integer largest(Integer a, Integer b) {
        if (a == null) return b;
        if (b == null) return a;
        return Math.max(a, b);
    }

    private static Integer smallest(Integer a, Integer b) {
        if (a == null) return b;
        if (b == null) return a;
        return Math.min(a, b);
    }

    private static Integer parse(String digits) {
        if (digits == null) return null;
        try { return Integer.valueOf(digits); }
        catch (NumberFormatException e) { return null; }
    }
}
