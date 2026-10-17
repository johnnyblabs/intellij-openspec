package com.johnnyblabs.openspec.validation;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.psi.PsiFile;
import com.johnnyblabs.openspec.integration.OpenSpecIntegrationTestBase;

public class DeltaSpecQuickFixTest extends OpenSpecIntegrationTestBase {

    public void testModifiedRequirementWithMatchingMainSpecOffersQuickFix() {
        // Create a delta spec with a MODIFIED requirement that exists in main spec but has no scenarios
        myFixture.addFileToProject("openspec/changes/fix-change/specs/actions/spec.md",
                "## MODIFIED Requirements\n\n### Requirement: Init Action\n\nUpdated description without scenarios.\n");
        refreshVfs();

        PsiFile deltaSpec = myFixture.configureByFile("openspec/changes/fix-change/specs/actions/spec.md");
        DeltaSpecInspection inspection = new DeltaSpecInspection();
        ProblemDescriptor[] problems = inspection.checkFile(deltaSpec,
                InspectionManager.getInstance(getProject()), false);

        assertTrue("Should have at least one problem", problems.length > 0);
        boolean hasQuickFix = false;
        for (ProblemDescriptor p : problems) {
            if (p.getDescriptionTemplate().contains("MODIFIED requirement") && p.getFixes() != null && p.getFixes().length > 0) {
                hasQuickFix = true;
                assertEquals("Copy requirement from main spec", p.getFixes()[0].getFamilyName());
            }
        }
        assertTrue("MODIFIED requirement with matching main spec should offer quick-fix", hasQuickFix);
    }

    public void testModifiedRequirementWithoutMatchingMainSpecHasNoQuickFix() {
        // Create a delta spec with a MODIFIED requirement that does NOT exist in main spec
        myFixture.addFileToProject("openspec/changes/fix-change/specs/actions/spec.md",
                "## MODIFIED Requirements\n\n### Requirement: Nonexistent Requirement\n\nSome description.\n");
        refreshVfs();

        PsiFile deltaSpec = myFixture.configureByFile("openspec/changes/fix-change/specs/actions/spec.md");
        DeltaSpecInspection inspection = new DeltaSpecInspection();
        ProblemDescriptor[] problems = inspection.checkFile(deltaSpec,
                InspectionManager.getInstance(getProject()), false);

        assertTrue("Should have at least one problem", problems.length > 0);
        for (ProblemDescriptor p : problems) {
            if (p.getDescriptionTemplate().contains("MODIFIED requirement")) {
                assertTrue("Should have no quick-fix for unmatched requirement",
                        p.getFixes() == null || p.getFixes().length == 0);
            }
        }
    }

    public void testQuickFixInsertsFullRequirementBlock() {
        // Verify findRequirementInMainSpec returns the full block
        String block = DeltaSpecInspection.findRequirementInMainSpec(
                getProject(), "actions", "Init Action");

        assertNotNull("Should find Init Action in main spec", block);
        assertTrue("Block should contain requirement header",
                block.contains("### Requirement: Init Action"));
        assertTrue("Block should contain scenario",
                block.contains("#### Scenario: Init creates config"));
        assertTrue("Block should contain SHALL keyword",
                block.contains("SHALL"));
    }

    public void testFilledScaffoldWithEmptyRenamedSectionHasNoRenamedProblem() {
        // The scaffolded RENAMED section is comment-only by design; the CLI accepts it beside a parsed
        // ADDED requirement (fixtures/cli/1.12.0/scaffold-delta/validate-new.json), so the editor must not red it.
        String filled = com.johnnyblabs.openspec.scaffolding.TemplateProvider.deltaSpecTemplate("actions")
                .replace("## MODIFIED Requirements", """
                        ### Requirement: Logout
                        The system SHALL let a signed-in user log out.

                        #### Scenario: Logging out ends the session
                        - **WHEN** a signed-in user logs out
                        - **THEN** their session ends

                        ## MODIFIED Requirements""");
        assertTrue("insertion anchor must exist in the template", filled.contains("### Requirement: Logout"));
        assertFalse(renamedProblems(filled).iterator().hasNext());
    }

    public void testStarBulletedRenamedPairIsNotAProblem() {
        // CLI 1.13.0+ accepts * and + bullets in RENAMED (fixtures/cli/1.13.2/renamed-star, valid:true).
        assertFalse("a *-bulleted pair must not be flagged",
                renamedProblems("## RENAMED Requirements\n\n* FROM: `### Requirement: Init Action`\n"
                        + "* TO: `### Requirement: Initialize`\n").iterator().hasNext());
    }

    public void testPlusBulletedRenamedPairIsNotAProblem() {
        assertFalse("a +-bulleted pair must not be flagged",
                renamedProblems("## RENAMED Requirements\n\n+ FROM: `### Requirement: Init Action`\n"
                        + "+ TO: `### Requirement: Initialize`\n").iterator().hasNext());
    }

    public void testCapturedUnpairedStarRenamedIsAProblem() throws Exception {
        // Liveness anchor: CLI 1.13.2 rejects this lone `* FROM:` (renamed-star-unpaired/validate-change.json).
        String delta;
        try (java.io.InputStream in = getClass().getResourceAsStream(
                "/fixtures/cli/1.13.2/renamed-star-unpaired/openspec/changes/c/specs/demo/spec.md")) {
            assertNotNull("missing fixture", in);
            delta = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n");
        }
        assertTrue("the inspection must flag a lone * FROM:", renamedProblems(delta).iterator().hasNext());
    }

    public void testRenamedFromWithoutToIsStillAProblem() {
        assertTrue("FROM: with no TO: must still be flagged",
                renamedProblems("## RENAMED Requirements\n\n- FROM: `### Requirement: Init Action`\n")
                        .iterator().hasNext());
    }

    private java.util.List<ProblemDescriptor> renamedProblems(String deltaContent) {
        myFixture.addFileToProject("openspec/changes/fix-change/specs/actions/spec.md", deltaContent);
        refreshVfs();
        PsiFile deltaSpec = myFixture.configureByFile("openspec/changes/fix-change/specs/actions/spec.md");
        ProblemDescriptor[] problems = new DeltaSpecInspection().checkFile(deltaSpec,
                InspectionManager.getInstance(getProject()), false);
        java.util.List<ProblemDescriptor> renamed = new java.util.ArrayList<>();
        for (ProblemDescriptor p : problems) {
            if (p.getDescriptionTemplate().contains("RENAMED section")) {
                renamed.add(p);
            }
        }
        return renamed;
    }

    private void refreshVfs() {
        com.intellij.openapi.vfs.VirtualFile root = myFixture.findFileInTempDir("openspec");
        if (root != null) {
            com.intellij.openapi.vfs.VfsUtil.markDirtyAndRefresh(false, true, true, root);
        }
    }
}
