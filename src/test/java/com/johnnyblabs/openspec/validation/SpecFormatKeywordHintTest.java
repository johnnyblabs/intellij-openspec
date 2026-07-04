package com.johnnyblabs.openspec.validation;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.johnnyblabs.openspec.integration.OpenSpecIntegrationTestBase;

/**
 * CLI 1.4 parity in SpecFormatInspection: case-insensitive header recognition and the
 * keyword-in-header-only targeted hint with its deterministic quick-fix.
 */
public class SpecFormatKeywordHintTest extends OpenSpecIntegrationTestBase {

    public void testLowercaseHeaderIsRecognizedAsRequirement() {
        myFixture.addFileToProject("openspec/specs/auth/spec.md",
                "# Auth\n\n### requirement: Login support\nThe system SHALL support login.\n\n"
                        + "#### Scenario: Login\n- **WHEN** user submits credentials\n- **THEN** the session starts\n");
        refreshVfs();

        PsiFile file = myFixture.configureByFile("openspec/specs/auth/spec.md");
        ProblemDescriptor[] problems = new SpecFormatInspection().checkFile(file,
                InspectionManager.getInstance(getProject()), false);

        for (ProblemDescriptor p : problems) {
            assertFalse("Lowercase header token must count as a requirement heading (CLI 1.4+ parity)",
                    p.getDescriptionTemplate().contains("should contain at least one"));
        }
    }

    public void testKeywordOnlyInHeaderGetsTargetedHintWithQuickFix() {
        myFixture.addFileToProject("openspec/specs/auth/spec.md",
                "# Auth\n\n### Requirement: The system SHALL support login\nUsers can authenticate with a password.\n\n"
                        + "#### Scenario: Login\n- **WHEN** user submits credentials\n- **THEN** the session starts\n");
        refreshVfs();

        PsiFile file = myFixture.configureByFile("openspec/specs/auth/spec.md");
        ProblemDescriptor[] problems = new SpecFormatInspection().checkFile(file,
                InspectionManager.getInstance(getProject()), false);

        ProblemDescriptor hint = null;
        for (ProblemDescriptor p : problems) {
            if (p.getDescriptionTemplate().contains("move the keyword onto the requirement body line")) {
                hint = p;
            }
        }
        assertNotNull("Keyword-in-header-only must produce the targeted hint", hint);
        assertNotNull("Targeted hint must offer a quick-fix", hint.getFixes());
        assertEquals(1, hint.getFixes().length);

        // Apply the fix: the header's requirement text is inserted as the first body line.
        ((com.intellij.codeInspection.LocalQuickFix) hint.getFixes()[0]).applyFix(getProject(), hint);
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();

        String text = file.getText();
        assertTrue("Fix must insert the keyword-bearing sentence as a body line, got:\n" + text,
                text.contains("### Requirement: The system SHALL support login\n\nThe system SHALL support login"));

        ProblemDescriptor[] after = new SpecFormatInspection().checkFile(file,
                InspectionManager.getInstance(getProject()), false);
        for (ProblemDescriptor p : after) {
            assertFalse("Hint must be resolved after the fix",
                    p.getDescriptionTemplate().contains("move the keyword onto the requirement body line"));
            assertFalse("Generic keyword warning must not appear after the fix",
                    p.getDescriptionTemplate().contains("should contain an RFC 2119 keyword"));
        }
    }

    private void refreshVfs() {
        com.intellij.openapi.vfs.VirtualFile root = myFixture.findFileInTempDir("openspec");
        if (root != null) {
            com.intellij.openapi.vfs.VfsUtil.markDirtyAndRefresh(false, true, true, root);
        }
    }

    public void testKeywordNowhereKeepsGenericWeakWarning() {
        myFixture.addFileToProject("openspec/specs/auth/spec.md",
                "# Auth\n\n### Requirement: Login support\nUsers can authenticate with a password.\n\n"
                        + "#### Scenario: Login\n- **WHEN** user submits credentials\n- **THEN** the session starts\n");
        refreshVfs();

        PsiFile file = myFixture.configureByFile("openspec/specs/auth/spec.md");
        ProblemDescriptor[] problems = new SpecFormatInspection().checkFile(file,
                InspectionManager.getInstance(getProject()), false);

        boolean generic = false;
        for (ProblemDescriptor p : problems) {
            assertFalse("No targeted hint without a keyword in the header",
                    p.getDescriptionTemplate().contains("move the keyword onto the requirement body line"));
            if (p.getDescriptionTemplate().contains("should contain an RFC 2119 keyword")) {
                generic = true;
            }
        }
        assertTrue("Keyword-nowhere keeps the generic warning", generic);
    }
}
