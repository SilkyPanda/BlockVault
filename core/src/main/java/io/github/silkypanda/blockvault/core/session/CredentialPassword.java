package io.github.silkypanda.blockvault.core.session;

import io.github.silkypanda.blockvault.core.validation.InputRules;

/** Validation shared by every path that saves or sends passwords. */
public final class CredentialPassword {
    private CredentialPassword() { }

    /**
     * A server password becomes one command argument, so it may not contain whitespace, control or invisible
     * characters or the section sign, and it must stay short enough for a Minecraft chat command.
     */
    public static boolean isValid(char[] password) {
        return InputRules.isPassword(password);
    }

    /** A master password follows the same character rules but may be longer; it is never sent to a server. */
    public static boolean isValidMaster(char[] password) {
        return InputRules.isMasterPassword(password);
    }
}
