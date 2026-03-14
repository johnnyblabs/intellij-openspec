package com.johnnyblabs.openspec.integration;

import com.intellij.openapi.vfs.VirtualFile;
import com.johnnyblabs.openspec.model.SpecFile;
import com.johnnyblabs.openspec.services.SpecParsingService;

import java.nio.charset.StandardCharsets;

/**
 * Integration tests for SpecParsingService with project-level file access.
 * Verifies spec parsing from VirtualFile content in a real project context.
 */
public class SpecParsingIntegrationTest extends OpenSpecIntegrationTestBase {

    public void testParsesSpecFromProjectFiles() throws Exception {
        SpecParsingService service = getProject().getService(SpecParsingService.class);
        assertNotNull("SpecParsingService should be available", service);

        VirtualFile specFile = myFixture.findFileInTempDir("openspec/specs/actions/spec.md");
        assertNotNull("actions/spec.md should exist", specFile);

        String content = new String(specFile.contentsToByteArray(), StandardCharsets.UTF_8);
        SpecFile spec = service.parseSpecContent(content, "actions", specFile.getPath());

        assertEquals("Actions", spec.getTitle());
        assertEquals("actions", spec.getDomain());
        assertEquals(2, spec.getRequirements().size());
    }

    public void testParsesRequirementKeywords() throws Exception {
        SpecParsingService service = getProject().getService(SpecParsingService.class);
        VirtualFile specFile = myFixture.findFileInTempDir("openspec/specs/actions/spec.md");
        String content = new String(specFile.contentsToByteArray(), StandardCharsets.UTF_8);
        SpecFile spec = service.parseSpecContent(content, "actions", specFile.getPath());

        assertEquals("SHALL", spec.getRequirements().get(0).getKeyword());
        assertEquals("SHALL", spec.getRequirements().get(1).getKeyword());
    }

    public void testParsesScenarios() throws Exception {
        SpecParsingService service = getProject().getService(SpecParsingService.class);
        VirtualFile specFile = myFixture.findFileInTempDir("openspec/specs/actions/spec.md");
        String content = new String(specFile.contentsToByteArray(), StandardCharsets.UTF_8);
        SpecFile spec = service.parseSpecContent(content, "actions", specFile.getPath());

        assertEquals(1, spec.getRequirements().get(0).getScenarios().size());
        assertEquals("Init creates config",
                spec.getRequirements().get(0).getScenarios().get(0).getName());
    }

    public void testParsesDeltaSpec() throws Exception {
        SpecParsingService service = getProject().getService(SpecParsingService.class);
        VirtualFile deltaFile = myFixture.findFileInTempDir(
                "openspec/changes/test-change/specs/actions/spec.md");
        assertNotNull("Delta spec should exist", deltaFile);

        String content = new String(deltaFile.contentsToByteArray(), StandardCharsets.UTF_8);
        SpecFile spec = service.parseSpecContent(content, "actions", deltaFile.getPath());

        assertNotNull(spec);
        assertEquals(1, spec.getRequirements().size());
        assertEquals("Validate Action", spec.getRequirements().get(0).getName());
    }
}
