package io.github.silkypanda.blockvault.core.session;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class CommandTemplateTest {
    @Test
    void rendersBuiltInTemplatesWithoutSlash() {
        assertEquals("register pass123 pass123", CommandTemplate.builtins().get("authme")
                .render(true, "pass123".toCharArray(), null, "Alex", null));
    }
}
