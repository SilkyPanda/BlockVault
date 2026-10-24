package io.github.silkypanda.blockvault;

import java.util.Locale;
import java.util.Set;

/** Identifies authentication commands that must never enter vanilla command history. */
public final class SensitiveCommands {
    private static final Set<String> NAMES = Set.of(
            "login", "register", "reg", "changepassword", "changepass", "unregister", "totp", "2fa");

    private SensitiveCommands() { }

    public static boolean isSensitive(String command) {
        if (command == null || !BlockVaultConfig.current().scrubHistory()) return false;
        String normalized = command.strip();
        if (normalized.startsWith("/")) normalized = normalized.substring(1).stripLeading();
        int end = 0;
        while (end < normalized.length() && !Character.isWhitespace(normalized.charAt(end))) end++;
        if (end == 0) return false;
        String name = normalized.substring(0, end).toLowerCase(Locale.ROOT);
        int namespace = name.lastIndexOf(':');
        if (namespace >= 0) name = name.substring(namespace + 1);
        return NAMES.contains(name) || Hooks.isSensitiveAlias(name);
    }
}
