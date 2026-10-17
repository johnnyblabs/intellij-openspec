package com.johnnyblabs.openspec.toolwindow;

import com.johnnyblabs.openspec.toolwindow.SpecPreviewRenderer.PreviewKind;
import com.johnnyblabs.openspec.toolwindow.SpecTreeModel.TreeNodeType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Per-node-type rendering. Asserts on structural markers of the render fragment (before {@code
 * wrapInHtml}, avoiding the theme-CSS/{@code JBUI} headless dependency): a main spec renders its
 * requirement heading and is NOT badged; a delta spec IS badged; the SAME source routed as delta vs
 * main yields DIFFERENT output (the never-conflate guarantee); a non-previewable node yields the
 * empty-state marker.
 */
class SpecPreviewRenderTest {

    private static final String MAIN_SPEC_MD =
            "# Gateway\n\n## Requirements\n\n### Requirement: Throttle traffic\n"
                    + "The system SHALL enforce a quota.\n";

    private static final String DELTA_SPEC_MD =
            "## ADDED Requirements\n\n### Requirement: Throttle traffic\n"
                    + "The system SHALL enforce a quota.\n";

    @Test
    void mainSpecRendersRequirementHeadingWithoutBadge() {
        String fragment = SpecPreviewRenderer.renderMarkdown(PreviewKind.MAIN_SPEC, MAIN_SPEC_MD);
        assertTrue(fragment.contains("Throttle traffic"), "requirement heading text must render");
        assertFalse(fragment.contains(DeltaBadgeDecorator.BADGE_CLASS),
                "a main spec must never carry a delta operation badge");
    }

    @Test
    void deltaSpecRendersBadge() {
        String fragment = SpecPreviewRenderer.renderMarkdown(PreviewKind.DELTA_SPEC, DELTA_SPEC_MD);
        assertTrue(fragment.contains(DeltaBadgeDecorator.BADGE_CLASS),
                "a delta spec's operation header must be badged");
    }

    @Test
    void sameSourceRoutedAsDeltaVsMainDiffers() {
        // A source that contains a delta operation header: as a delta it is badged, as a main spec
        // it is not — so the two interpretations must not produce identical output.
        String asDelta = SpecPreviewRenderer.renderMarkdown(PreviewKind.DELTA_SPEC, DELTA_SPEC_MD);
        String asMain = SpecPreviewRenderer.renderMarkdown(PreviewKind.MAIN_SPEC, DELTA_SPEC_MD);
        assertNotEquals(asMain, asDelta, "delta and main interpretations must never be conflated");
        assertTrue(asDelta.contains(DeltaBadgeDecorator.BADGE_CLASS));
        assertFalse(asMain.contains(DeltaBadgeDecorator.BADGE_CLASS));
    }

    @Test
    void mainSpecInjectsRequirementAnchor() {
        String fragment = SpecPreviewRenderer.renderMarkdown(PreviewKind.MAIN_SPEC, MAIN_SPEC_MD);
        assertTrue(fragment.contains("name=\"" + RequirementAnchors.anchorId("Throttle traffic") + "\""),
                "a main spec must carry the requirement anchor for scroll targeting");
    }

    @Test
    void nonPreviewableYieldsEmptyStateMarker() {
        assertTrue(SpecPreviewRenderer.renderMarkdown(PreviewKind.NONE, MAIN_SPEC_MD)
                .contains(SpecPreviewRenderer.EMPTY_STATE_MARKER));
        assertTrue(SpecPreviewRenderer.renderMarkdown(PreviewKind.MAIN_SPEC, "")
                .contains(SpecPreviewRenderer.EMPTY_STATE_MARKER), "blank markdown → empty state");
    }

    @Test
    void emptyStatePlaceholderNamesOnlyChanges() {
        // Post-restructure the tree has no spec/requirement nodes — only selecting a change populates
        // the pane, so the placeholder must name only "a change" (regression guard for the stale copy
        // that still promised spec/requirement/artifact selection).
        String empty = SpecPreviewRenderer.emptyState();
        assertTrue(empty.contains(SpecPreviewRenderer.EMPTY_STATE_MARKER));
        assertTrue(empty.contains("Select a change"), "placeholder must name the real trigger");
        assertFalse(empty.toLowerCase().contains("requirement"),
                "placeholder must not mention the retired requirement node type");
    }

    @Test
    void classifyRoutesNodeTypesToTheRightKind() {
        // Plain-spec (SPEC_DOMAIN/REQUIREMENT) node types are retired — the tree no longer holds them
        // and classify no longer routes to MAIN_SPEC (specs open in the editor). The surviving
        // change-scoped kinds still route correctly.
        assertEquals(PreviewKind.DELTA_SPEC, SpecPreviewRenderer.classify(TreeNodeType.DELTA_SPEC, "/c/specs/s/spec.md"));
        assertEquals(PreviewKind.CHANGE_ARTIFACT, SpecPreviewRenderer.classify(TreeNodeType.ARTIFACT, "/c/proposal.md"));
        assertEquals(PreviewKind.CHANGE_ARTIFACT, SpecPreviewRenderer.classify(TreeNodeType.ARTIFACT_DONE, "/c/design.md"));
        // A change node backs a DIRECTORY (not a .md file) — it routes to the consolidated deltas
        // view, recognized before the markdown-file guard would collapse it to NONE.
        assertEquals(PreviewKind.CHANGE_DELTAS,
                SpecPreviewRenderer.classify(TreeNodeType.CHANGE, "/proj/openspec/changes/my-change"));
        assertEquals(PreviewKind.NONE, SpecPreviewRenderer.classify(TreeNodeType.CHANGE, null));
        // Every change rollup state previews identically, including archive-ready.
        assertEquals(PreviewKind.CHANGE_DELTAS,
                SpecPreviewRenderer.classify(TreeNodeType.CHANGE_ARCHIVE_READY, "/proj/openspec/changes/my-change"));
        assertEquals(PreviewKind.NONE, SpecPreviewRenderer.classify(TreeNodeType.CHANGE_ARCHIVE_READY, null));
        // Absent path and non-previewable types collapse to NONE.
        assertEquals(PreviewKind.NONE, SpecPreviewRenderer.classify(TreeNodeType.DELTA_SPEC, null));
        assertEquals(PreviewKind.NONE, SpecPreviewRenderer.classify(TreeNodeType.HINT, "/s/spec.md"));
    }
}
