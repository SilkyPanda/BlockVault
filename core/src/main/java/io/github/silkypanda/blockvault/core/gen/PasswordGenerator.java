package io.github.silkypanda.blockvault.core.gen;

import io.github.silkypanda.blockvault.core.detect.ServerRuleHint;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

/** Uniform printable-ASCII password generation with required character classes. */
public final class PasswordGenerator {
    private static final SecureRandom RANDOM = new SecureRandom();
    /** Number of words used by the memorable preset (10 bits each). */
    static final int WORD_COUNT = 1024;
    private static final String UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijklmnopqrstuvwxyz";
    private static final String DIGITS = "0123456789";
    private static final String FORBIDDEN = " '\"\\";

    private PasswordGenerator() { }

    /** Password policy. Disabled classes are excluded; symbols are filtered to safe printable ASCII. */
    public record Policy(int length, boolean upper, boolean lower, boolean digits, boolean symbols,
                         String symbolSet, boolean excludeAmbiguous, int memorableWordCount,
                         int memorableMinLength, int memorableMaxLength) {
        public Policy(int length, boolean upper, boolean lower, boolean digits, boolean symbols,
                      String symbolSet, boolean excludeAmbiguous) {
            this(length, upper, lower, digits, symbols, symbolSet, excludeAmbiguous, 0, 0, 0);
        }
        public Policy(int length, boolean upper, boolean lower, boolean digits, boolean symbols,
                      String symbolSet, boolean excludeAmbiguous, int memorableWordCount) {
            this(length, upper, lower, digits, symbols, symbolSet, excludeAmbiguous, memorableWordCount, 0, 0);
        }
        public Policy {
            if (length < 8 || length > 64 || symbolSet == null || !(upper || lower || digits || symbols)
                    || memorableWordCount < 0 || memorableWordCount == 1 || memorableWordCount > 12) {
                throw new IllegalArgumentException("Invalid password policy");
            }
        }
        public static Policy defaults() { return new Policy(20, true, true, true, true, "!@#$%^&*-_", false); }
        public static Policy pickyServer() { return new Policy(16, true, true, true, false, "", false); }
        public static Policy max() { return new Policy(32, true, true, true, true, "!@#$%^&*-_", false); }
        public static Policy memorable() { return memorable(4); }
        private static Policy memorable(int words) {
            return new Policy(5 * words + 2, false, false, false, true, "", false, words);
        }

        /** Tightens length and character constraints using a parsed server rule. */
        public Policy adjustFor(ServerRuleHint hint) {
            if (memorableWordCount > 0) {
                if (hint.alphanumericOnly()) throw new IllegalArgumentException("Memorable passwords require hyphens");
                int min = hint.minLength() == null ? 8 : hint.minLength();
                int max = hint.maxLength() == null ? 64 : hint.maxLength();
                if (min > max) throw new IllegalArgumentException("Server password length rules conflict");
                // Word lengths vary, so pick the word count whose *expected* length fits, then make generation
                // retry until the real length is inside the server's range.
                double average = WordList.averageLength();
                int selected = -1;
                for (int words = 2; words <= 12; words++) {
                    double expected = words * average + words + 2;
                    if (expected >= min && expected <= max
                            && (selected < 0 || Math.abs(words - memorableWordCount) < Math.abs(selected - memorableWordCount))) {
                        selected = words;
                    }
                }
                if (selected < 0) throw new IllegalArgumentException("Server length rule cannot fit the memorable preset");
                Policy base = memorable(selected);
                return new Policy(base.length(), false, false, false, true, "", false, selected,
                        hint.minLength() == null ? 0 : hint.minLength(), hint.maxLength() == null ? 0 : hint.maxLength());
            }
            int adjusted = length;
            if (hint.minLength() != null) adjusted = Math.max(adjusted, hint.minLength());
            if (hint.maxLength() != null) adjusted = Math.min(adjusted, hint.maxLength());
            if (hint.minLength() != null && hint.maxLength() != null && hint.minLength() > hint.maxLength()) {
                throw new IllegalArgumentException("Server password length rules conflict");
            }
            if ((hint.minLength() != null && hint.minLength() > 64)
                    || (hint.maxLength() != null && hint.maxLength() < 8)) {
                throw new IllegalArgumentException("Server password length is outside the supported range");
            }
            adjusted = Math.max(8, Math.min(64, adjusted));
            boolean nextUpper = upper;
            boolean nextLower = lower;
            boolean nextDigits = digits;
            if (hint.alphanumericOnly() && !(nextUpper || nextLower || nextDigits)) {
                nextLower = true;
                nextDigits = true;
            }
            return new Policy(adjusted, nextUpper, nextLower, nextDigits, hint.alphanumericOnly() ? false : symbols,
                    hint.alphanumericOnly() ? "" : symbolSet, excludeAmbiguous);
        }
    }

