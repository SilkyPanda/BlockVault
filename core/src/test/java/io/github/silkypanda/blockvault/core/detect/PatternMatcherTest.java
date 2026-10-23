package io.github.silkypanda.blockvault.core.detect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import org.junit.jupiter.api.Test;

final class PatternMatcherTest {
    private PatternPack pack() throws Exception {
        InputStream input = getClass().getResourceAsStream("/assets/blockvault/patterns/default.json");
        return PatternPack.load(input, null);
    }

    @Test
    void matchesPromptsAndClassifiesResults() throws Exception {
        PatternPack pack = pack();
        PromptMatcher prompts = new PromptMatcher(pack);
        ResultMatcher results = new ResultMatcher(pack);
        assertEquals(PromptKind.LOGIN, prompts.matchPrompt("Please login with /login <password>").orElseThrow());
        assertEquals(PromptKind.REGISTER, prompts.matchPrompt("Use /register <password>").orElseThrow());
        assertTrue(prompts.matchPrompt("hello there").isEmpty());
        assertEquals(ResultKind.SUCCESS, results.classify("Successfully registered!"));
        assertEquals(ResultKind.FAILURE, results.classify("Wrong password!"));
        assertEquals(8, results.parseRuleHint("Password must be at least 8 characters").orElseThrow().minLength());
    }

    @Test
    void invalidUserPatternIsSkippedWithOneWarning() throws Exception {
        java.nio.file.Path user = java.nio.file.Files.createTempFile("blockvault-pattern", ".json");
        try {
            java.nio.file.Files.writeString(user, "{\"prompt\":{\"login\":[\"[broken\"]}}");
            java.util.List<String> warnings = new java.util.ArrayList<>();
            PatternPack loaded = PatternPack.load(getClass().getResourceAsStream("/assets/blockvault/patterns/default.json"), user, warnings::add);
            assertEquals(1, warnings.size());
            assertEquals(PromptKind.LOGIN, new PromptMatcher(loaded).matchPrompt("/login please").orElseThrow());
        } finally {
            java.nio.file.Files.deleteIfExists(user);
        }
    }

    @Test
    void corpusContainsAtLeastFortySystemMessageExamples() throws Exception {
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(
                getClass().getResourceAsStream("/prompt-corpus.txt"), java.nio.charset.StandardCharsets.UTF_8))) {
            assertTrue(reader.lines().count() >= 40);
        }
    }

    @Test
    void recognizesAddedLanguageResults() throws Exception {
        ResultMatcher matcher = new ResultMatcher(pack());
        for (String success : java.util.List.of("Anmeldung erfolgreich", "Connexion réussie", "Başarıyla giriş yaptınız",
                "Zalogowano pomyślnie", "登录成功")) {
            assertEquals(ResultKind.SUCCESS, matcher.classify(success));
        }
        for (String failure : java.util.List.of("Falsches Passwort", "Mot de passe incorrect", "Yanlış şifre",
                "Nieprawidłowe hasło", "密码错误")) {
            assertEquals(ResultKind.FAILURE, matcher.classify(failure));
        }
    }
}
