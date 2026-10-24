package io.github.silkypanda.blockvault.core.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class CommandTemplateTest {
    @Test
    void rendersBuiltInTemplatesWithoutSlash() {
        assertEquals("register pass123 pass123", CommandTemplate.builtins().get("authme")
                .render(true, "pass123".toCharArray(), null, "Alex"));
    }

    @Test
    void detectsConfirmationAndEmailArguments() {
        assertEquals("register {password}", CommandTemplate.fromRegistrationPrompt(
                "Use /register <password>", java.util.Set.of("register")).register());
        CommandTemplate email = CommandTemplate.fromRegistrationPrompt(
                "Use /register <password> <confirm> <email>", java.util.Set.of("register"));
        assertEquals("register {password} {password} {email}", email.register());
    }

    @Test
    void rejectsSlashInjectionInRenderedCommand() {
        String rendered = CommandTemplate.builtins().get("authme-noconfirm")
                .render(true, "safe\n/logout".toCharArray(), null, "Alex");
        assertTrue(rendered.startsWith("register safe"));
        assertTrue(!rendered.contains("\n"));
    }

    @Test
    void insertedPasswordIsNotRescannedAsAnotherPlaceholder() {
        String rendered = new CommandTemplate("test", "login {password}", "register {password}")
                .render(false, "{email}".toCharArray(), "secret@example.test", "Alex");
        assertEquals("login {email}", rendered);
    }
}
