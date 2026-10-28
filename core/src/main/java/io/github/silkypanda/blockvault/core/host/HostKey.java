package io.github.silkypanda.blockvault.core.host;

import java.util.Locale;
import java.util.Optional;
import java.net.IDN;

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
            try { host = IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT); }
            catch (IllegalArgumentException invalidIdn) { return Optional.empty(); }
            if (!validHostname(host) || !validIpv4(host)) return Optional.empty();
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

    /** Letters, digits, hyphen and underscore in dot-separated labels of 1-63 characters, 253 in total. */
    private static boolean validHostname(String host) {
        if (host.isEmpty() || host.length() > 253) return false;
        for (String label : host.split("\\.", -1)) {
            if (label.isEmpty() || label.length() > 63 || label.startsWith("-") || label.endsWith("-")) return false;
            for (int i = 0; i < label.length(); i++) {
                char c = label.charAt(i);
                if (!((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-' || c == '_')) return false;
            }
        }
        return true;
    }

    /** Rejects dotted numeric addresses that are not valid IPv4 literals. */
    private static boolean validIpv4(String host) {
        if (!host.matches("[0-9.]+")) return true;
        String[] parts = host.split("\\.", -1);
        if (parts.length != 4) return false;
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 3) return false;
            try { if (Integer.parseInt(part) > 255) return false; }
            catch (NumberFormatException invalid) { return false; }
        }
        return true;
    }
}
