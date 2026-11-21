package com.johnnyblabs.openspec.util;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class PluginVersionsTest {

    /** The runtime target is the value the build wrote from gradle.properties, not a second copy of it. */
    @Test
    void theTargetCliVersionIsTheSingleSourcedValueFromGradleProperties() throws IOException {
        Matcher matcher = Pattern.compile("(?m)^openspecTargetVersion=(.+)$")
                .matcher(Files.readString(Path.of("gradle.properties")));

        assertTrue(matcher.find(), "gradle.properties sets openspecTargetVersion");
        assertEquals(matcher.group(1).strip(), PluginVersions.targetCliVersion());
    }

    /** The runtime plugin version is the build's version, not a second copy of it. */
    @Test
    void thePluginVersionIsTheBuildVersion() throws IOException {
        Matcher matcher = Pattern.compile("(?m)^version = \"([^\"]+)\"")
                .matcher(Files.readString(Path.of("build.gradle.kts")));

        assertTrue(matcher.find(), "build.gradle.kts sets the version");
        assertEquals(matcher.group(1), PluginVersions.pluginVersion());
    }
}
