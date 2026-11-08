package io.github.silkypanda.blockvault.core.session;

import java.util.Locale;
import java.util.Set;

/** Pure command-name decisions shared by the Minecraft hooks and unit tests. */
public final class CommandPolicy {
    private CommandPolicy() { }

    /** Returns the name of a command containing only an optional slash, namespace, and command token. */
    public static String bareCommandName(String command) {
        if (command == null) return null;
        String normalized = command.strip();
        if (!normalized.matches("(?i)/?(?:[a-z0-9_]+:)?[a-z0-9_]+\\s*")) return null;
        String name = normalized.replaceFirst("^/", "").strip().toLowerCase(Locale.ROOT);
        int namespace = name.lastIndexOf(':');
        return namespace >= 0 ? name.substring(namespace + 1) : name;
    }

    /** Short aliases are sensitive only after a matching prompt established their meaning. */
    public static boolean isSensitiveAlias(String normalizedAlias, Set<String> aliases, boolean shortAliasPrompted) {
        return aliases.contains(normalizedAlias) && (normalizedAlias.length() > 3 || shortAliasPrompted);
    }

    /** A bare login command is held only when the saved-login flow recognizes that exact command name. */
    public static boolean shouldHoldBareLogin(String command, Set<String> loginNames,
                                               boolean sessionAllowsInterception,
                                               boolean shortAliasPrompted) {
        String name = bareCommandName(command);
        return name != null && loginNames.contains(name) && sessionAllowsInterception
                && (name.equals("login") || shortAliasPrompted);
    }
}
