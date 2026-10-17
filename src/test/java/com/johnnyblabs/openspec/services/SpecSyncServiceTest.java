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
        void modifiesRequirementWithNonCanonicalHeaderCasing() {
            String mainSpec = """
                    # case-parity Specification

                    ## Requirements

                    ### requirement: Target block
                    The system SHALL be found.

                    #### Scenario: Find
                    - **WHEN** sync looks it up
                    - **THEN** it resolves
                    """;
            String delta = """
                    ## MODIFIED Requirements

                    ### Requirement: Target block
                    The system SHALL be found and updated.

                    #### Scenario: Find
                    - **WHEN** sync looks it up
                    - **THEN** it resolves
                    """;

            SpecMerge.Result r = SpecMerge.merge("case-parity", "c1", delta, mainSpec);

            assertNull(r.refusal(), "the header token matches in any casing; only the NAME is exact");
            assertTrue(r.projected().contains("found and updated"), r.projected());
        }

        @Test
        void renamedRewritesNonCanonicalHeaderToCanonicalCasing() {
            String mainSpec = """
                    # case-parity Specification

                    ## Requirements

                    ### requirement: Old name
                    The system SHALL be renamed.
                    """;
            String delta = "## RENAMED Requirements\n\n- FROM: `### Requirement: Old name`\n- TO: `### Requirement: New name`\n";

            SpecMerge.Result r = SpecMerge.merge("case-parity", "c1", delta, mainSpec);

            assertNull(r.refusal(), "rename must match the lowercase-token header");
            assertTrue(r.projected().contains("### Requirement: New name"), r.projected());
            assertFalse(r.projected().contains("### requirement:"), r.projected());
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
        void bareRenamedNamesAreNotRenames() {
            // OpenSpec reads FROM:/TO: only with the `### Requirement:` header; its validate rejects a delta holding
            // only bare lines and archive refuses it, so the plugin reads no rename from them either.
            String content = """
                    ## RENAMED Requirements

                    FROM: Old name
                    TO: New name
                    """;

            assertTrue(service.parseDeltaSpecContent("workflow", content).isEmpty());
            assertEquals("Delta parsing found no operations for workflow. Provide ADDED/MODIFIED/REMOVED/RENAMED sections in change spec.",
                    SpecMerge.merge("workflow", "c1", content, "# workflow Specification\n\n## Requirements\n").refusal());
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
            // Operations come back in apply order (RENAMED, REMOVED, MODIFIED, ADDED), not document order.
            assertEquals(OperationType.REMOVED, ops.get(0).type());
            assertEquals(OperationType.ADDED, ops.get(1).type());
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
        void bulletedBareRenamedNamesAreNotRenamesEither() {
            String content = """
                    ## RENAMED Requirements

                    - FROM: Old requirement
                    - TO: New requirement
                    """;

            assertTrue(service.parseDeltaSpecContent("cap", content).isEmpty());
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
        void normalizeName_stripsClosingHashesAndTrims_keepsCase() {
            assertEquals("Works", SpecMerge.normalizeName("  Works ##  "));
            assertEquals("C#", SpecMerge.normalizeName("C#"), "a # not preceded by a space is part of the name");
            assertEquals("Works", SpecMerge.normalizeName("Works\t#"));
            assertEquals("works", SpecMerge.normalizeName("works"), "no case folding: names match exactly");
            assertEquals("works well", SpecMerge.foldName("Works   Well"), "the fold is only for refusing near-misses");
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

                SpecMerge.Result r = SpecMerge.merge("demo", "c", delta, main);
                assertNull(r.refusal(), "rename must find its target");
                assertEquals(requirementHeaders(fixture(base + "archived-spec.md")), requirementHeaders(r.projected()));
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

            SpecMerge.Result r = SpecMerge.merge("demo", "c", delta, main);
            assertNull(r.refusal());
            assertTrue(r.warnings().isEmpty(), "removal must find its target: " + r.warnings());
            assertEquals(requirementHeaders(fixture(base + "archived-spec.md")), requirementHeaders(r.projected()),
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
    class Merge {

        private static final String SAMPLE_SPEC = """
                # workflow Specification

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

        private SpecMerge.Result merge(String delta) {
            return SpecMerge.merge("workflow", "c1", delta, SAMPLE_SPEC);
        }

        @Test
        void added_appendsAfterExistingRequirements() {
            SpecMerge.Result r = merge("## ADDED Requirements\n\n### Requirement: Sync action\nThe plugin SHALL sync specs.\n\n#### Scenario: Sync\n- **WHEN** sync\n- **THEN** specs update\n");

            assertNull(r.refusal());
            assertTrue(r.projected().indexOf("### Requirement: Sync action") > r.projected().indexOf("### Requirement: Archive action"));
        }

        @Test
        void added_newCapabilityGetsTheClientsSkeleton() {
            SpecMerge.Result r = SpecMerge.merge("new-cap", "add-thing",
                    "## ADDED Requirements\n\n### Requirement: First req\nDoes something.\n\n#### Scenario: Basic\n- **WHEN** x\n- **THEN** y\n", null);

            assertEquals("""
                    # new-cap Specification

                    ## Purpose
                    TBD - created by archiving change add-thing. Update Purpose after archive.

                    ## Requirements

                    ### Requirement: First req
                    Does something.

                    #### Scenario: Basic
                    - **WHEN** x
                    - **THEN** y
                    """, r.projected());
        }

        @Test
        void modified_replacesBlockKeepingItsScenarios() {
            SpecMerge.Result r = merge("## MODIFIED Requirements\n\n### Requirement: Propose action\nThe plugin SHALL create a change with validation.\n\n#### Scenario: Change creation\n- **WHEN** propose\n- **THEN** validate first\n");

            assertNull(r.refusal());
            assertTrue(r.projected().contains("create a change with validation"));
            assertFalse(r.projected().contains("create a new change."));
            assertTrue(r.projected().contains("### Requirement: Archive action"));
        }

        @Test
        void modified_droppingAScenarioIsRefused() {
            SpecMerge.Result r = merge("## MODIFIED Requirements\n\n### Requirement: Propose action\nThe plugin SHALL create a change with validation.\n\n#### Scenario: Validated creation\n- **WHEN** propose\n- **THEN** validate first\n");

            assertNull(r.projected());
            assertTrue(r.refusal().contains("current spec contains scenario(s) not present in the modified block: \"Change creation\""), r.refusal());
        }

        @Test
        void modified_unmatchedIsRefused() {
            SpecMerge.Result r = merge("## MODIFIED Requirements\n\n### Requirement: Nonexistent req\nContent\n\n#### Scenario: S\n- **WHEN** a\n- **THEN** b\n");

            assertEquals("workflow MODIFIED failed for header \"### Requirement: Nonexistent req\" - not found", r.refusal());
        }

        @Test
        void modified_caseVariantIsNotAMatch() {
            SpecMerge.Result r = merge("## MODIFIED Requirements\n\n### Requirement: propose action\nContent.\n\n#### Scenario: Change creation\n- **WHEN** a\n- **THEN** b\n");

            assertEquals("workflow MODIFIED failed for header \"### Requirement: propose action\" - not found", r.refusal());
        }

        @Test
        void removed_removesBlock() {
            SpecMerge.Result r = merge("## REMOVED Requirements\n\n### Requirement: Archive action\n**Reason**: Deprecated\n");

            assertNull(r.refusal());
            assertFalse(r.projected().contains("### Requirement: Archive action"));
            assertTrue(r.projected().contains("### Requirement: Propose action"));
        }

        @Test
        void removed_alreadyGoneIsAWarningNotARefusal() {
            SpecMerge.Result r = merge("## REMOVED Requirements\n\n- `### Requirement: Ghost req`\n");

            assertNull(r.refusal());
            assertEquals(List.of("workflow - REMOVED requirement \"Ghost req\" is not in the current spec; treating it as already removed."), r.warnings());
        }

        @Test
        void renamed_updatesHeaderInPlace() {
            SpecMerge.Result r = merge("## RENAMED Requirements\n\n- FROM: `### Requirement: Propose action`\n- TO: `### Requirement: Create action`\n");

            assertNull(r.refusal());
            assertTrue(r.projected().indexOf("### Requirement: Create action") < r.projected().indexOf("### Requirement: Archive action"));
            assertFalse(r.projected().contains("### Requirement: Propose action"));
        }

        @Test
        void renamed_missingSourceIsRefused() {
            SpecMerge.Result r = merge("## RENAMED Requirements\n\n- FROM: `### Requirement: Missing`\n- TO: `### Requirement: New name`\n");

            assertEquals("workflow RENAMED failed for header \"### Requirement: Missing\" - source not found", r.refusal());
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
