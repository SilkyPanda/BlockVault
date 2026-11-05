package io.github.silkypanda.blockvault.core.host;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class HostKeyTest {
    @Test
    void normalizesHostnamesAndIpv6() {
        assertEquals("play.example.net:25565", HostKey.normalize("Play.Example.NET").orElseThrow());
        assertEquals("play.example.net:25570", HostKey.normalize("play.example.net:25570").orElseThrow());
        assertEquals("[::1]:25565", HostKey.normalize("[::1]:25565").orElseThrow());
        assertTrue(HostKey.normalize("bad host").isEmpty());
    }

    @org.junit.jupiter.api.Test
    void rejectsMalformedHostnames() {
        for (String bad : new String[] {"a..b", "-a.com", "a-.com", ".a.com", "a b.com", "x".repeat(64) + ".com"}) {
            org.junit.jupiter.api.Assertions.assertTrue(HostKey.normalize(bad).isEmpty(), bad);
        }
        org.junit.jupiter.api.Assertions.assertEquals("play_1.example.com:25565",
                HostKey.normalize("Play_1.Example.com").orElseThrow());
    }

    @Test
    void convertsInternationalizedHostnamesToTheirAsciiForm() {
        assertEquals(java.util.Optional.of("xn--bcher-kva.de:25565"), HostKey.normalize("b\u00fccher.de"));
        assertEquals(java.util.Optional.of("xn--bcher-kva.de:25566"), HostKey.normalize("B\u00dcCHER.de:25566"));
        assertEquals(HostKey.normalize("xn--bcher-kva.de"), HostKey.normalize("b\u00fccher.de"));
    }

    @Test
    void stillRejectsNonAsciiInIpv6LiteralsAndBadInternationalNames() {
        assertTrue(HostKey.normalize("[\u0436::1]:25565").isEmpty());
        assertTrue(HostKey.normalize("play \u00fc.de").isEmpty());
        assertTrue(HostKey.normalize("b\u00fccher..de").isEmpty());
    }
}
