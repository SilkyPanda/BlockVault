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
    void registrationParsingStopsAtTheNextCommand() {
        assertEquals("register {password} {password}", CommandTemplate.fromRegistrationPrompt(
                "Use /register <password> <password> or /login <password>", java.util.Set.of("register")).register());
        assertEquals("register {password}", CommandTemplate.fromRegistrationPrompt(
                "Use /register <password> then /login <password>", java.util.Set.of("register")).register());
    }

    @Test
    void rejectsSlashInjectionInRenderedCommand() {
        // A newline followed by a second command is refused outright instead of being cleaned up.
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> CommandTemplate.builtins()
                .get("authme-noconfirm").render(true, "safe\n/logout".toCharArray(), null, "Alex"));
        // A leading slash would turn the argument into a different command.
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () -> new CommandTemplate("t", "{password}",
                "{password}").render(false, "/op me".toCharArray(), null, "Alex"));
    }

    @Test
    void insertedPasswordIsNotRescannedAsAnotherPlaceholder() {
        String rendered = new CommandTemplate("test", "login {password}", "register {password}")
                .render(false, "{email}".toCharArray(), "secret@example.test", "Alex");
        assertEquals("login {email}", rendered);
    }

    @org.junit.jupiter.api.Test
    void controlCharactersInArgumentsAreRefusedNotSilentlyRemoved() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> CommandTemplate.builtins().get("authme").render(false, "pa\nss".toCharArray(), "", "Alex"));
    }
}
