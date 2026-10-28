package io.github.silkypanda.blockvault.core.vault;

import java.util.Locale;

/** Case-insensitive account identity shared by the vault, merge and trash operations. */
public final class Usernames {
    private Usernames() { }

    public static String key(String username) {
        return username.toLowerCase(Locale.ROOT);
    }

    public static boolean same(String left, String right) {
        return left != null && right != null && key(left).equals(key(right));
    }
}
