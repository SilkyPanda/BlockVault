package io.github.silkypanda.blockvault.core.gen;

import io.github.silkypanda.blockvault.core.detect.ServerRuleHint;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

/** Uniform printable-ASCII password generation with required character classes. */
public final class PasswordGenerator {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijklmnopqrstuvwxyz";
    private static final String DIGITS = "0123456789";
    private static final String FORBIDDEN = " '\"\\";

    private PasswordGenerator() { }

    /** Password policy. Disabled classes are excluded; symbols are filtered to safe printable ASCII. */
    public record Policy(int length, boolean upper, boolean lower, boolean digits, boolean symbols,
                         String symbolSet, boolean excludeAmbiguous) {
        public Policy {
            if (length < 8 || length > 64 || symbolSet == null || !(upper || lower || digits || symbols)) {
                throw new IllegalArgumentException("Invalid password policy");
            }
        }
        public static Policy defaults() { return new Policy(20, true, true, true, true, "!@#$%^&*-_", false); }
        public static Policy pickyServer() { return new Policy(16, true, true, true, false, "", false); }
        public static Policy max() { return new Policy(32, true, true, true, true, "!@#$%^&*-_", false); }
        public static Policy memorable() { return new Policy(20, false, false, false, true, "\u0000", false); }

        /** Tightens length and character constraints using a parsed server rule. */
        public Policy adjustFor(ServerRuleHint hint) {
            int adjusted = length;
            if (hint.minLength() != null) adjusted = Math.max(adjusted, hint.minLength());
            if (hint.maxLength() != null) adjusted = Math.min(adjusted, hint.maxLength());
            if (hint.minLength() != null && hint.maxLength() != null && hint.minLength() > hint.maxLength()) {
                throw new IllegalArgumentException("Server password length rules conflict");
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
        if (policy.equals(Policy.memorable())) return generateMemorable();
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

    /** Computes the policy's idealized Shannon entropy in bits. */
    public static double entropyBits(Policy policy) {
        if (policy.equals(Policy.memorable())) return 4 * (Math.log(1024) / Math.log(2)) + (Math.log(100) / Math.log(2));
        int alphabet = 0;
        if (policy.upper()) alphabet += filter(UPPER, policy.excludeAmbiguous()).length();
        if (policy.lower()) alphabet += filter(LOWER, policy.excludeAmbiguous()).length();
        if (policy.digits()) alphabet += filter(DIGITS, policy.excludeAmbiguous()).length();
        if (policy.symbols()) alphabet += filter(policy.symbolSet(), policy.excludeAmbiguous()).length();
        return policy.length() * (Math.log(alphabet) / Math.log(2));
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

    private static char[] generateMemorable() {
        List<String> words = new ArrayList<>(1024);
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(
                PasswordGenerator.class.getResourceAsStream("/assets/blockvault/password-words.txt"),
                java.nio.charset.StandardCharsets.UTF_8))) {
            String word;
            while ((word = reader.readLine()) != null) words.add(word);
        } catch (java.io.IOException | NullPointerException e) {
            throw new IllegalStateException("Memorable word list is unavailable");
        }
        if (words.size() != 1024) throw new IllegalStateException("Memorable word list is invalid");
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            if (i > 0) result.append('-');
            result.append(words.get(uniformIndex(RANDOM, words.size())));
        }
        result.append('-').append(uniformIndex(RANDOM, 10)).append(uniformIndex(RANDOM, 10));
        char[] password = new char[result.length()];
        result.getChars(0, result.length(), password, 0);
        return password;
    }
}
