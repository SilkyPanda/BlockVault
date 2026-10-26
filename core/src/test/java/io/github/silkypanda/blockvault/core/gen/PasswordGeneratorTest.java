package io.github.silkypanda.blockvault.core.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.random.RandomGenerator;
import io.github.silkypanda.blockvault.core.detect.ServerRuleHint;
import org.junit.jupiter.api.Test;

final class PasswordGeneratorTest {
    @Test
    void generatesRequestedLengthAndEveryEnabledClass() {
        PasswordGenerator.Policy policy = PasswordGenerator.Policy.defaults();
        char[] value = PasswordGenerator.generate(policy);
        String password = new String(value);
        assertEquals(policy.length(), value.length);
        assertTrue(password.chars().anyMatch(Character::isUpperCase));
        assertTrue(password.chars().anyMatch(Character::isLowerCase));
        assertTrue(password.chars().anyMatch(Character::isDigit));
        assertTrue(password.chars().anyMatch(c -> "!@#$%^&*-_".indexOf(c) >= 0));
        java.util.Arrays.fill(value, '\0');
    }

    @Test
    void forbiddenCharactersNeverAppear() {
        for (int i = 0; i < 100_000; i++) {
            char[] password = PasswordGenerator.generate(PasswordGenerator.Policy.max());
            for (char c : password) assertTrue(c >= 0x21 && c <= 0x7e && " '\"\\".indexOf(c) < 0);
            java.util.Arrays.fill(password, '\0');
        }
    }

    @Test
    void rejectionSamplingRejectsHighUnsignedValue() {
        RandomGenerator fake = new RandomGenerator() {
            private int calls;
            @Override public int nextInt() { return calls++ == 0 ? -1 : 1; }
            @Override public long nextLong() { throw new UnsupportedOperationException(); }
        };
        assertEquals(1, PasswordGenerator.uniformIndex(fake, 10));
    }

    @Test
    void memorablePresetUsesFourWordsAndTwoDigits() {
        char[] generated = PasswordGenerator.generate(PasswordGenerator.Policy.memorable());
        String value = new String(generated);
        assertEquals(22, generated.length);
        assertEquals(4, value.chars().filter(c -> c == '-').count());
        assertTrue(value.matches("[a-z]{4}(?:-[a-z]{4}){3}-[0-9]{2}"));
        java.util.Arrays.fill(generated, '\0');
    }

    @Test
    void memorablePresetChangesWordCountToFitServerLengthRules() {
        char[] shortPassword = PasswordGenerator.generate(PasswordGenerator.Policy.memorable()
                .adjustFor(new ServerRuleHint(null, 12, false)));
        assertEquals(12, shortPassword.length);
        assertEquals(2, new String(shortPassword).chars().filter(c -> c == '-').count());
        java.util.Arrays.fill(shortPassword, '\0');

        char[] longPassword = PasswordGenerator.generate(PasswordGenerator.Policy.memorable()
                .adjustFor(new ServerRuleHint(24, null, false)));
        assertEquals(27, longPassword.length);
        assertEquals(5, new String(longPassword).chars().filter(c -> c == '-').count());
        java.util.Arrays.fill(longPassword, '\0');
    }

    @Test
    void wordListAcceptsTheEffDiceFormatAndUsesTheFirst1024Words() throws Exception {
        StringBuilder file = new StringBuilder();
        for (int i = 0; i < 1296; i++) {
            file.append(1111 + i).append('\t').append(word(i)).append('\n');
        }
        file.append("6666\tyo-yo\n");
        java.util.List<String> words = PasswordGenerator.parseWordList(new java.io.StringReader(file.toString()));
        org.junit.jupiter.api.Assertions.assertEquals(1024, words.size());
        org.junit.jupiter.api.Assertions.assertEquals(word(0), words.getFirst());
    }

    @Test
    void wordListRejectsShortOrMalformedFiles() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> PasswordGenerator.parseWordList(new java.io.StringReader("1111\tacid\n1112\tacorn\n")));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> PasswordGenerator.parseWordList(new java.io.StringReader("1111\tnot-a-word\n")));
    }

    /** Distinct letters-only words: aaa, aab, ... */
    private static String word(int i) {
        return "w" + (char) ('a' + i / 676 % 26) + (char) ('a' + i / 26 % 26) + (char) ('a' + i % 26);
    }
}
