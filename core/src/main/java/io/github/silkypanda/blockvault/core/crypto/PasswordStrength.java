package io.github.silkypanda.blockvault.core.crypto;

/** Coarse local feedback for master-password entry; this is guidance, not an entropy guarantee. */
public final class PasswordStrength {
    private PasswordStrength() { }

    /** Returns a stable rating from 0 (short/weak) to 3 (stronger). */
    public static int rating(char[] password) {
        if (password == null || password.length < 8) return 0;
        boolean lower = false, upper = false, digit = false, symbol = false;
        boolean varied = false;
        char first = password[0];
        for (char value : password) {
            lower |= value >= 'a' && value <= 'z';
            upper |= value >= 'A' && value <= 'Z';
            digit |= value >= '0' && value <= '9';
            symbol |= !Character.isLetterOrDigit(value);
            varied |= value != first;
        }
        int classes = (lower ? 1 : 0) + (upper ? 1 : 0) + (digit ? 1 : 0) + (symbol ? 1 : 0);
        if (password.length >= 20 && varied || password.length >= 16 && classes >= 3) return 3;
        if (password.length >= 12 && varied && classes >= 2) return 2;
        if (password.length >= 8 && varied) return 1;
        return 0;
    }
}
