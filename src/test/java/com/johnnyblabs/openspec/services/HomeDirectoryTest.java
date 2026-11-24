package com.johnnyblabs.openspec.services;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/** The home directory in upstream's order, {@code USERPROFILE} then {@code HOME} then the JVM's {@code user.home}. */
class HomeDirectoryTest {

    private static Function<String, String> props(String home) {
        return key -> "user.home".equals(key) ? home : null;
    }

    @Test
    void userprofileWins() {
        assertEquals(Path.of("/u/profile"),
                HomeDirectory.resolve(Map.of("USERPROFILE", "/u/profile", "HOME", "/u/home"), props("/u/jvm")));
    }

    @Test
    void thenHome() {
        assertEquals(Path.of("/u/home"), HomeDirectory.resolve(Map.of("HOME", "/u/home"), props("/u/jvm")));
    }

    @Test
    void thenTheJvm() {
        assertEquals(Path.of("/u/jvm"), HomeDirectory.resolve(Map.of(), props("/u/jvm")));
    }

    /** A plugin choice, not upstream's: its {@code ??} would take an empty value as a relative home (see HomeDirectory). */
    @Test
    void blankValuesAreSkipped() {
        assertEquals(Path.of("/u/jvm"), HomeDirectory.resolve(Map.of("USERPROFILE", " ", "HOME", ""), props("/u/jvm")));
        assertNull(HomeDirectory.resolve(Map.of(), props(null)));
    }

    /**
     * Detection, and the machine-wide skills cache that defaults to detection's home, read home through the resolver:
     * a {@code USERPROFILE} no other source has must come back, so a caller reading {@code user.home} itself can't pass.
     */
    @Test
    void detectionUsesTheResolver() {
        try {
            HomeDirectory.setEnvForTest(Map.of("USERPROFILE", "/only/in/userprofile"));
            assertEquals(Path.of("/only/in/userprofile"), HomeDirectory.current());
            assertEquals(Path.of("/only/in/userprofile"), AiToolDetectionService.home());
        } finally {
            HomeDirectory.setEnvForTest(null);
        }
    }
}
