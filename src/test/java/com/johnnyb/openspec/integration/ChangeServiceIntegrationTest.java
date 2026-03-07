package com.johnnyb.openspec.integration;

import com.johnnyb.openspec.model.Change;
import com.johnnyb.openspec.model.ChangeStatus;
import com.johnnyb.openspec.services.ChangeService;

import java.util.List;

/**
 * Integration tests for ChangeService with a real IntelliJ project.
 * Verifies change discovery, metadata parsing, and delta spec detection.
 */
public class ChangeServiceIntegrationTest extends OpenSpecIntegrationTestBase {

    public void testFindsActiveChanges() {
        ChangeService changeService = getProject().getService(ChangeService.class);
        List<Change> changes = changeService.getActiveChanges();

        assertNotNull("Changes list should not be null", changes);
        assertEquals("Should find one active change", 1, changes.size());
        assertEquals("test-change", changes.get(0).getName());
    }

    public void testParsesChangeMetadata() {
        ChangeService changeService = getProject().getService(ChangeService.class);
        List<Change> changes = changeService.getActiveChanges();
        Change change = changes.get(0);

        assertNotNull("Metadata should be parsed", change.getMetadata());
        assertEquals("spec-driven", change.getMetadata().getSchema());
    }

    public void testDetectsChangeStatus() {
        ChangeService changeService = getProject().getService(ChangeService.class);
        List<Change> changes = changeService.getActiveChanges();
        Change change = changes.get(0);

        ChangeStatus status = changeService.getStatus(change);
        assertEquals(ChangeStatus.PROPOSED, status);
    }

    public void testFindsArtifactFiles() {
        ChangeService changeService = getProject().getService(ChangeService.class);
        List<Change> changes = changeService.getActiveChanges();
        Change change = changes.get(0);

        assertTrue("Should find proposal.md as artifact",
                change.getArtifactFiles().contains("proposal.md"));
    }

    public void testFindsDeltaSpecs() {
        ChangeService changeService = getProject().getService(ChangeService.class);
        List<Change> changes = changeService.getActiveChanges();
        Change change = changes.get(0);

        List<String> deltaSpecs = changeService.getDeltaSpecNames(change);
        assertNotNull(deltaSpecs);
        assertEquals("Should find one delta spec domain", 1, deltaSpecs.size());
        assertEquals("actions", deltaSpecs.get(0));
    }

    public void testArchivedChangesEmptyInitially() {
        ChangeService changeService = getProject().getService(ChangeService.class);
        List<Change> archived = changeService.getArchivedChanges();
        assertNotNull(archived);
        assertTrue("No archived changes in fixture", archived.isEmpty());
    }
}
