package com.johnnyblabs.openspec.services;

import com.johnnyblabs.openspec.model.OpenSpecConfig;
import com.johnnyblabs.openspec.model.OpenSpecConfig.SkillsScope;
import com.johnnyblabs.openspec.util.FixtureVersions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@code skillsScope:} as the real CLI writes and reads it, loaded through {@link ConfigService#load}: the config
 * upstream's own {@code rememberProjectSkills} writes records the project scope, and an invalid value (which 2.1
 * warns about while applying the rest of the config) records nothing and still loads cleanly.
 */
class SkillsScopeConfigContractTest {

    static final List<String> RELEASES = List.of("2.1.0", "2.1.1");

    private static ConfigService.LoadResult load(String text) {
        return ConfigService.load(() -> new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
    }

    private static String captured(String version, String name) throws IOException {
        return Files.readString(FixtureVersions.CLI.resolve(version).resolve("skills-scope").resolve(name));
    }

    @TestFactory
    Stream<DynamicTest> theRememberedConfigRecordsTheProjectScope() {
        return RELEASES.stream().map(v -> DynamicTest.dynamicTest(v, () -> {
            ConfigService.LoadResult r = load(captured(v, "remembered-config.yaml"));
            assertNull(r.problem());
            assertEquals(SkillsScope.PROJECT, r.config().getSkillsScope());
            assertEquals("spec-driven", r.config().getSchema());
        }));
    }

    @TestFactory
    Stream<DynamicTest> anInvalidValueRecordsNothingAndTheRestStillLoads() {
        return RELEASES.stream().map(v -> DynamicTest.dynamicTest(v, () -> {
            assertEquals("0", captured(v, "team-warning.exit").strip(), "2.1 only warns");
            ConfigService.LoadResult r = load(captured(v, "config-team.yaml"));
            assertNull(r.problem(), "not a load failure: 2.1 keeps the config");
            assertNull(r.config().getSkillsScope());
            assertEquals("spec-driven", r.config().getSchema());
            assertEquals(captured(v, "team-context.txt").strip(), r.config().getContext().strip(),
                    "the context 2.1 still applied is read too");
        }));
    }

    @Test
    void noKeyIsNoRecordedChoiceAndGlobalIsRead() {
        assertNull(load("schema: spec-driven\n").config().getSkillsScope());
        assertEquals(SkillsScope.GLOBAL, load("schema: spec-driven\nskillsScope: global\n").config().getSkillsScope());
        assertNull(load("skillsScope: Project\n").config().getSkillsScope(), "2.1 compares exactly");
        assertNull(OpenSpecConfig.fromMap(null).getSkillsScope());
    }

    /** The plugin reads the key and never writes it: no setter, and no main source names it in a write. */
    @Test
    void nothingInThePluginWritesSkillsScope() throws IOException {
        assertTrue(java.util.Arrays.stream(OpenSpecConfig.class.getMethods())
                .noneMatch(m -> m.getName().equals("setSkillsScope")));
        List<Path> mentions;
        try (Stream<Path> files = Files.walk(Path.of("src/main"))) {
            mentions = files.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java") || p.toString().endsWith(".kt"))
                    .filter(p -> {
                        try {
                            return Files.readString(p).contains("skillsScope");
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }).toList();
        }
        // Positive control: the reader itself must be found, so an empty scan can't pass.
        assertTrue(mentions.contains(Path.of("src/main/java/com/johnnyblabs/openspec/model/OpenSpecConfig.java")), mentions.toString());
        // Writing the key means emitting `skillsScope:` from a string literal; comments may name it.
        java.util.regex.Pattern written = java.util.regex.Pattern.compile("\"[^\"\\n]*skillsScope\\s*:");
        assertTrue(written.matcher("String s = \"skillsScope: project\\n\";").find(), "positive control for the pattern");
        for (Path p : mentions) {
            assertFalse(written.matcher(Files.readString(p)).find(), p + " writes or templates skillsScope");
        }
        String builtIn = Files.readString(Path.of("src/main/java/com/johnnyblabs/openspec/scaffolding/ScaffoldingService.java"));
        assertFalse(builtIn.contains("skillsScope"), "the built-in init's config holds only schema:");
    }
}
