package io.github.silkypanda.blockvault.core.vault;

import java.util.Locale;

/** Case-insensitive account identity shared by the vault, merge and trash operations. */
public final class Usernames {
    private static final java.util.regex.Pattern ALLOWED = java.util.regex.Pattern.compile("[A-Za-z0-9_]{1,64}");
    private Usernames() { }

    public static boolean isValid(String username) {
        return username != null && ALLOWED.matcher(username).matches();
    }

    public static String key(String username) {
        return username.toLowerCase(Locale.ROOT);
    }

    public static boolean same(String left, String right) {
        return left != null && right != null && key(left).equals(key(right));
    }
}
