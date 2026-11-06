package com.johnnyblabs.openspec;

import com.johnnyblabs.openspec.model.OpenSpecConfig;
import com.johnnyblabs.openspec.services.ConfigService;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConfigServiceTest {

    @Test
    void parsesConfigYamlViaFromMap() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("openspec/config.yaml")) {
            assertNotNull(is, "Test config.yaml not found");
            Map<String, Object> raw = new Yaml().load(is);
            OpenSpecConfig config = OpenSpecConfig.fromMap(raw);

            assertEquals("spec-driven", config.getSchema());
            assertEquals("1.2.0", config.getVersion());
            assertNotNull(config.getProfile());
            assertEquals("TestProject", config.getProfile().get("name"));
            assertEquals("Test context item", config.getContext());
            assertEquals(1, config.getRules().size());
            assertEquals("All tests SHALL pass", config.getRules().get("testing"));
        } catch (Exception e) {
            fail("Failed to parse config: " + e.getMessage());
        }
    }

    @Test
    void malformedYamlReturnsDefaultConfig() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("openspec/malformed-config.yaml")) {
            assertNotNull(is, "malformed-config.yaml not found");
            Map<String, Object> raw;
            try {
                raw = new Yaml().load(is);
            } catch (Exception e) {
                // Malformed YAML throws — return default config like ConfigService does
                raw = null;
            }
            OpenSpecConfig config = OpenSpecConfig.fromMap(raw);
            assertNotNull(config);
            assertNull(config.getSchema());
            assertNull(config.getVersion());
            assertTrue(config.getProfile().isEmpty());
            assertEquals("", config.getContext());
            assertTrue(config.getRules().isEmpty());
        } catch (Exception e) {
            fail("Should not throw: " + e.getMessage());
        }
    }

    @Test
    void unknownFieldsIgnored() {
        String yaml = "schema: spec-driven\nversion: \"1.0.0\"\nunknown_field: some_value\nanother_unknown:\n  nested: true\n";
        Map<String, Object> raw = new Yaml().load(yaml);
        OpenSpecConfig config = OpenSpecConfig.fromMap(raw);

        assertEquals("spec-driven", config.getSchema());
        assertEquals("1.0.0", config.getVersion());
    }

    @Test
    void typeMismatchSkipsFieldAndUsesDefault() {
        // rules values are arrays instead of strings — should skip gracefully
        String yaml = "schema: spec-driven\nversion: \"1.2.0\"\nrules:\n  services:\n    - \"Rule one\"\n    - \"Rule two\"\n";
        Map<String, Object> raw = new Yaml().load(yaml);
        OpenSpecConfig config = OpenSpecConfig.fromMap(raw);

        assertEquals("spec-driven", config.getSchema());
        assertEquals("1.2.0", config.getVersion());
        // rules entry with array value is skipped — map is empty
        assertTrue(config.getRules().isEmpty());
    }

    @Test
    void configDefaultsForNullFields() {
        OpenSpecConfig config = new OpenSpecConfig();
        assertNotNull(config.getProfile());
        assertTrue(config.getProfile().isEmpty());
        assertNotNull(config.getContext());
        assertEquals("", config.getContext());
        assertNotNull(config.getRules());
        assertTrue(config.getRules().isEmpty());
    }

    @Test
    void toleratesOperationsAndListShapedRulesFromCli17() throws Exception {
        // A real 1.7 config.yaml carries optional `operations` (apply/archive guidance) and `rules`
        // shaped `artifactId → string[]`. The tolerant Map→fromMap reader must ignore `operations`
        // entirely (no getter, no crash) and skip the list-valued `rules` (it models only
        // String-valued rules), matching the CLI which accepts this config. Captured/proven fixture.
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(
                "fixtures/cli/1.7.0/config-validation/operations-and-rules.config.yaml")) {
            assertNotNull(is, "operations-and-rules fixture not found on the test classpath");
            Map<String, Object> raw = new Yaml().load(is);
            OpenSpecConfig config = OpenSpecConfig.fromMap(raw);

            assertEquals("spec-driven", config.getSchema());
            assertTrue(config.getRules().isEmpty(),
                    "1.7 list-shaped rules (artifactId → string[]) are skipped by the String-valued "
                            + "reader without crashing; got: " + config.getRules());
        }
    }

    // ---- ConfigService.load: the production loader, and what it records when it falls back ----

    private static ConfigService.LoadResult loadText(String yaml) {
        return ConfigService.load(() -> new java.io.ByteArrayInputStream(yaml.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test
    void aValidConfigLoadsWithNoProblem() {
        ConfigService.LoadResult r = loadText("schema: spec-driven\ncontext: hello\n");
        assertNull(r.problem());
        assertEquals("spec-driven", r.config().getSchema());
    }

    @Test
    void malformedYamlIsUnparseableWithTheParsersReason() {
        ConfigService.LoadResult r = loadText("schema: spec-driven\ncontext: [unclosed\n");
        assertNotNull(r.problem());
        assertEquals(ConfigService.ProblemKind.UNPARSEABLE, r.problem().kind());
        assertFalse(r.problem().detail().isBlank());
        assertFalse(r.problem().detail().contains("\n"), "only the first line of the parser's message");
        assertNull(r.config().getSchema(), "defaults are used");
    }

    @Test
    void yamlThatIsNotAMappingIsNotAParseFailure() {
        // Upstream parses these without error, so it reports nothing; neither does the plugin.
        for (String yaml : new String[]{"just a string\n", "- a\n- b\n", "42\n"}) {
            ConfigService.LoadResult r = loadText(yaml);
            assertNull(r.problem(), yaml);
            assertNotNull(r.config(), yaml);
        }
    }

    /**
     * The unreadable branch through the loader seam: the IntelliJ test fixture's in-memory filesystem can't be made
     * unreadable, so the source throws the {@link java.io.IOException} a real read would.
     */
    @Test
    void aSourceThatCannotBeReadIsUnreadable() {
        ConfigService.LoadResult r = ConfigService.load(() -> {
            throw new java.io.IOException("EACCES: permission denied");
        });
        assertNotNull(r.problem());
        assertEquals(ConfigService.ProblemKind.UNREADABLE, r.problem().kind());
        assertEquals("EACCES: permission denied", r.problem().detail());
    }

    /** The same branch with a real unreadable file on disk. */
    @Test
    @org.junit.jupiter.api.condition.DisabledOnOs(org.junit.jupiter.api.condition.OS.WINDOWS)
    void aRealUnreadableFileIsUnreadable(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        java.nio.file.Path file = dir.resolve("config.yaml");
        java.nio.file.Files.writeString(file, "schema: spec-driven\n");
        java.nio.file.Files.setPosixFilePermissions(file, java.util.Set.of());
        try {
            org.junit.jupiter.api.Assumptions.assumeFalse(java.nio.file.Files.isReadable(file),
                    "running as a user who can read a mode-000 file (root)");
            ConfigService.LoadResult r = ConfigService.load(() -> java.nio.file.Files.newInputStream(file));
            assertNotNull(r.problem());
            assertEquals(ConfigService.ProblemKind.UNREADABLE, r.problem().kind());
        } finally {
            java.nio.file.Files.setPosixFilePermissions(file,
                    java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
        }
    }
}
