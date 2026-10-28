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
        assertTrue(prompts.matchPrompt("[Ad] Join our discord. /register for rewards").isEmpty());
        assertTrue(prompts.matchPrompt("<Steve> hey everyone use /register <password>").isEmpty());
        assertTrue(prompts.matchPrompt("The word /login appears here as an example").isEmpty());
        assertEquals(ResultKind.SUCCESS, results.classify("Successfully registered!"));
        assertEquals(ResultKind.SUCCESS, results.classify(
                "Successfully registered! You can change it after at least 5 minutes."));
        assertEquals(ResultKind.FAILURE, results.classify("Wrong password!"));
        assertEquals(ResultKind.FAILURE, results.classify("Registration failed!"));
        assertEquals(8, results.parseRuleHint("Password must be at least 8 characters").orElseThrow().minLength());
    }

    @Test
    void negatedSuccessMessagesAreFailuresAndUnallowedCharactersAreRules() throws Exception {
        var matcher = new ResultMatcher(pack());
        assertEquals(ResultKind.FAILURE, matcher.classify("Unsuccessful login!"));
        assertEquals(ResultKind.FAILURE, matcher.classify("You were not successfully registered."));
        assertEquals(ResultKind.PASSWORD_RULE, matcher.classify("Password contains unallowed characters!"));
    }

    @Test
    void distinguishesRegistrationRulesFromLoginFailures() throws Exception {
        ResultMatcher matcher = new ResultMatcher(pack());
        for (String rule : java.util.List.of("Invalid password: must contain a digit",
                "Invalid password format", "Invalid password length, 8-16 characters",
                "Your password is too weak", "The chosen password isn't safe, please choose another one.",
                "Password is unsafe", "This password is too common")) {
            assertEquals(ResultKind.PASSWORD_RULE, matcher.classify(rule), rule);
        }
        for (String failure : java.util.List.of("You are already logged in!", "Login failed",
                "Incorrect login credentials", "Incorrect password")) {
            assertEquals(ResultKind.FAILURE, matcher.classify(failure), failure);
        }
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
    void malformedPatternShapesAndOversizedRegexesAreSkipped() throws Exception {
        java.nio.file.Path user = java.nio.file.Files.createTempFile("blockvault-pattern-shape", ".json");
        try {
            java.nio.file.Files.writeString(user, "{\"prompt\":5,\"result\":{\"success\":[1,{}]},\"commandNames\":[]}");
            java.util.List<String> warnings = new java.util.ArrayList<>();
            PatternPack loaded = PatternPack.load(getClass().getResourceAsStream("/assets/blockvault/patterns/default.json"), user, warnings::add);
            assertTrue(warnings.size() >= 2);
            assertEquals(PromptKind.LOGIN, new PromptMatcher(loaded).matchPrompt("/login please").orElseThrow());

            java.nio.file.Files.writeString(user, "{\"prompt\":{\"login\":[\"" + "a".repeat(257) + "\"]}}");
            warnings.clear();
            loaded = PatternPack.load(getClass().getResourceAsStream("/assets/blockvault/patterns/default.json"), user, warnings::add);
            assertEquals(1, warnings.size());
            assertEquals(PromptKind.LOGIN, new PromptMatcher(loaded).matchPrompt("/login please").orElseThrow());
        } finally {
            java.nio.file.Files.deleteIfExists(user);
        }
    }

    /** Every corpus line must be classified the way its label says (player chat is deliberately not in it). */
    @Test
    void everyCorpusLineMatchesItsLabel() throws Exception {
        PromptMatcher prompts = new PromptMatcher(pack());
        ResultMatcher results = new ResultMatcher(pack());
        java.util.List<String> mismatches = new java.util.ArrayList<>();
        int checked = 0;
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(
                getClass().getResourceAsStream("/prompt-corpus.txt"), java.nio.charset.StandardCharsets.UTF_8))) {
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] parts = line.split("\t", 2);
                String label = parts[0];
                String text = parts[1];
                checked++;
                String actual;
                switch (label) {
                    case "LOGIN", "REGISTER" -> actual = prompts.matchPrompt(text).map(Enum::name).orElse("NONE");
                    case "SUCCESS", "FAILURE", "PASSWORD_RULE" -> actual = results.classify(text).name();
                    default -> actual = prompts.matchPrompt(text).isPresent() ? "PROMPT"
                            : results.classify(text).name();
                }
                if (!actual.equals(label)) mismatches.add(label + " expected but got " + actual + ": " + text);
            }
        }
        assertTrue(checked >= 30);
        assertEquals(java.util.List.of(), mismatches);
    }

    /** A built-in pattern longer than the 256-character cap is silently dropped, so none may exceed it. */
    @Test
    void noBuiltInPatternIsDroppedByTheLengthLimit() throws Exception {
        com.google.gson.JsonObject root;
        try (java.io.Reader reader = new java.io.InputStreamReader(
                getClass().getResourceAsStream("/assets/blockvault/patterns/default.json"),
                java.nio.charset.StandardCharsets.UTF_8)) {
            root = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
        }
        int expected = 0;
        for (String group : java.util.List.of("prompt", "result")) {
            for (var entry : root.getAsJsonObject(group).entrySet()) expected += entry.getValue().getAsJsonArray().size();
        }
        PatternPack pack = pack();
        int loaded = 0;
        for (PromptKind kind : PromptKind.values()) loaded += pack.prompt(kind).size();
        for (ResultKind kind : ResultKind.values()) loaded += pack.result(kind).size();
        assertEquals(expected, loaded);
    }

    @Test
    void ruleHintsUseTheStrictestNumbersAndUnderstandRanges() throws Exception {
        ResultMatcher matcher = new ResultMatcher(pack());
        var both = matcher.parseRuleHint("Password must contain at least 1 digit and be at least 8 characters").orElseThrow();
        assertEquals(8, both.minLength());
        var range = matcher.parseRuleHint("Your password must be between 8 and 16 characters").orElseThrow();
        assertEquals(8, range.minLength());
        assertEquals(16, range.maxLength());
        assertEquals(16, matcher.parseRuleHint("Password must be no more than 16 characters").orElseThrow().maxLength());
        assertEquals(19, matcher.parseRuleHint("Password must be shorter than 20 characters").orElseThrow().maxLength());
        assertEquals(6, matcher.parseRuleHint("min. 6 characters").orElseThrow().minLength());
    }

    @Test
    void nonAlphanumericIsNotTreatedAsAlphanumericOnly() throws Exception {
        ResultMatcher matcher = new ResultMatcher(pack());
        assertTrue(matcher.parseRuleHint("Password must contain a non-alphanumeric character").isEmpty());
        assertTrue(matcher.parseRuleHint("Password may only contain alphanumeric characters").orElseThrow().alphanumericOnly());
    }

    @Test
    void passwordChangeAndUnregisterAreRecognisedAsSuccess() throws Exception {
        ResultMatcher matcher = new ResultMatcher(pack());
        for (String text : java.util.List.of("Password changed successfully!", "Your password has been changed",
                "Successfully unregistered!", "Account successfully unregistered",
                "You have successfully unregistered your account")) {
            assertEquals(ResultKind.SUCCESS, matcher.classify(text), text);
        }
    }

    @Test
    void rgbColourCodesAreStrippedBeforeMatching() throws Exception {
        ResultMatcher matcher = new ResultMatcher(pack());
        String coloured = "\u00a7x\u00a7f\u00a7f\u00a70\u00a70\u00a70\u00a70Successfully \u00a7x\u00a70\u00a70\u00a7f\u00a7f\u00a70\u00a70registered";
        assertEquals(ResultKind.SUCCESS, matcher.classify(coloured));
    }

    @Test
    void catastrophicPatternsAreStoppedByTheTimeBudget() {
        java.util.List<java.util.regex.Pattern> evil = java.util.List.of(java.util.regex.Pattern.compile("(a+)+$"));
        long start = System.nanoTime();
        boolean matched = PromptMatcher.matches(evil, "a".repeat(60) + "b");
        long millis = (System.nanoTime() - start) / 1_000_000;
        assertEquals(false, matched);
        assertTrue(millis < 1000, "took " + millis + " ms");
    }
}
