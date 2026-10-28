package io.github.silkypanda.blockvault.core.session;

/** Validation shared by every path that saves or sends a server credential. */
public final class CredentialPassword {
    private CredentialPassword() { }

    /** Empty passwords, whitespace, and control characters cannot be represented safely as command arguments. */
    public static boolean isValid(char[] password) {
        if (password == null || password.length == 0) return false;
        for (char value : password) {
            if (Character.isWhitespace(value) || Character.isSpaceChar(value) || Character.isISOControl(value)) {
                return false;
            }
        }
        return true;
    }
}
