package io.github.silkypanda.blockvault.core.session;

import java.util.Map;

/** Validated command template with explicit credential placeholders. */
public record CommandTemplate(String id, String login, String register) {
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

    /** Renders one command while preserving the no-leading-slash contract. */
    public String render(boolean registration, char[] password, String email, String username, String totp) {
        String source = registration ? register : login;
        String result = source.replace("{password}", new String(password))
                .replace("{email}", safe(email)).replace("{username}", safe(username)).replace("{totp}", safe(totp));
        if (result.startsWith("/") || result.isBlank()) throw new IllegalStateException("Template produced an invalid command");
        return result;
    }

    private static String safe(String value) { return value == null ? "" : value.replaceAll("[\\r\\n]", ""); }

    private static void validate(String template) {
        if (template.startsWith("/") || template.indexOf('\n') >= 0 || template.indexOf('\r') >= 0
                || template.replace("{password}", "").replace("{email}", "").replace("{username}", "")
                .replace("{totp}", "").matches(".*\\{[^}]*}.*")) {
            throw new IllegalArgumentException("Invalid command template");
        }
    }
}
