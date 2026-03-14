package com.johnnyblabs.openspec.integration;

import com.johnnyblabs.openspec.model.OpenSpecConfig;
import com.johnnyblabs.openspec.services.ConfigService;

/**
 * Integration tests for ConfigService with a real IntelliJ project.
 * Verifies config loading from the virtual filesystem.
 */
public class ConfigServiceIntegrationTest extends OpenSpecIntegrationTestBase {

    public void testConfigServiceLoadsFromProject() {
        ConfigService configService = getProject().getService(ConfigService.class);
        assertNotNull("ConfigService should be available", configService);

        configService.reload();
        assertTrue("Config should be loaded", configService.isConfigLoaded());

        OpenSpecConfig config = configService.getConfig();
        assertNotNull("Config should not be null", config);
        assertEquals("spec-driven", config.getSchema());
        assertEquals("1.2.0", config.getVersion());
    }

    public void testConfigServiceReturnsProfile() {
        ConfigService configService = getProject().getService(ConfigService.class);
        configService.reload();
        OpenSpecConfig config = configService.getConfig();

        assertNotNull(config.getProfile());
        assertEquals("TestProject", config.getProfile().get("name"));
        assertEquals("A test project for integration testing", config.getProfile().get("description"));
    }

    public void testConfigServiceReturnsRules() {
        ConfigService configService = getProject().getService(ConfigService.class);
        configService.reload();
        OpenSpecConfig config = configService.getConfig();

        assertNotNull(config.getRules());
        assertEquals(1, config.getRules().size());
        assertEquals("All tests SHALL pass", config.getRules().get("testing"));
    }

    public void testConfigServiceReloadsAfterChange() {
        ConfigService configService = getProject().getService(ConfigService.class);
        configService.reload();
        assertNotNull(configService.getConfig());

        // Reload again should not fail
        configService.reload();
        assertTrue(configService.isConfigLoaded());
    }
}
