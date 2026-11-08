package io.github.silkypanda.blockvault.core.session;

import io.github.silkypanda.blockvault.core.gen.PasswordGenerator;

/** Maps stored preference values to the session configuration applied on the session-owning thread. */
public final class SessionPreferences {
    private SessionPreferences() { }

    public static PasswordGenerator.Policy generatorPolicy(String preset) {
        return "memorable".equals(preset)
                ? PasswordGenerator.Policy.memorable()
                : PasswordGenerator.Policy.pickyServer();
    }

    public static void apply(LoginSession session, boolean autofill, boolean passwordlessLogin,
                             long autofillDelayMillis, String generatorPreset) {
        session.configure(autofill, passwordlessLogin, autofillDelayMillis, generatorPolicy(generatorPreset));
    }
}
