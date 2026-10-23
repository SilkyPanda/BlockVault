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
}
