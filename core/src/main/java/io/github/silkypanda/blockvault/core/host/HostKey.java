package io.github.silkypanda.blockvault.core.host;

import java.util.Locale;
import java.util.Optional;
import java.text.Normalizer;

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
            host = Normalizer.normalize(host.toLowerCase(Locale.ROOT), Normalizer.Form.NFC);
            if (host.endsWith(".")) host = host.substring(0, host.length() - 1);
            host = toAsciiHostname(host);
            if (host == null) return Optional.empty();
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

    /** Converts Unicode labels to ASCII using the local RFC 3492 Punycode algorithm (no network APIs). */
    private static String toAsciiHostname(String host) {
        String[] labels = host.split("\\.", -1);
        for (int i = 0; i < labels.length; i++) {
            String label = labels[i];
            boolean nonAscii = label.codePoints().anyMatch(cp -> cp > 0x7f);
            if (!nonAscii) continue;
            for (int cp : label.codePoints().toArray()) {
                int type = Character.getType(cp);
                if (Character.isWhitespace(cp) || Character.isISOControl(cp)
                        || type == Character.FORMAT || type == Character.PRIVATE_USE
                        || type == Character.SURROGATE || type == Character.UNASSIGNED) return null;
            }
            String encoded = punycode(label);
            if (encoded == null) return null;
            labels[i] = "xn--" + encoded;
        }
        return String.join(".", labels);
    }

    /** RFC 3492 encoder for one already-normalized label. */
    private static String punycode(String label) {
        int[] input = label.codePoints().toArray();
        StringBuilder output = new StringBuilder();
        int basic = 0;
        for (int cp : input) {
            if (cp < 0x80) {
                output.append((char) cp);
                basic++;
            }
        }
        int handled = basic;
        if (basic > 0 && handled < input.length) output.append('-');
        long n = 128;
        long delta = 0;
        int bias = 72;
        while (handled < input.length) {
            long minimum = Long.MAX_VALUE;
            for (int cp : input) if (cp >= n && cp < minimum) minimum = cp;
            if (minimum == Long.MAX_VALUE || minimum - n > (Long.MAX_VALUE - delta) / (handled + 1L)) return null;
            delta += (minimum - n) * (handled + 1L);
            n = minimum;
            for (int cp : input) {
                if (cp < n && ++delta < 0) return null;
                if (cp != n) continue;
                long q = delta;
                for (int k = 36; ; k += 36) {
                    int threshold = k <= bias ? 1 : (k >= bias + 26 ? 26 : k - bias);
                    if (q < threshold) break;
                    int digit = threshold + (int) ((q - threshold) % (36 - threshold));
                    output.append(punycodeDigit(digit));
                    q = (q - threshold) / (36 - threshold);
                }
                output.append(punycodeDigit((int) q));
                bias = adaptBias(delta, handled + 1, handled == basic);
                delta = 0;
                handled++;
            }
            delta++;
            n++;
        }
        return output.toString();
    }

    private static int adaptBias(long delta, int points, boolean first) {
        delta = first ? delta / 700 : delta / 2;
        delta += delta / points;
        int k = 0;
        while (delta > 455) {
            delta /= 35;
            k += 36;
        }
        return k + (int) (36 * delta / (delta + 38));
    }

    private static char punycodeDigit(int digit) {
        return (char) (digit < 26 ? 'a' + digit : '0' + digit - 26);
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
