package com.johnnyblabs.openspec.services;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.johnnyblabs.openspec.validation.BuiltInValidator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The production offer on a project on disk: the capabilities named are every one the marker would retire, from the
 * plugin's merge over the change's delta specs, not only the one the refusal names.
 */
public class RetireMarkerOfferTest extends BasePlatformTestCase {

    private static final String DELTA = "## REMOVED Requirements\n\n### Requirement: %s works\n**Reason**: gone\n**Migration**: none\n";
    private static final String HINT = "Rebuilt spec for 'foo' failed validation. No files were changed. This change removes "
            + "the last requirement 'foo' has. To retire the capability and delete its spec, add `retire_capabilities: true` "
            + "to the change's .openspec.yaml (alongside its `schema:`, which that file requires), then rerun.";

    private Path base;

    private static String spec(String id, String cap) {
        return "# " + id + " Specification\n\n## Purpose\n" + cap + " does things.\n\n## Requirements\n\n### Requirement: " + cap
                + " works\nThe system SHALL " + id + ".\n\n#### Scenario: " + id + "\n- **WHEN** x\n- **THEN** y\n";
    }

    private Project project(String metadata) throws IOException {
        base = FileUtil.createTempDirectory("retire-offer", null).toPath().toRealPath();
        for (String id : List.of("foo", "zoo")) {
            String cap = Character.toUpperCase(id.charAt(0)) + id.substring(1);
            Files.createDirectories(base.resolve("openspec/specs/" + id));
            Files.writeString(base.resolve("openspec/specs/" + id + "/spec.md"), spec(id, cap));
            Files.createDirectories(base.resolve("openspec/changes/c/specs/" + id));
            Files.writeString(base.resolve("openspec/changes/c/specs/" + id + "/spec.md"), DELTA.formatted(cap));
        }
        Files.writeString(base.resolve("openspec/changes/c/.openspec.yaml"), metadata);
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(base);
        Project project = mock(Project.class);
        when(project.getBasePath()).thenReturn(base.toString());
        when(project.getService(BuiltInValidator.class)).thenReturn(getProject().getService(BuiltInValidator.class));
        when(project.getService(SpecSyncService.class)).thenReturn(new SpecSyncService(project));
        return project;
    }

    public void testARefusalNamingOneCapabilityOffersEveryOneTheMarkerRetires() throws IOException {
        Project project = project("schema: spec-driven\ncreated: 2026-11-27\n");
        assertEquals("sanity: both are refused unmarked", 2,
                project.getService(SpecSyncService.class).computeSync("c").stream().filter(r -> !r.refusals().isEmpty()).count());
        RetireMarkerFix.Offer offer = RetireMarkerFix.offer(project, "c", List.of(HINT));
        assertNotNull(offer);
        assertEquals(List.of("foo", "zoo"), offer.capabilities());
        assertEquals("deciding writes nothing", "schema: spec-driven\ncreated: 2026-11-27\n",
                Files.readString(base.resolve("openspec/changes/c/.openspec.yaml")));
    }

    public void testNoOfferWhenTheEditedMetadataStillWouldNotCount() throws IOException {
        assertNull("no schema:", RetireMarkerFix.offer(project("created: 2026-11-27\n"), "c", List.of(HINT)));
    }
}
