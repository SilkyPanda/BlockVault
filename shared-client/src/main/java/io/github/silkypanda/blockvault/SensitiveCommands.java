package io.github.silkypanda.blockvault;

import java.util.Locale;

/** Identifies authentication commands that must never enter vanilla command history. */
public final class SensitiveCommands {
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
        return Hooks.isSensitiveAlias(name);
    }
}
