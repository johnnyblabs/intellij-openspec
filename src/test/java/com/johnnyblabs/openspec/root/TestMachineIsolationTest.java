package com.johnnyblabs.openspec.root;

import com.johnnyblabs.openspec.coordination.CoordinationPaths;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The test JVM's OpenSpec machine files (store registry, global config) are empty build folders, set by the Gradle
 * test task, so a developer's own stores or default store never change what a test resolves.
 */
class TestMachineIsolationTest {

    @Test
    void theMachineWideOpenSpecFilesAreUnderTheBuildFolder() {
        Path build = Path.of("build").toAbsolutePath().normalize();
        RootResolver.Inputs host = RootResolver.Inputs.host("2.1.1");
        assertTrue(host.globalConfigFile().toAbsolutePath().normalize().startsWith(build), host.globalConfigFile()::toString);
        Path registry = CoordinationPaths.resolve().storeRegistryFile();
        assertTrue(registry.toAbsolutePath().normalize().startsWith(build), registry::toString);
    }
}
