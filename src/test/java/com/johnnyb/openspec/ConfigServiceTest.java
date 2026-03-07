package com.johnnyb.openspec;

import com.johnnyb.openspec.model.OpenSpecConfig;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

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
