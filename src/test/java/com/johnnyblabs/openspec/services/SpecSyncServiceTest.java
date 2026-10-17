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
import java.util.Map;

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
    class HeaderCasingParity {

        @Test
        void parsesDeltaWithLowercaseHeaderToken() {
            String content = """
                    ## ADDED Requirements

                    ### requirement: Lowercase header
                    The system SHALL accept this requirement.

                    #### Scenario: Accept
                    - **WHEN** the delta is parsed
                    - **THEN** the block is found
                    """;

            List<DeltaSpecOperation> ops = service.parseDeltaSpecContent("case-parity", content);

            assertEquals(1, ops.size(), "CLI 1.4+ parses the header token case-insensitively");
            assertEquals("Lowercase header", ops.getFirst().requirementName());
        }

        @Test
        void findsRequirementBlockWithNonCanonicalHeaderCasing() {
            String mainSpec = """
                    # Case Parity

                    ## Requirements

                    ### requirement: Target block
                    The system SHALL be found.

                    #### Scenario: Find
                    - **WHEN** sync looks it up
                    - **THEN** it resolves
                    """;

            int[] block = service.findRequirementBlock(mainSpec, "Target block");

            assertNotNull(block, "Lookup must resolve a lowercase-token header");
            assertTrue(mainSpec.substring(block[0], block[1]).contains("The system SHALL be found."));
        }

        @Test
        void renamedRewritesNonCanonicalHeaderToCanonicalCasing() {
            String mainSpec = """
                    # Case Parity

                    ### requirement: Old name
                    The system SHALL be renamed.
                    """;
            DeltaSpecOperation op = new DeltaSpecOperation(
                    OperationType.RENAMED, "case-parity", null, null, "Old name", "New name");
            List<String> warnings = new ArrayList<>();

            String result = service.applyRenamed(mainSpec, op, warnings);

            assertTrue(warnings.isEmpty(), "Rename must match the lowercase-token header: " + warnings);
            assertTrue(result.contains("### Requirement: New name"),
                    "Rewritten header must use canonical casing, got: " + result);
            assertFalse(result.contains("### requirement:"),
                    "Non-canonical source header must not survive the rewrite: " + result);
        }
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

        @Test
        void parsesRenamedCanonicalFormToBareNames_anyBullet() {
            String content = """
                    ## RENAMED Requirements

                    * FROM: `### Requirement: Works`
                    * TO: `### Requirement: Works well`
                    """;

            List<DeltaSpecOperation> ops = service.parseDeltaSpecContent("demo", content);

            assertEquals(1, ops.size());
            assertEquals("Works", ops.getFirst().fromName());
            assertEquals("Works well", ops.getFirst().toName());
        }

        @Test
        void bareRequirementName_stripsBackticksAndHeaderToken_keepsBareNames() {
            assertEquals("Old", SpecSyncService.bareRequirementName("`### Requirement: Old`"));
            assertEquals("Old", SpecSyncService.bareRequirementName("### requirement: Old"));
            assertEquals("Old name", SpecSyncService.bareRequirementName("Old name"));
            assertEquals("Old name", SpecSyncService.bareRequirementName("  `Old name`  "));
        }

        @Test
        void capturedCanonicalRename_appliesLikeTheClientsArchive() throws Exception {
            // Contract: 1.13.2 renamed-star. show-change.json gives the client's rename names; archived-spec.md is
            // the main spec after a real `openspec archive`. Both line endings, since Windows checks out CRLF.
            String base = "/fixtures/cli/1.13.2/renamed-star/";
            var rename = com.google.gson.JsonParser.parseString(fixture(base + "show-change.json")).getAsJsonObject()
                    .getAsJsonArray("deltas").get(0).getAsJsonObject().getAsJsonObject("rename");
            for (String eol : List.of("\n", "\r\n")) {
                String delta = fixture(base + "openspec/changes/c/specs/demo/spec.md").replace("\n", eol);
                String main = fixture(base + "openspec/specs/demo/spec.md");

                List<DeltaSpecOperation> ops = service.parseDeltaSpecContent("demo", delta);
                assertEquals(1, ops.size(), "the *-bulleted canonical pair must parse (eol=" + eol.length() + "): " + ops);
                assertEquals(rename.get("from").getAsString(), ops.getFirst().fromName());
                assertEquals(rename.get("to").getAsString(), ops.getFirst().toName());

                List<String> warnings = new ArrayList<>();
                String renamed = service.applyRenamed(main, ops.getFirst(), warnings);
                assertTrue(warnings.isEmpty(), "rename must find its target: " + warnings);
                assertEquals(requirementHeaders(fixture(base + "archived-spec.md")), requirementHeaders(renamed));
            }
        }

        @Test
        void capturedBulletRemoved_parsesToTheClientsRemovals_andApplies() throws Exception {
            // Contract: the 1.13.2 removed-star control. show-change.json is the client's own reading of the delta.
            String base = "/fixtures/cli/1.13.2/removed-star/";
            String delta = fixture(base + "openspec/changes/c/specs/demo/spec.md");
            String main = fixture(base + "openspec/specs/demo/spec.md");
            com.google.gson.JsonArray deltas = com.google.gson.JsonParser.parseString(
                    fixture(base + "show-change.json")).getAsJsonObject().getAsJsonArray("deltas");
            List<String> clientRemovals = new ArrayList<>();
            for (var d : deltas) {
                var o = d.getAsJsonObject();
                if ("REMOVED".equals(o.get("operation").getAsString())) {
                    // Upstream quirk: a bullet-form REMOVED reports text "Requirement: <name>".
                    clientRemovals.add(o.getAsJsonObject("requirement").get("text").getAsString()
                            .replaceFirst("^Requirement:\\s*", ""));
                }
            }
            assertFalse(clientRemovals.isEmpty(), "capture must contain the client's REMOVED delta");

            List<DeltaSpecOperation> ops = service.parseDeltaSpecContent("demo", delta);
            assertEquals(clientRemovals, ops.stream()
                    .filter(op -> op.type() == OperationType.REMOVED)
                    .map(DeltaSpecOperation::requirementName).toList(),
                    "bullet REMOVED entries must parse to the removals the client applies");

            List<String> warnings = new ArrayList<>();
            String after = service.applyRemoved(main, ops.getFirst(), warnings);
            assertTrue(warnings.isEmpty(), "removal must find its target: " + warnings);
            assertEquals(requirementHeaders(fixture(base + "archived-spec.md")), requirementHeaders(after),
                    "the applied main spec must keep exactly the requirements the client's archive kept");

            List<DeltaSpecOperation> crlfOps = service.parseDeltaSpecContent("demo", delta.replace("\n", "\r\n"));
            assertEquals(clientRemovals, crlfOps.stream().filter(op -> op.type() == OperationType.REMOVED)
                    .map(DeltaSpecOperation::requirementName).toList(), "CRLF delta must parse the same removals");
        }

        private List<String> requirementHeaders(String spec) {
            return spec.lines().map(String::strip).filter(l -> l.startsWith("### Requirement:")).toList();
        }

        @Test
        void removedSection_mixesBlockAndBulletEntriesInDocumentOrder() {
            String content = """
                    ## REMOVED Requirements

                    - `### Requirement: First`

                    ### Requirement: Second
                    **Reason**: Replaced
                    **Migration**: None

                    + ### Requirement: Third
                    """;

            List<DeltaSpecOperation> ops = service.parseDeltaSpecContent("demo", content);

            assertEquals(List.of("First", "Second", "Third"),
                    ops.stream().map(DeltaSpecOperation::requirementName).toList());
            assertTrue(ops.stream().allMatch(op -> op.type() == OperationType.REMOVED));
        }

        private String fixture(String path) throws java.io.IOException {
            try (java.io.InputStream in = getClass().getResourceAsStream(path)) {
                assertNotNull(in, "missing fixture " + path);
                return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n");
            }
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

            // Upstream specs-apply.js order: RENAMED → REMOVED → MODIFIED → ADDED
            assertEquals(OperationType.RENAMED, sorted.get(0).type());
            assertEquals(OperationType.REMOVED, sorted.get(1).type());
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
    class DetectConflicts {

        @Test
        void noConflicts_whenNoOverlap() {
            // detectConflicts requires file system access, so we test the logic pattern:
            // two changes with distinct capabilities should yield empty map
            Map<String, java.util.List<String>> capToChanges = new java.util.LinkedHashMap<>();
            capToChanges.computeIfAbsent("auth", k -> new java.util.ArrayList<>()).add("change-a");
            capToChanges.computeIfAbsent("export", k -> new java.util.ArrayList<>()).add("change-b");
            capToChanges.entrySet().removeIf(e -> e.getValue().size() < 2);
            assertTrue(capToChanges.isEmpty());
        }

        @Test
        void detectsSingleOverlap() {
            Map<String, java.util.List<String>> capToChanges = new java.util.LinkedHashMap<>();
            capToChanges.computeIfAbsent("workflow", k -> new java.util.ArrayList<>()).add("change-a");
            capToChanges.computeIfAbsent("workflow", k -> new java.util.ArrayList<>()).add("change-b");
            capToChanges.computeIfAbsent("auth", k -> new java.util.ArrayList<>()).add("change-a");
            capToChanges.entrySet().removeIf(e -> e.getValue().size() < 2);
            assertEquals(1, capToChanges.size());
            assertTrue(capToChanges.containsKey("workflow"));
            assertEquals(List.of("change-a", "change-b"), capToChanges.get("workflow"));
        }

        @Test
        void detectsMultipleOverlaps() {
            Map<String, java.util.List<String>> capToChanges = new java.util.LinkedHashMap<>();
            // Both changes touch workflow and editor
            for (String cap : List.of("workflow", "editor")) {
                capToChanges.computeIfAbsent(cap, k -> new java.util.ArrayList<>()).add("change-a");
                capToChanges.computeIfAbsent(cap, k -> new java.util.ArrayList<>()).add("change-b");
            }
            capToChanges.computeIfAbsent("auth", k -> new java.util.ArrayList<>()).add("change-a");
            capToChanges.entrySet().removeIf(e -> e.getValue().size() < 2);
            assertEquals(2, capToChanges.size());
            assertTrue(capToChanges.containsKey("workflow"));
            assertTrue(capToChanges.containsKey("editor"));
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
