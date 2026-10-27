package io.github.silkypanda.blockvault.core.session;

import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Validated command template with explicit credential placeholders. */
public record CommandTemplate(String id, String login, String register) {
    private static final Pattern REGISTER_COMMAND = Pattern.compile("(?iu)/([\\p{L}]+)\\b([^\\r\\n]*)");
    private static final Pattern PLACEHOLDER = Pattern.compile("<([^<>]+)>");
    private static final Pattern TOKEN = Pattern.compile("\\{(password|email|username)}");
    public CommandTemplate {
        if (id == null || login == null || register == null) throw new IllegalArgumentException("Template fields are required");
        validate(login);
        validate(register);
    }

    /** Built-in login-plugin command shapes. */
    public static Map<String, CommandTemplate> builtins() {
        return Map.of(
                "authme", new CommandTemplate("authme", "login {password}", "register {password} {password}"),
                "authme-noconfirm", new CommandTemplate("authme-noconfirm", "login {password}", "register {password}"),
                "nlogin", new CommandTemplate("nlogin", "login {password}", "register {password} {password}"),
                "loginsecurity", new CommandTemplate("loginsecurity", "login {password}", "register {password} {password}"));
    }

    /**
     * Selects a registration shape from a system prompt, preserving placeholder order.
     * One placeholder means no confirmation; two means confirmation; an email word or third
     * placeholder requests an email field.
     */
    public static CommandTemplate fromRegistrationPrompt(String plain, Set<String> commandNames) {
        String text = plain == null ? "" : plain.replaceAll("(?i)\\u00a7[0-9A-FK-OR]", "");
        Matcher command = REGISTER_COMMAND.matcher(text);
        while (command.find()) {
            if (!commandNames.contains(command.group(1).toLowerCase(java.util.Locale.ROOT))) continue;
            String remainder = command.group(2);
            Matcher placeholder = PLACEHOLDER.matcher(remainder);
            java.util.List<String> arguments = new java.util.ArrayList<>();
            while (placeholder.find()) arguments.add(placeholder.group(1));
            boolean emailMentioned = remainder.toLowerCase(java.util.Locale.ROOT).contains("email");
            if (arguments.isEmpty()) return builtins().get("authme");
            if (arguments.size() == 1 && !emailMentioned) return builtins().get("authme-noconfirm");

            StringBuilder register = new StringBuilder(command.group(1).toLowerCase(java.util.Locale.ROOT));
            for (int i = 0; i < arguments.size(); i++) {
                register.append(' ').append(argumentPlaceholder(arguments.get(i), i, arguments.size()));
            }
            boolean hasEmail = arguments.stream().anyMatch(value -> value.toLowerCase(java.util.Locale.ROOT).contains("email"));
            if (emailMentioned && !hasEmail) register.append(" {email}");
            return new CommandTemplate("detected", "login {password}", register.toString());
        }
        return builtins().get("authme");
    }

    /** Renders one command while preserving the no-leading-slash contract. */
    public String render(boolean registration, char[] password, String email, String username) {
        String source = registration ? register : login;
        String[] values = {safe(new String(password)), safe(email), safe(username)};
        Matcher tokens = TOKEN.matcher(source);
        StringBuffer resultBuffer = new StringBuffer();
        while (tokens.find()) {
            String value = switch (tokens.group(1)) {
                case "password" -> values[0];
                case "email" -> values[1];
                case "username" -> values[2];
                default -> "";
            };
            tokens.appendReplacement(resultBuffer, Matcher.quoteReplacement(value));
        }
        tokens.appendTail(resultBuffer);
        String result = resultBuffer.toString();
        if (result.startsWith("/") || result.isBlank()) throw new IllegalStateException("Template produced an invalid command");
        return result;
    }

    /** Control characters could split or corrupt the command, so they are refused rather than silently removed. */
    private static String safe(String value) {
        if (value == null) return "";
        for (int i = 0; i < value.length(); i++) {
            if (Character.isISOControl(value.charAt(i))) {
                throw new IllegalArgumentException("Command argument contains control characters");
            }
        }
        return value;
    }

    private static String argumentPlaceholder(String label, int index, int count) {
        String normalized = label.toLowerCase(java.util.Locale.ROOT);
        if (normalized.contains("email")) return "{email}";
        if (normalized.contains("user")) return "{username}";
        if (count >= 3 && index == 2) return "{email}";
        return "{password}";
    }

    private static void validate(String template) {
        if (template.startsWith("/") || template.indexOf('\n') >= 0 || template.indexOf('\r') >= 0
                || template.replace("{password}", "").replace("{email}", "").replace("{username}", "")
                .matches(".*\\{[^}]*}.*")) {
            throw new IllegalArgumentException("Invalid command template");
        }
    }
}
