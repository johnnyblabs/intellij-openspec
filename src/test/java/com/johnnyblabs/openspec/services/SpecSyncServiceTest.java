package com.johnnyblabs.openspec.services;

import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.model.DeltaSpecOperation;
import com.johnnyblabs.openspec.model.DeltaSpecOperation.OperationType;
import com.johnnyblabs.openspec.model.SpecSyncResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class SpecSyncServiceTest {

    @Mock Project project;

    private SpecSyncService service;

    @BeforeEach
    void setUp() {
        service = new SpecSyncService(project);
    }

    @Nested
    class ParseDeltaSpecContent {

        @Test
        void parsesAddedSection() {
            String content = """
                    ## ADDED Requirements

                    ### Requirement: User export
                    The system SHALL allow data export.

                    #### Scenario: CSV export
                    - **WHEN** user clicks Export
                    - **THEN** system downloads CSV
                    """;

            List<DeltaSpecOperation> ops = service.parseDeltaSpecContent("data-export", content);

            assertEquals(1, ops.size());
            assertEquals(OperationType.ADDED, ops.getFirst().type());
            assertEquals("data-export", ops.getFirst().capabilityName());
            assertEquals("User export", ops.getFirst().requirementName());
            assertTrue(ops.getFirst().content().contains("### Requirement: User export"));
            assertTrue(ops.getFirst().content().contains("#### Scenario: CSV export"));
        }

        @Test
        void parsesModifiedSection() {
            String content = """
                    ## MODIFIED Requirements

                    ### Requirement: Login flow
                    The system SHALL require two-factor auth.

                    #### Scenario: 2FA prompt
                    - **WHEN** user logs in
                    - **THEN** system prompts for 2FA code
                    """;

            List<DeltaSpecOperation> ops = service.parseDeltaSpecContent("auth", content);

            assertEquals(1, ops.size());
            assertEquals(OperationType.MODIFIED, ops.getFirst().type());
            assertEquals("Login flow", ops.getFirst().requirementName());
        }

        @Test
        void parsesRemovedSection() {
            String content = """
                    ## REMOVED Requirements

                    ### Requirement: Legacy export
                    **Reason**: Replaced by new export
                    **Migration**: Use /api/v2/export
                    """;

            List<DeltaSpecOperation> ops = service.parseDeltaSpecContent("export", content);

            assertEquals(1, ops.size());
            assertEquals(OperationType.REMOVED, ops.getFirst().type());
            assertEquals("Legacy export", ops.getFirst().requirementName());
            assertTrue(ops.getFirst().content().contains("**Reason**"));
        }

        @Test
        void parsesRenamedSection() {
            String content = """
                    ## RENAMED Requirements

                    FROM: Old name
                    TO: New name
                    """;

            List<DeltaSpecOperation> ops = service.parseDeltaSpecContent("workflow", content);

            assertEquals(1, ops.size());
            assertEquals(OperationType.RENAMED, ops.getFirst().type());
            assertEquals("Old name", ops.getFirst().fromName());
            assertEquals("New name", ops.getFirst().toName());
        }

        @Test
        void parsesMultipleSections() {
            String content = """
                    ## ADDED Requirements

                    ### Requirement: New feature
                    The system SHALL do something.

                    #### Scenario: Basic
                    - **WHEN** triggered
                    - **THEN** it works

                    ## REMOVED Requirements

                    ### Requirement: Old feature
                    **Reason**: Deprecated
                    **Migration**: None
                    """;

            List<DeltaSpecOperation> ops = service.parseDeltaSpecContent("cap", content);

            assertEquals(2, ops.size());
            assertEquals(OperationType.ADDED, ops.get(0).type());
            assertEquals(OperationType.REMOVED, ops.get(1).type());
        }

        @Test
        void parsesMultipleRequirementsInOneSection() {
            String content = """
                    ## ADDED Requirements

                    ### Requirement: First
                    Description one.

                    #### Scenario: A
                    - **WHEN** a
                    - **THEN** b

                    ### Requirement: Second
                    Description two.

                    #### Scenario: B
                    - **WHEN** c
                    - **THEN** d
                    """;

            List<DeltaSpecOperation> ops = service.parseDeltaSpecContent("cap", content);

            assertEquals(2, ops.size());
            assertEquals("First", ops.get(0).requirementName());
            assertEquals("Second", ops.get(1).requirementName());
        }

        @Test
        void returnsEmptyForNoDeltaSections() {
            String content = """
                    # Some Capability

                    ## Purpose
                    Just a regular spec file.

                    ## Requirements

                    ### Requirement: Something
                    Normal requirement.
                    """;

            List<DeltaSpecOperation> ops = service.parseDeltaSpecContent("cap", content);
            assertTrue(ops.isEmpty());
        }

        @Test
        void handlesEmptyContent() {
            List<DeltaSpecOperation> ops = service.parseDeltaSpecContent("cap", "");
            assertTrue(ops.isEmpty());
        }

        @Test
        void parsesRenamedWithBulletFormat() {
            String content = """
                    ## RENAMED Requirements

                    - FROM: Old requirement
                    - TO: New requirement
                    """;

            List<DeltaSpecOperation> ops = service.parseDeltaSpecContent("cap", content);

            assertEquals(1, ops.size());
            assertEquals("Old requirement", ops.getFirst().fromName());
            assertEquals("New requirement", ops.getFirst().toName());
        }
    }

    @Nested
    class ApplyOperations {

        private static final String SAMPLE_SPEC = """
                # Workflow

                ## Purpose
                Artifact generation pipeline.

                ## Requirements

                ### Requirement: Propose action
                The plugin SHALL create a new change.

                #### Scenario: Change creation
                - **WHEN** the user proposes a change
                - **THEN** the plugin SHALL create the change directory

                ### Requirement: Archive action
                The plugin SHALL move completed changes.

                #### Scenario: Archive flow
                - **WHEN** the user archives
                - **THEN** the change SHALL be moved
                """;

        @Test
        void applyAdded_appendsToExisting() {
            DeltaSpecOperation op = new DeltaSpecOperation(
                    OperationType.ADDED, "workflow", "Sync action",
                    "### Requirement: Sync action\nThe plugin SHALL sync specs.\n\n#### Scenario: Sync\n- **WHEN** sync\n- **THEN** specs update",
                    null, null);

            String result = service.applyAdded(SAMPLE_SPEC, op);

            assertTrue(result.contains("### Requirement: Sync action"));
            assertTrue(result.contains("### Requirement: Propose action"));
            // New content should be at the end
            int proposeIdx = result.indexOf("### Requirement: Propose action");
            int syncIdx = result.indexOf("### Requirement: Sync action");
            assertTrue(syncIdx > proposeIdx);
        }

        @Test
        void applyAdded_createsNewFile() {
            DeltaSpecOperation op = new DeltaSpecOperation(
                    OperationType.ADDED, "new-cap", "First req",
                    "### Requirement: First req\nDoes something.\n\n#### Scenario: Basic\n- **WHEN** x\n- **THEN** y",
                    null, null);

            String result = service.applyAdded("", op);

            assertTrue(result.startsWith("# New Cap"));
            assertTrue(result.contains("## Purpose"));
            assertTrue(result.contains("### Requirement: First req"));
        }

        @Test
        void applyModified_replacesBlock() {
            DeltaSpecOperation op = new DeltaSpecOperation(
                    OperationType.MODIFIED, "workflow", "Propose action",
                    "### Requirement: Propose action\nThe plugin SHALL create a change with validation.\n\n#### Scenario: Validated creation\n- **WHEN** propose\n- **THEN** validate first",
                    null, null);
            List<String> warnings = new ArrayList<>();

            String result = service.applyModified(SAMPLE_SPEC, op, warnings);

            assertTrue(warnings.isEmpty());
            assertTrue(result.contains("create a change with validation"));
            assertFalse(result.contains("create a new change."));
            // Archive should still be there
            assertTrue(result.contains("### Requirement: Archive action"));
        }

        @Test
        void applyModified_warnsOnUnmatched() {
            DeltaSpecOperation op = new DeltaSpecOperation(
                    OperationType.MODIFIED, "workflow", "Nonexistent req",
                    "### Requirement: Nonexistent req\nContent",
                    null, null);
            List<String> warnings = new ArrayList<>();

            String result = service.applyModified(SAMPLE_SPEC, op, warnings);

            assertEquals(1, warnings.size());
            assertTrue(warnings.getFirst().contains("not found"));
            assertEquals(SAMPLE_SPEC, result);
        }

        @Test
        void applyRemoved_removesBlock() {
            DeltaSpecOperation op = new DeltaSpecOperation(
                    OperationType.REMOVED, "workflow", "Archive action",
                    "### Requirement: Archive action\n**Reason**: Deprecated",
                    null, null);
            List<String> warnings = new ArrayList<>();

            String result = service.applyRemoved(SAMPLE_SPEC, op, warnings);

            assertTrue(warnings.isEmpty());
            assertFalse(result.contains("### Requirement: Archive action"));
            assertTrue(result.contains("### Requirement: Propose action"));
        }

        @Test
        void applyRemoved_warnsOnUnmatched() {
            DeltaSpecOperation op = new DeltaSpecOperation(
                    OperationType.REMOVED, "workflow", "Ghost req",
                    "### Requirement: Ghost req\n**Reason**: Gone",
                    null, null);
            List<String> warnings = new ArrayList<>();

            String result = service.applyRemoved(SAMPLE_SPEC, op, warnings);

            assertEquals(1, warnings.size());
            assertTrue(warnings.getFirst().contains("not found"));
        }

        @Test
        void applyRenamed_updatesHeader() {
            DeltaSpecOperation op = new DeltaSpecOperation(
                    OperationType.RENAMED, "workflow", "Propose action",
                    null, "Propose action", "Create action");
            List<String> warnings = new ArrayList<>();

            String result = service.applyRenamed(SAMPLE_SPEC, op, warnings);

            assertTrue(warnings.isEmpty());
            assertTrue(result.contains("### Requirement: Create action"));
            assertFalse(result.contains("### Requirement: Propose action"));
        }

        @Test
        void applyRenamed_warnsOnUnmatched() {
            DeltaSpecOperation op = new DeltaSpecOperation(
                    OperationType.RENAMED, "workflow", "Missing",
                    null, "Missing", "New name");
            List<String> warnings = new ArrayList<>();

            String result = service.applyRenamed(SAMPLE_SPEC, op, warnings);

            assertEquals(1, warnings.size());
        }

        @Test
        void sortOperations_ordersCorrectly() {
            List<DeltaSpecOperation> ops = List.of(
                    new DeltaSpecOperation(OperationType.ADDED, "c", "a", "content", null, null),
                    new DeltaSpecOperation(OperationType.REMOVED, "c", "r", "content", null, null),
                    new DeltaSpecOperation(OperationType.MODIFIED, "c", "m", "content", null, null),
                    new DeltaSpecOperation(OperationType.RENAMED, "c", "rn", null, "old", "new")
            );

            List<DeltaSpecOperation> sorted = service.sortOperations(ops);

            assertEquals(OperationType.REMOVED, sorted.get(0).type());
            assertEquals(OperationType.RENAMED, sorted.get(1).type());
            assertEquals(OperationType.MODIFIED, sorted.get(2).type());
            assertEquals(OperationType.ADDED, sorted.get(3).type());
        }

        @Test
        void applyOperations_allTypesInSequence() {
            List<DeltaSpecOperation> ops = List.of(
                    new DeltaSpecOperation(OperationType.REMOVED, "workflow", "Archive action",
                            "### Requirement: Archive action\n**Reason**: gone", null, null),
                    new DeltaSpecOperation(OperationType.ADDED, "workflow", "Sync",
                            "### Requirement: Sync\nSyncs specs.\n\n#### Scenario: Sync\n- **WHEN** sync\n- **THEN** done",
                            null, null)
            );
            List<String> warnings = new ArrayList<>();

            String result = service.applyOperations(SAMPLE_SPEC, ops, warnings);

            assertTrue(warnings.isEmpty());
            assertFalse(result.contains("Archive action"));
            assertTrue(result.contains("### Requirement: Sync"));
            assertTrue(result.contains("### Requirement: Propose action"));
        }
    }

    @Nested
    class FindRequirementBlock {

        @Test
        void findsBlockBetweenHeadings() {
            String content = """
                    ### Requirement: First
                    Content one.

                    ### Requirement: Second
                    Content two.
                    """;

            int[] range = service.findRequirementBlock(content, "First");
            assertNotNull(range);
            String block = content.substring(range[0], range[1]);
            assertTrue(block.contains("Content one"));
            assertFalse(block.contains("Content two"));
        }

        @Test
        void findsLastBlock() {
            String content = """
                    ### Requirement: First
                    Content one.

                    ### Requirement: Last
                    Content last.
                    """;

            int[] range = service.findRequirementBlock(content, "Last");
            assertNotNull(range);
            String block = content.substring(range[0], range[1]);
            assertTrue(block.contains("Content last"));
        }

        @Test
        void returnsNullForMissing() {
            String content = "### Requirement: Something\nContent.";
            assertNull(service.findRequirementBlock(content, "Missing"));
        }

        @Test
        void caseInsensitiveMatch() {
            String content = "### Requirement: Propose Action\nContent.";
            int[] range = service.findRequirementBlock(content, "propose action");
            assertNotNull(range);
        }
    }

    @Nested
    class SpecSyncResultTest {

        @Test
        void hasChanges_trueWhenDifferent() {
            var result = new com.johnnyblabs.openspec.model.SpecSyncResult(
                    "cap", "/path", "original", "modified", List.of(), List.of());
            assertTrue(result.hasChanges());
        }

        @Test
        void hasChanges_falseWhenSame() {
            var result = new com.johnnyblabs.openspec.model.SpecSyncResult(
                    "cap", "/path", "same", "same", List.of(), List.of());
            assertFalse(result.hasChanges());
        }

        @Test
        void hasChanges_trueWhenOriginalNull() {
            var result = new com.johnnyblabs.openspec.model.SpecSyncResult(
                    "cap", "/path", null, "new content", List.of(), List.of());
            assertTrue(result.hasChanges());
        }
    }

    @Nested
    class DeltaSpecOperationTest {

        @Test
        void rejectsNullType() {
            assertThrows(IllegalArgumentException.class, () ->
                    new DeltaSpecOperation(null, "cap", "req", "content", null, null));
        }

        @Test
        void rejectsNullCapability() {
            assertThrows(IllegalArgumentException.class, () ->
                    new DeltaSpecOperation(OperationType.ADDED, null, "req", "content", null, null));
        }
    }
}
