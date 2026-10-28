package io.github.silkypanda.blockvault.core.validation;

import java.util.regex.Pattern;

/** Explicit ASCII allowlists for values BlockVault accepts from text fields. */
public final class AsciiInput {
    private static final Pattern EMAIL = Pattern.compile(
            "[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+");

    private AsciiInput() { }

    /** Printable ASCII including spaces, for local folder paths and search/name text. */
    public static boolean isPrintable(String value) {
        if (value == null) return false;
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character < ' ' || character > '~') return false;
        }
        return true;
    }

    /** Printable, non-space ASCII used for passwords transmitted as a single command argument. */
    public static boolean isPassword(char[] value) {
        if (value == null || value.length == 0) return false;
        for (char character : value) if (character < '!' || character > '~') return false;
        return true;
    }

    public static boolean isPasswordText(String value) {
        if (value == null) return false;
        for (int i = 0; i < value.length(); i++) if (value.charAt(i) < '!' || value.charAt(i) > '~') return false;
        return true;
    }

    public static boolean isUsernameText(String value) {
        return value != null && value.length() <= 64 && value.matches("[A-Za-z0-9_]*");
    }

    public static boolean isEmailText(String value) {
        return value != null && value.length() <= 254 && value.matches("[A-Za-z0-9.!#$%&'*+/=?^_`{|}~@-]*");
    }

    public static boolean isEmail(String value) {
        return value != null && value.length() <= 254 && EMAIL.matcher(value).matches();
    }
}
