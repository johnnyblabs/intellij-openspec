package com.johnnyblabs.openspec.integration;

import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.johnnyblabs.openspec.toolwindow.SpecPreviewRenderer;
import com.johnnyblabs.openspec.toolwindow.SpecTreeModel.TreeNodeType;

/**
 * Covers the preview's file-read contract: resolve a real {@link VirtualFile} for a node's path,
 * read it with {@link VfsUtilCore#loadText} (the same call the panel makes off the UI thread —
 * paths are re-resolved, {@code VirtualFile} handles are never cached), classify it, and render.
 * The rendered HTML must contain the spec's requirement heading. The listener/Alarm/invokeLater hop
 * is intentionally NOT driven here — that wiring is a uiSmoke concern.
 */
public class SpecPreviewFileReadTest extends BasePlatformTestCase {

    public void testMainSpecFileReadRendersRequirementHeading() throws Exception {
        PsiFile psi = myFixture.addFileToProject(
                "openspec/specs/gateway/spec.md",
                "# Gateway\n\n## Requirements\n\n### Requirement: Throttle inbound traffic\n"
                        + "The system SHALL enforce a quota of 100 tokens per client.\n\n"
                        + "#### Scenario: Burst\n- **WHEN** a client exceeds its quota\n- **THEN** it is rejected\n");
        VirtualFile file = psi.getVirtualFile();
        assertNotNull("spec file must exist in the temp project", file);
        String path = file.getPath();

        // Re-resolve by PATH through the file's own filesystem (never cache the handle) — the panel's
        // contract. In production this is LocalFileSystem; the in-memory fixture uses a temp:// VFS,
        // so we go through that file's own filesystem rather than hardcoding LocalFileSystem.
        VirtualFile resolved = file.getFileSystem().findFileByPath(path);
        assertNotNull("path must re-resolve to a VirtualFile", resolved);

        String markdown = VfsUtilCore.loadText(resolved);
        SpecPreviewRenderer.PreviewKind kind = SpecPreviewRenderer.classify(TreeNodeType.SPEC_DOMAIN, path);
        assertEquals(SpecPreviewRenderer.PreviewKind.MAIN_SPEC, kind);

        String fragment = SpecPreviewRenderer.renderMarkdown(kind, markdown);
        assertTrue("rendered HTML must contain the requirement heading text",
                fragment.contains("Throttle inbound traffic"));
        assertFalse("a main spec must not be badged as a delta",
                fragment.contains("openspec-op-badge"));
    }
}