    /** Generates a secret as a caller-owned character array. */
    public static char[] generate(Policy policy) {
        if (policy.memorableWordCount() > 0) {
            return generateMemorable(policy.memorableWordCount(), policy.memorableMinLength(), policy.memorableMaxLength());
        }
        List<String> classes = new ArrayList<>();
        if (policy.upper()) classes.add(filter(UPPER, policy.excludeAmbiguous()));
        if (policy.lower()) classes.add(filter(LOWER, policy.excludeAmbiguous()));
        if (policy.digits()) classes.add(filter(DIGITS, policy.excludeAmbiguous()));
        if (policy.symbols()) classes.add(filter(policy.symbolSet(), policy.excludeAmbiguous()));
        if (classes.stream().anyMatch(String::isEmpty)) throw new IllegalArgumentException("Enabled character class is empty");
        StringBuilder combined = new StringBuilder();
        classes.forEach(combined::append);
        char[] result = new char[policy.length()];
        int index = 0;
        for (String enabled : classes) result[index++] = enabled.charAt(uniformIndex(RANDOM, enabled.length()));
        while (index < result.length) result[index++] = combined.charAt(uniformIndex(RANDOM, combined.length()));
        for (int i = result.length - 1; i > 0; i--) {
            int j = uniformIndex(RANDOM, i + 1);
            char swap = result[i]; result[i] = result[j]; result[j] = swap;
        }
        return result;
    }

    /** Rejection sampling avoids modulo bias and accepts a testable random source. */
    public static int uniformIndex(java.util.random.RandomGenerator random, int bound) {
        if (bound <= 0) throw new IllegalArgumentException("Bound must be positive");
        long range = 1L << 32;
        long limit = range - range % bound;
        long value;
        do {
            value = Integer.toUnsignedLong(random.nextInt());
        } while (value >= limit);
        return (int) (value % bound);
    }

    private static String filter(String source, boolean excludeAmbiguous) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c < 0x21 || c > 0x7e || FORBIDDEN.indexOf(c) >= 0) continue;
            if (excludeAmbiguous && "O0oIl1|".indexOf(c) >= 0) continue;
            if (result.indexOf(String.valueOf(c)) < 0) result.append(c);
        }
        return result.toString();
    }

    /** Builds "word-word-word-word-NN", retrying until the length is inside the optional bounds (0 = no bound). */
    private static char[] generateMemorable(int wordCount, int minLength, int maxLength) {
        List<String> words = WordList.VALUE;
        for (int attempt = 0; attempt < 10_000; attempt++) {
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < wordCount; i++) {
                if (i > 0) result.append('-');
                result.append(words.get(uniformIndex(RANDOM, words.size())));
            }
            result.append('-').append(uniformIndex(RANDOM, 10)).append(uniformIndex(RANDOM, 10));
            if ((minLength > 0 && result.length() < minLength) || (maxLength > 0 && result.length() > maxLength)) {
                continue;
            }
            char[] password = new char[result.length()];
            result.getChars(0, result.length(), password, 0);
            return password;
        }
        throw new IllegalStateException("Could not build a memorable password inside the server's length rule");
    }

    private static List<String> loadWordList() {
        java.io.InputStream resource = PasswordGenerator.class.getResourceAsStream("/assets/blockvault/password-words.txt");
        if (resource == null) throw new IllegalStateException("Memorable word list is unavailable");
        try (java.io.Reader reader = new java.io.InputStreamReader(resource, java.nio.charset.StandardCharsets.UTF_8)) {
            return parseWordList(reader);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Memorable word list is unavailable", e);
        }
    }

    /**
     * Reads a word list: one word per line, or the EFF diceware format {@code 1111<TAB>word}. Words are lower-cased
     * and de-duplicated, entries that are not purely letters (hyphenated words) are skipped, and exactly the first
     * {@link #WORD_COUNT} words are used (any subset of equally likely words is itself uniform, so this keeps
     * exactly 10 bits per word).
     */
    static List<String> parseWordList(java.io.Reader reader) throws java.io.IOException {
        java.util.LinkedHashSet<String> words = new java.util.LinkedHashSet<>();
        try (java.io.BufferedReader lines = new java.io.BufferedReader(reader)) {
            String line;
            while ((line = lines.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;
                String[] parts = trimmed.split("\\s+");
                String word = parts[parts.length - 1].toLowerCase(java.util.Locale.ROOT);
                // Hyphenated entries such as "yo-yo" are skipped: the hyphen is the separator between words.
                if (word.matches("[a-z]{3,8}")) words.add(word);
            }
        }
        if (words.size() < WORD_COUNT) throw new IllegalStateException("Memorable word list is too short");
        return List.copyOf(new ArrayList<>(words).subList(0, WORD_COUNT));
    }

    private static final class WordList {
        private static final List<String> VALUE = loadWordList();

        static double averageLength() {
            return VALUE.stream().mapToInt(String::length).average().orElse(4);
        }
    }
}
