package com.johnnyblabs.openspec.editor;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.johnnyblabs.openspec.integration.OpenSpecIntegrationTestBase;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests that {@link SpecRefLineMarkerProvider} is language-agnostic. The reference detection is
 * pure text keyed off the base {@link PsiComment} interface, with no language-specific logic, so
 * the same {@code @spec} reference is recognized in any comment style. Lives in the provider's
 * package to exercise the package-private {@code parseSpecRef} detection helper.
 */
public class SpecRefLineMarkerProviderTest extends OpenSpecIntegrationTestBase {

    private final SpecRefLineMarkerProvider provider = new SpecRefLineMarkerProvider();

    // --- detection logic (language-agnostic, no PSI/LineMarkerInfo needed) ---

    public void testParsesReferenceFromSlashComment() {
        String[] ref = SpecRefLineMarkerProvider.parseSpecRef("// @spec actions:Init Action");
        assertNotNull(ref);
        assertEquals("actions", ref[0]);
        assertEquals("Init Action", ref[1]);
    }

    public void testParsesReferenceFromHashComment() {
        // '#'-style comment (Python, Ruby, YAML, shell).
        String[] ref = SpecRefLineMarkerProvider.parseSpecRef("# @spec actions:Propose Action");
        assertNotNull(ref);
        assertEquals("actions", ref[0]);
        assertEquals("Propose Action", ref[1]);
    }

    public void testParsesReferenceFromDashComment() {
        // '--'-style comment (SQL, Haskell, Lua).
        String[] ref = SpecRefLineMarkerProvider.parseSpecRef("-- @spec actions:Init Action");
        assertNotNull(ref);
        assertEquals("Init Action", ref[1]);
    }

    public void testParsesReferenceWithHyphenatedDomain() {
        String[] ref = SpecRefLineMarkerProvider.parseSpecRef("// @spec my-domain:Some Requirement");
        assertNotNull(ref);
        assertEquals("my-domain", ref[0]);
        assertEquals("Some Requirement", ref[1]);
    }

    public void testNoReferenceInPlainComment() {
        assertNull(SpecRefLineMarkerProvider.parseSpecRef("// just an ordinary comment"));
    }

    public void testNullCommentTextYieldsNoReference() {
        assertNull(SpecRefLineMarkerProvider.parseSpecRef(null));
    }

    // --- provider entry point: ignores non-comment / non-@spec elements without a marker ---

    public void testNoMarkerForCommentWithoutSpecReference() {
        PsiComment comment = mock(PsiComment.class);
        when(comment.getProject()).thenReturn(getProject());
        when(comment.getText()).thenReturn("// not a spec reference");
        assertNull(provider.getLineMarkerInfo(comment));
    }

    public void testNoMarkerForNonCommentElement() {
        PsiElement notAComment = mock(PsiElement.class);
        assertNull(provider.getLineMarkerInfo(notAComment));
    }

    // --- end-to-end on a real (non-Java) comment: YAML '#' comment in an OpenSpec project ---

    public void testMarkerAndTooltipForRealYamlComment() {
        PsiFile file = myFixture.configureByText("sample.yaml",
                "# @spec actions:Init Action\nkey: value\n");
        PsiComment comment = PsiTreeUtil.findChildOfType(file, PsiComment.class);
        assertNotNull("YAML '#' line should parse as a PsiComment", comment);

        LineMarkerInfo<?> info = provider.getLineMarkerInfo(comment);
        assertNotNull("a real @spec comment in any language yields a gutter marker", info);
        assertSame("marker is anchored to the comment element", comment, info.getElement());

        String tooltip = info.getLineMarkerTooltip();
        assertNotNull(tooltip);
        assertTrue("tooltip names the domain and requirement",
                tooltip.contains("actions") && tooltip.contains("Init Action"));
    }

    // --- navigation target resolution (branch coverage) ---

    public void testNavigateOpensExistingDomainSpec() {
        VirtualFile specFile = myFixture.findFileInTempDir("openspec/specs/actions/spec.md");
        assertNotNull(specFile);
        SpecRefLineMarkerProvider.navigateToSpec(getProject(), "actions");
        assertTrue("clicking the marker opens the domain's spec.md",
                FileEditorManager.getInstance(getProject()).isFileOpen(specFile));
    }

    public void testNavigateIsNoOpForUnknownDomain() {
        // Must not throw when the domain directory does not exist.
        SpecRefLineMarkerProvider.navigateToSpec(getProject(), "no-such-domain");
    }

    public void testNavigateIsNoOpWhenDomainHasNoSpecFile() {
        // Domain directory exists but contains no spec.md → no-op (specFile == null branch).
        myFixture.addFileToProject("openspec/specs/emptydomain/notes.md", "no spec here\n");
        SpecRefLineMarkerProvider.navigateToSpec(getProject(), "emptydomain");
    }
}