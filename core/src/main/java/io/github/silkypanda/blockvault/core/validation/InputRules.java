package io.github.silkypanda.blockvault.core.validation;

import java.util.regex.Pattern;

/**
 * What BlockVault accepts from text fields.
 *
 * <p>Passwords and other command arguments are sent to a server as one chat command, so they must survive
 * Minecraft's chat rules and the server's whitespace-separated argument parsing. They therefore allow letters
 * (any language), marks, digits, punctuation and symbols, and refuse everything else: whitespace of any kind,
 * control characters, invisible or formatting characters, private-use or unassigned code points, unpaired
 * surrogates and the section sign that Minecraft uses for formatting codes.
 */
public final class InputRules {
    /** Longest server password; leaves room for a register command that repeats it inside the chat limit. */
    public static final int SERVER_PASSWORD_MAX = 100;
    /** Longest master password. It never leaves the machine, so it is limited only by the field size. */
    public static final int MASTER_PASSWORD_MAX = 256;
    /** Minecraft refuses chat and command text longer than this. */
    public static final int COMMAND_MAX = 256;
    /** Minimum master password length, counted in characters (code points). */
    public static final int MASTER_PASSWORD_MIN = 8;

    private static final Pattern EMAIL = Pattern.compile(
            "[\\p{L}\\p{M}\\p{N}.!#$%&'*+/=?^_`{|}~-]+@[\\p{L}\\p{N}](?:[\\p{L}\\p{M}\\p{N}-]{0,61}[\\p{L}\\p{M}\\p{N}])?"
                    + "(?:\\.[\\p{L}\\p{N}](?:[\\p{L}\\p{M}\\p{N}-]{0,61}[\\p{L}\\p{M}\\p{N}])?)+");

    private InputRules() { }

    // ---- single characters -------------------------------------------------------------------------------

    /** A visible character that can appear inside one command argument. */
    public static boolean isPasswordChar(int codePoint) {
        if (codePoint == 0xA7 || isInvisibleOrFormatting(codePoint)) return false;
        return switch (Character.getType(codePoint)) {
            case Character.UPPERCASE_LETTER, Character.LOWERCASE_LETTER, Character.TITLECASE_LETTER,
                 Character.MODIFIER_LETTER, Character.OTHER_LETTER,
                 Character.NON_SPACING_MARK, Character.ENCLOSING_MARK, Character.COMBINING_SPACING_MARK,
                 Character.DECIMAL_DIGIT_NUMBER, Character.LETTER_NUMBER, Character.OTHER_NUMBER,
                 Character.CONNECTOR_PUNCTUATION, Character.DASH_PUNCTUATION, Character.START_PUNCTUATION,
                 Character.END_PUNCTUATION, Character.INITIAL_QUOTE_PUNCTUATION, Character.FINAL_QUOTE_PUNCTUATION,
                 Character.OTHER_PUNCTUATION, Character.MATH_SYMBOL, Character.CURRENCY_SYMBOL,
                 Character.MODIFIER_SYMBOL, Character.OTHER_SYMBOL -> true;
            default -> false;
        };
    }

    private static boolean isInvisibleOrFormatting(int codePoint) {
        return codePoint == 0x034F || codePoint == 0x00AD || codePoint == 0x061C || codePoint == 0x115F
                || codePoint == 0x1160 || codePoint == 0x17B4 || codePoint == 0x17B5
                || codePoint >= 0x180B && codePoint <= 0x180F
                || codePoint >= 0x200B && codePoint <= 0x200F
                || codePoint >= 0x202A && codePoint <= 0x202E
                || codePoint >= 0x2060 && codePoint <= 0x206F
                || codePoint == 0x3164 || codePoint >= 0xFE00 && codePoint <= 0xFE0F
                || codePoint == 0xFEFF || codePoint == 0xFFA0
                || codePoint >= 0xFFF0 && codePoint <= 0xFFF8
                || codePoint == 0x2800 || codePoint >= 0x1BCA0 && codePoint <= 0x1BCA3
                || codePoint >= 0x1D173 && codePoint <= 0x1D17A
                || codePoint >= 0xE0000 && codePoint <= 0xE0FFF;
    }

    // ---- passwords ---------------------------------------------------------------------------------------

