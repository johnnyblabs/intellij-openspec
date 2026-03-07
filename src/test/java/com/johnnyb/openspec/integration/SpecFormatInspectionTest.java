package com.johnnyb.openspec.integration;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.psi.PsiFile;
import com.johnnyb.openspec.validation.SpecFormatInspection;

/**
 * Integration tests for SpecFormatInspection.
 * Verifies that spec format issues are detected in the editor.
 */
public class SpecFormatInspectionTest extends OpenSpecIntegrationTestBase {

    public void testValidSpecHasNoWarnings() {
        PsiFile file = myFixture.configureByText("spec.md",
                "# Domain\n\n### Requirement: Feature\n\nThe system SHALL do something.\n");

        SpecFormatInspection inspection = new SpecFormatInspection();
        ProblemDescriptor[] problems = inspection.checkFile(file,
                InspectionManager.getInstance(getProject()), false);

        // Valid spec has requirement + RFC keyword — no warnings expected
        assertEquals(0, problems.length);
    }

    public void testMissingRequirementWarning() {
        PsiFile file = myFixture.configureByText("spec.md",
                "# Domain\n\nSome text without any requirements.\n");

        SpecFormatInspection inspection = new SpecFormatInspection();
        ProblemDescriptor[] problems = inspection.checkFile(file,
                InspectionManager.getInstance(getProject()), false);

        assertTrue("Should detect missing requirement", problems.length > 0);
        assertTrue(problems[0].getDescriptionTemplate().contains("Requirement"));
    }

    public void testMissingRfcKeywordWarning() {
        PsiFile file = myFixture.configureByText("spec.md",
                "# Domain\n\n### Requirement: Feature\n\nThe system does something without a keyword.\n");

        SpecFormatInspection inspection = new SpecFormatInspection();
        ProblemDescriptor[] problems = inspection.checkFile(file,
                InspectionManager.getInstance(getProject()), false);

        assertTrue("Should detect missing RFC 2119 keyword", problems.length > 0);
        boolean hasKeywordWarning = false;
        for (ProblemDescriptor p : problems) {
            if (p.getDescriptionTemplate().contains("RFC 2119")) {
                hasKeywordWarning = true;
                break;
            }
        }
        assertTrue("Should have RFC 2119 keyword warning", hasKeywordWarning);
    }
}
