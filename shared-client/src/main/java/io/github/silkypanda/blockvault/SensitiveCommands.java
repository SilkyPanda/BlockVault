package io.github.silkypanda.blockvault;

import java.util.Locale;
import java.util.Set;

/** Identifies authentication commands that must never enter vanilla command history. */
public final class SensitiveCommands {
    private static final Set<String> NAMES = Set.of(
            "login", "l", "log", "register", "reg", "changepassword", "changepass", "unregister", "totp", "2fa");

    private SensitiveCommands() { }

    public static boolean isSensitive(String command) {
        if (command == null || !BlockVaultConfig.current().scrubHistory()) return false;
        String normalized = command.strip();
        if (normalized.startsWith("/")) normalized = normalized.substring(1).stripLeading();
        int end = 0;
        while (end < normalized.length() && !Character.isWhitespace(normalized.charAt(end))) end++;
        if (end == 0) return false;
        return NAMES.contains(normalized.substring(0, end).toLowerCase(Locale.ROOT));
    }
}