    /** True when every character of {@code value} may appear in a password (no length check). */
    public static boolean isPasswordText(String value) {
        if (value == null) return false;
        for (int i = 0; i < value.length(); ) {
            int codePoint = value.codePointAt(i);
            if (!isPasswordChar(codePoint)) return false;
            i += Character.charCount(codePoint);
        }
        return true;
    }

    /** Allowed characters and maximum code-point count for a server password edit box; empty is allowed while typing. */
    public static boolean isServerPasswordInput(String value) {
        return value != null && characterCount(value) <= SERVER_PASSWORD_MAX && isPasswordText(value);
    }

    /** Allowed characters and maximum code-point count for a master password edit box; empty is allowed while typing. */
    public static boolean isMasterPasswordInput(String value) {
        return value != null && characterCount(value) <= MASTER_PASSWORD_MAX && isPasswordText(value);
    }

    /** A password for a server login: 1 to {@link #SERVER_PASSWORD_MAX} acceptable characters. */
    public static boolean isPassword(char[] value) {
        return validPassword(value, SERVER_PASSWORD_MAX);
    }

    /** A master password: {@link #MASTER_PASSWORD_MIN} to {@link #MASTER_PASSWORD_MAX} acceptable code points. */
    public static boolean isMasterPassword(char[] value) {
        return validPassword(value, MASTER_PASSWORD_MAX)
                && characterCount(value) >= MASTER_PASSWORD_MIN;
    }

    /** A password typed into a command: acceptable characters and within the server password length. */
    public static boolean isPasswordArgument(String value) {
        return value != null && !value.isEmpty()
                && value.codePointCount(0, value.length()) <= SERVER_PASSWORD_MAX && isPasswordText(value);
    }

    /** Number of characters (code points) in a password, so one emoji or accented letter counts once. */
    public static int characterCount(char[] value) {
        return value == null ? 0 : Character.codePointCount(value, 0, value.length);
    }

    private static int characterCount(String value) {
        return Character.codePointCount(value, 0, value.length());
    }

    private static boolean validPassword(char[] value, int maxLength) {
        if (value == null || value.length == 0 || Character.codePointCount(value, 0, value.length) > maxLength) return false;
        for (int i = 0; i < value.length; ) {
            int codePoint = Character.codePointAt(value, i);
            if (!isPasswordChar(codePoint)) return false;
            i += Character.charCount(codePoint);
        }
        return true;
    }

    // ---- other fields ------------------------------------------------------------------------------------

    /** Display, search and path text: acceptable characters plus ordinary spaces. */
    public static boolean isText(String value) {
        if (value == null) return false;
        for (int i = 0; i < value.length(); ) {
            int codePoint = value.codePointAt(i);
            if (codePoint != ' ' && !isPasswordChar(codePoint)) return false;
            i += Character.charCount(codePoint);
        }
        return true;
    }

    /** A server address as typed: letters and digits of any language plus {@code . - _ : [ ]}. */
    public static boolean isHostText(String value) {
        if (value == null) return false;
        for (int i = 0; i < value.length(); ) {
            int codePoint = value.codePointAt(i);
            boolean allowed = Character.isLetterOrDigit(codePoint)
                    || codePoint == '.' || codePoint == '-' || codePoint == '_'
                    || codePoint == ':' || codePoint == '[' || codePoint == ']'
                    || codePoint == 0x3002 || codePoint == 0xFF0E || codePoint == 0xFF61 // dots used in IDN input
                    || isPasswordChar(codePoint) && Character.getType(codePoint) == Character.NON_SPACING_MARK;
            if (!allowed) return false;
            i += Character.charCount(codePoint);
        }
        return true;
    }

    /** Minecraft account names: letters, digits and underscores only. */
    public static boolean isUsernameText(String value) {
        return value != null && value.length() <= 64 && value.matches("[A-Za-z0-9_]*");
    }

    public static boolean isEmailText(String value) {
        if (value == null || value.length() > 254) return false;
        for (int i = 0; i < value.length(); ) {
            int codePoint = value.codePointAt(i);
            boolean allowed = isPasswordChar(codePoint) && "\"(),:;<>[\\]".indexOf(codePoint) < 0;
            if (!allowed) return false;
            i += Character.charCount(codePoint);
        }
        return true;
    }

    public static boolean isEmail(String value) {
        return value != null && value.length() <= 254 && isEmailText(value) && EMAIL.matcher(value).matches();
    }
}
