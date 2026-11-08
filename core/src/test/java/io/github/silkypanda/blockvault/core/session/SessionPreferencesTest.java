package io.github.silkypanda.blockvault.core.session;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.silkypanda.blockvault.core.gen.PasswordGenerator;
import org.junit.jupiter.api.Test;

final class SessionPreferencesTest {
    @Test
    void mapsKnownAndUnknownGeneratorPresetsToExistingPolicies() {
        assertEquals(PasswordGenerator.Policy.memorable(), SessionPreferences.generatorPolicy("memorable"));
        assertEquals(PasswordGenerator.Policy.pickyServer(), SessionPreferences.generatorPolicy("basic"));
        assertEquals(PasswordGenerator.Policy.pickyServer(), SessionPreferences.generatorPolicy("unknown"));
        assertEquals(PasswordGenerator.Policy.pickyServer(), SessionPreferences.generatorPolicy(null));
    }
}
