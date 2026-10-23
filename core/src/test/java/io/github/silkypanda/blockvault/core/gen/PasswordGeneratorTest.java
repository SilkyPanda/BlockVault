package io.github.silkypanda.blockvault.core.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.random.RandomGenerator;
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
            @Override public long nextLong() { return calls++ == 0 ? -1L : 1L; }
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
}
