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
        assertEquals(4, value.chars().filter(c -> c == '-').count());
        assertTrue(value.matches("[a-z]{4}(?:-[a-z]{4}){3}-[0-9]{2}"));
        java.util.Arrays.fill(generated, '\0');
    }

    @Test
    void memorablePresetChangesWordCountToFitServerLengthRules() {
        char[] shortPassword = PasswordGenerator.generate(PasswordGenerator.Policy.memorable()
                .adjustFor(new ServerRuleHint(null, 12, false)));
        assertEquals(11, shortPassword.length);
        assertEquals(2, new String(shortPassword).chars().filter(c -> c == '-').count());
        java.util.Arrays.fill(shortPassword, '\0');

        char[] longPassword = PasswordGenerator.generate(PasswordGenerator.Policy.memorable()
                .adjustFor(new ServerRuleHint(24, null, false)));
        assertEquals(26, longPassword.length);
        assertEquals(5, new String(longPassword).chars().filter(c -> c == '-').count());
        java.util.Arrays.fill(longPassword, '\0');
    }
}
