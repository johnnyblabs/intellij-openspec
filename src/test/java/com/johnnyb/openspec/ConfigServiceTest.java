package com.johnnyb.openspec;

import com.johnnyb.openspec.model.OpenSpecConfig;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.error.MarkedYAMLException;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

class ConfigServiceTest {

    @Test
    void parsesConfigYaml() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("openspec/config.yaml")) {
            assertNotNull(is, "Test config.yaml not found");
            Yaml yaml = new Yaml();
            OpenSpecConfig config = yaml.loadAs(is, OpenSpecConfig.class);

            assertEquals("spec-driven", config.getSchema());
            assertEquals("1.2.0", config.getVersion());
            assertNotNull(config.getProfile());
            assertEquals("TestProject", config.getProfile().get("name"));
            assertEquals("Test context item", config.getContext());
            assertEquals(1, config.getRules().size());
        } catch (Exception e) {
            fail("Failed to parse config: " + e.getMessage());
        }
    }

    @Test
    void malformedYamlThrowsMarkedYAMLException() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("openspec/malformed-config.yaml")) {
            assertNotNull(is, "malformed-config.yaml not found");
            Yaml yaml = new Yaml(new Constructor(OpenSpecConfig.class, new LoaderOptions()));
            assertThrows(MarkedYAMLException.class, () -> yaml.loadAs(is, OpenSpecConfig.class));
        } catch (MarkedYAMLException e) {
            // Expected — verify we can extract location info
            assertNotNull(e.getProblem());
        } catch (Exception e) {
            fail("Unexpected exception type: " + e.getClass().getName() + ": " + e.getMessage());
        }
    }

    @Test
    void malformedYamlDoesNotCrashConfigParsing() {
        // Simulates what ConfigService.reload() does with malformed input
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("openspec/malformed-config.yaml")) {
            assertNotNull(is, "malformed-config.yaml not found");
            Yaml yaml = new Yaml(new Constructor(OpenSpecConfig.class, new LoaderOptions()));
            OpenSpecConfig config = null;
            try {
                config = yaml.loadAs(is, OpenSpecConfig.class);
            } catch (MarkedYAMLException e) {
                // This is the expected path — config stays null
                assertNotNull(e.getProblemMark(), "MarkedYAMLException should have a problem mark");
                assertTrue(e.getProblemMark().getLine() >= 0, "Line number should be non-negative");
            }
            assertNull(config, "Config should be null after malformed YAML");
        } catch (Exception e) {
            fail("Should not throw: " + e.getMessage());
        }
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
