package io.github.silkypanda.blockvault;

import java.util.Locale;

/** Identifies authentication commands that must never enter vanilla command history. */
public final class SensitiveCommands {
    private SensitiveCommands() { }

    public static boolean isSensitive(String command) {
        return isSensitive(command, false);
    }

    /** Recent-chat history contains ordinary prose too, so only slash-prefixed lines can be commands there. */
    public static boolean isSensitiveChatLine(String command) {
        return command != null && command.stripLeading().startsWith("/") && isSensitive(command, true);
    }

    private static boolean isSensitive(String command, boolean slashRequired) {
        if (command == null || !BlockVaultConfig.current().scrubHistory()) return false;
        String normalized = command.strip();
        if (slashRequired && !normalized.startsWith("/")) return false;
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
