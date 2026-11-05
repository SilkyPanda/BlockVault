package io.github.silkypanda.blockvault.core.validation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class InputRulesTest {
    @Test
    void passwordsAcceptLettersOfAnyLanguageDigitsPunctuationAndSymbols() {
        for (String ok : new String[] {"hunter2", "P@ss-w0rd!", "p\u00e4ssword", "\u043f\u0430\u0440\u043e\u043b\u044c1",
                "\u5bc6\u7801123", "caf\u00e9_\u2603", "a\"b'c\\d/e", "e\u0301\u0301x"}) {
            assertTrue(InputRules.isPassword(ok.toCharArray()), ok);
            assertTrue(InputRules.isMasterPassword(ok.toCharArray()), ok);
        }
    }

    @Test
    void passwordsRefuseWhitespaceOfEveryKind() {
        for (String bad : new String[] {"a b", "a\tb", "a\nb", "a\rb", "a\u00a0b", "a\u2003b", "a\u3000b",
                "a\u2028b", "a\u2029b", " ", "ab "}) {
            assertFalse(InputRules.isPassword(bad.toCharArray()), bad);
            assertFalse(InputRules.isMasterPassword(bad.toCharArray()), bad);
        }
    }

    @Test
    void passwordsRefuseCharactersMinecraftOrAServerWouldMangle() {
        for (String bad : new String[] {"a\u00a7b", "a\u0000b", "a\u007fb", "a\u001bb", "a\u200bb", "a\u200eb",
                "a\ufeffb", "a\u3164b", "a\u2800b", "a\ue000b", "a\ud800b", "\udc00a", "a\u0378b"}) {
            assertFalse(InputRules.isPassword(bad.toCharArray()), "U+" + Integer.toHexString(bad.codePointAt(1)));
        }
    }

    @Test
    void passwordLengthLimitsDifferForServerAndMaster() {
        assertTrue(InputRules.isPassword("a".repeat(100).toCharArray()));
        assertFalse(InputRules.isPassword("a".repeat(101).toCharArray()));
        assertTrue(InputRules.isMasterPassword("a".repeat(256).toCharArray()));
        assertFalse(InputRules.isMasterPassword("a".repeat(257).toCharArray()));
        assertFalse(InputRules.isPassword(new char[0]));
        assertFalse(InputRules.isPassword(null));
    }

    @Test
    void charactersAreCountedAsCodePoints() {
        assertTrue(InputRules.characterCount("\ud83d\ude00\ud83d\ude00".toCharArray()) == 2);
        assertTrue(InputRules.isPasswordText("\ud83d\ude00"));
    }

    @Test
    void passwordArgumentsMustAlsoFitTheLengthLimit() {
        assertTrue(InputRules.isPasswordArgument("p\u00e4ss"));
        assertFalse(InputRules.isPasswordArgument("a".repeat(101)));
        assertFalse(InputRules.isPasswordArgument(""));
    }

    @Test
    void textAllowsOrdinarySpacesButNoControlOrInvisibleCharacters() {
        assertTrue(InputRules.isText("C:\\Users\\Jos\u00e9\\My Folder"));
        assertTrue(InputRules.isText("play.example.com"));
        assertFalse(InputRules.isText("a\tb"));
        assertFalse(InputRules.isText("a\u00a0b"));
        assertFalse(InputRules.isText("a\u200bb"));
        assertFalse(InputRules.isText("a\u00a7b"));
    }

    @Test
    void hostTextAllowsInternationalNamesButNoSpacesOrSymbols() {
        assertTrue(InputRules.isHostText("b\u00fccher.de:25565"));
        assertTrue(InputRules.isHostText("[::1]:25565"));
        assertFalse(InputRules.isHostText("play example.com"));
        assertFalse(InputRules.isHostText("play/example.com"));
        assertFalse(InputRules.isHostText("play@example.com"));
    }

    @Test
    void usernamesStayLettersDigitsAndUnderscores() {
        assertTrue(InputRules.isUsernameText("Steve_1"));
        assertFalse(InputRules.isUsernameText("Ste ve"));
        assertFalse(InputRules.isUsernameText("St\u00e9ve"));
    }

    @Test
    void emailsMayUseInternationalLettersButNoSpacesOrQuotedForms() {
        assertTrue(InputRules.isEmail("steve@example.com"));
        assertTrue(InputRules.isEmail("st.eve+mc@sub.example.org"));
        assertTrue(InputRules.isEmail("j\u00fcrgen@b\u00fccher.de"));
        assertFalse(InputRules.isEmail("steve @example.com"));
        assertFalse(InputRules.isEmail("\"steve\"@example.com"));
        assertFalse(InputRules.isEmail("steve@example"));
        assertFalse(InputRules.isEmail("steve@exa\u00a7mple.com"));
    }
}
