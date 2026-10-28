package io.github.silkypanda.blockvault.core.session;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class CredentialPasswordTest {
    @Test
    void rejectsEmptyWhitespaceAndControlCharacters() {
        assertFalse(CredentialPassword.isValid(new char[0]));
        assertFalse(CredentialPassword.isValid("has space".toCharArray()));
        assertFalse(CredentialPassword.isValid("has\ttab".toCharArray()));
        assertFalse(CredentialPassword.isValid("has\nlinebreak".toCharArray()));
        assertTrue(CredentialPassword.isValid("nospaces123".toCharArray()));
    }
}
