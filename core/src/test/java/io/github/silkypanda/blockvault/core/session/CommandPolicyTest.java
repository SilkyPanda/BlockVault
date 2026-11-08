package io.github.silkypanda.blockvault.core.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

final class CommandPolicyTest {
    @Test
    void extractsOnlyBareCommandNames() {
        assertEquals("login", CommandPolicy.bareCommandName(" /LOGIN "));
        assertEquals("login", CommandPolicy.bareCommandName("minecraft:login"));
        assertNull(CommandPolicy.bareCommandName("/login secret"));
        assertNull(CommandPolicy.bareCommandName("say hello"));
        assertNull(CommandPolicy.bareCommandName(null));
    }

    @Test
    void treatsShortAliasesAsSensitiveOnlyAfterTheirPrompt() {
        Set<String> aliases = Set.of("login", "reg", "unregister");
        assertTrue(CommandPolicy.isSensitiveAlias("unregister", aliases, false));
        assertFalse(CommandPolicy.isSensitiveAlias("reg", aliases, false));
        assertTrue(CommandPolicy.isSensitiveAlias("reg", aliases, true));
        assertFalse(CommandPolicy.isSensitiveAlias("register", aliases, true));
    }

    @Test
    void holdsOnlyRecognizedBareLoginCommandsWhenEnabled() {
        Set<String> loginNames = Set.of("login", "l");
        assertTrue(CommandPolicy.shouldHoldBareLogin("/login", loginNames, true, false));
        assertFalse(CommandPolicy.shouldHoldBareLogin("/login password", loginNames, true, false));
        assertFalse(CommandPolicy.shouldHoldBareLogin("/l", loginNames, true, false));
        assertTrue(CommandPolicy.shouldHoldBareLogin("/l", loginNames, true, true));
        assertFalse(CommandPolicy.shouldHoldBareLogin("/login", loginNames, false, false));
    }
}
