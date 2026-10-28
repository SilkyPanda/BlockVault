package io.github.silkypanda.blockvault.core.session;

import io.github.silkypanda.blockvault.core.validation.AsciiInput;

/** Validation shared by every path that saves or sends passwords. */
public final class CredentialPassword {
    private CredentialPassword() { }

    /** Passwords use printable, non-space ASCII so one command argument represents the exact value. */
    public static boolean isValid(char[] password) {
        return AsciiInput.isPassword(password);
    }
}
