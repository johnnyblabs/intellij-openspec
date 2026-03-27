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

    private void refreshVfs() {
        com.intellij.openapi.vfs.VirtualFile root = myFixture.findFileInTempDir("openspec");
        if (root != null) {
            com.intellij.openapi.vfs.VfsUtil.markDirtyAndRefresh(false, true, true, root);
        }
    }
}
