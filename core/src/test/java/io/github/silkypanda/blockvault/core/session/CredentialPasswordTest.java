package io.github.silkypanda.blockvault.core.session;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class CredentialPasswordTest {
    @Test
    void refusesEmptyWhitespaceAndControlCharacters() {
        assertFalse(CredentialPassword.isValid(new char[0]));
        assertFalse(CredentialPassword.isValid("has space".toCharArray()));
        assertFalse(CredentialPassword.isValid("has\ttab".toCharArray()));
        assertFalse(CredentialPassword.isValid("has\nlinebreak".toCharArray()));
        assertFalse(CredentialPassword.isValid("non\u00a0breaking".toCharArray()));
        assertFalse(CredentialPassword.isValid("section\u00a7sign".toCharArray()));
    }

    @Test
    void acceptsVisibleCharactersFromAnyLanguage() {
        assertTrue(CredentialPassword.isValid("nospaces123".toCharArray()));
        assertTrue(CredentialPassword.isValid("p\u00e4ssw\u00f6rd".toCharArray()));
        assertTrue(CredentialPassword.isValid("\u5bc6\u7801-123".toCharArray()));
    }

    @Test
    void masterPasswordsFollowTheSameCharacterRulesWithALongerLimit() {
        assertTrue(CredentialPassword.isValidMaster("correct-horse-battery-staple".toCharArray()));
        assertFalse(CredentialPassword.isValidMaster("correct horse".toCharArray()));
        assertTrue(CredentialPassword.isValidMaster("a".repeat(200).toCharArray()));
        assertFalse(CredentialPassword.isValid("a".repeat(200).toCharArray()));
    }
}
