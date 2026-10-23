package io.github.silkypanda.blockvault.core.host;

import java.util.Locale;
import java.util.Optional;

/** Canonical server address normalization that performs no DNS or network access. */
public final class HostKey {
    private HostKey() { }

    /** Normalizes hostnames and IPv6 literals, returning empty for malformed input. */
    public static Optional<String> normalize(String address) {
        if (address == null || address.isBlank()) return Optional.empty();
        String value = address.trim();
        String host;
        String portText = null;
        if (value.startsWith("[")) {
            int close = value.indexOf(']');
            if (close < 0) return Optional.empty();
            host = value.substring(1, close);
            String rest = value.substring(close + 1);
            if (!rest.isEmpty()) {
                if (!rest.startsWith(":")) return Optional.empty();
                portText = rest.substring(1);
            }
            if (host.indexOf(':') < 0 || host.contains("[") || host.contains("]")) return Optional.empty();
            host = "[" + host.toLowerCase(Locale.ROOT) + "]";
        } else {
            int firstColon = value.indexOf(':');
            int lastColon = value.lastIndexOf(':');
            if (firstColon != lastColon) return Optional.empty();
            if (lastColon >= 0) {
                host = value.substring(0, lastColon);
                portText = value.substring(lastColon + 1);
            } else host = value;
            host = host.toLowerCase(Locale.ROOT);
            if (host.endsWith(".")) host = host.substring(0, host.length() - 1);
            if (host.isEmpty() || !host.matches("[a-z0-9._-]+") || host.startsWith(".") || host.endsWith(".")) return Optional.empty();
        }
        int port = 25565;
        if (portText != null) {
            if (portText.isEmpty() || !portText.matches("[0-9]{1,5}")) return Optional.empty();
            try { port = Integer.parseInt(portText); }
            catch (NumberFormatException e) { return Optional.empty(); }
            if (port < 1 || port > 65535) return Optional.empty();
        }
        return Optional.of(host + ":" + port);
    }
}
