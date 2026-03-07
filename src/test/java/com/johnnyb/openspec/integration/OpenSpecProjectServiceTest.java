package com.johnnyb.openspec.integration;

import com.johnnyb.openspec.services.ChangeService;
import com.johnnyb.openspec.services.ConfigService;
import com.johnnyb.openspec.services.OpenSpecProjectService;
import com.johnnyb.openspec.services.SpecParsingService;

/**
 * Integration tests for OpenSpecProjectService.
 * Verifies service wiring and project detection.
 */
public class OpenSpecProjectServiceTest extends OpenSpecIntegrationTestBase {

    public void testServiceIsAvailable() {
        OpenSpecProjectService service = getProject().getService(OpenSpecProjectService.class);
        assertNotNull("OpenSpecProjectService should be registered", service);
    }

    public void testProvidesConfigService() {
        OpenSpecProjectService service = getProject().getService(OpenSpecProjectService.class);
        ConfigService configService = service.getConfigService();
        assertNotNull("Should provide ConfigService", configService);
    }

    public void testProvidesChangeService() {
        OpenSpecProjectService service = getProject().getService(OpenSpecProjectService.class);
        ChangeService changeService = service.getChangeService();
        assertNotNull("Should provide ChangeService", changeService);
    }

    public void testProvidesSpecParsingService() {
        OpenSpecProjectService service = getProject().getService(OpenSpecProjectService.class);
        SpecParsingService specParsingService = service.getSpecParsingService();
        assertNotNull("Should provide SpecParsingService", specParsingService);
    }

    public void testDetectsOpenSpecProject() {
        OpenSpecProjectService service = getProject().getService(OpenSpecProjectService.class);
        assertTrue("Should detect openspec directory", service.isOpenSpecProject());
    }
}
