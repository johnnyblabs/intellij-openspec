package com.johnnyblabs.openspec;

import com.johnnyblabs.openspec.model.OpenSpecConfig;
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
}
