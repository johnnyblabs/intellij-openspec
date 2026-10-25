package com.johnnyblabs.openspec.toolwindow;

import com.johnnyblabs.openspec.toolwindow.SpecPreviewRenderer.PreviewKind;
import com.johnnyblabs.openspec.toolwindow.SpecTreeModel.TreeNodeType;
import com.johnnyblabs.openspec.util.CliOutputParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Per-node-type routing and the pane's fixed fragments. Asserts on structural markers of the render
 * fragment (before {@code wrapInHtml}, avoiding the theme-CSS/{@code JBUI} headless dependency): every
 * node under a change routes to its change's deltas, a delta spec is badged, and every fragment
 * starts with the header that says what the pane shows.
 */
class SpecPreviewRenderTest {

    private static final String DELTA_SPEC_MD =
            "## ADDED Requirements\n\n### Requirement: Throttle traffic\n"
                    + "The system SHALL enforce a quota.\n";

    private static final String CHANGE_DIR = "/p/openspec/changes/demo-change";

    private static final String HEADER_START = "<div class=\"" + SpecPreviewRenderer.HEADER_MARKER + "\"><h1>";

    /** The {@code specs} artifact's {@code outputPath}, from captured {@code status --json} output. */
    static String capturedSpecsOutputPath() {
        try (InputStream is = SpecPreviewRenderTest.class
                .getResourceAsStream("/fixtures/cli/status-with-context.json")) {
            String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            return CliOutputParser.parseChangeStatus(json).getArtifacts().stream()
                    .filter(a -> "specs".equals(a.id())).findFirst().orElseThrow().outputPath();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void deltaSpecRendersBadgeAndAnchor() {
        String fragment = SpecPreviewRenderer.renderMarkdown(PreviewKind.DELTA_SPEC, DELTA_SPEC_MD);
        assertTrue(fragment.contains(DeltaBadgeDecorator.BADGE_CLASS),
                "a delta spec's operation header must be badged");
        assertTrue(fragment.contains("name=\"" + RequirementAnchors.anchorId("Throttle traffic") + "\""),
                "a delta spec carries the requirement anchor for scroll targeting");
    }

    @Test
    void nonDeltaKindOrBlankMarkdownYieldsEmptyState() {
        assertTrue(SpecPreviewRenderer.renderMarkdown(PreviewKind.NONE, DELTA_SPEC_MD)
                .contains(SpecPreviewRenderer.EMPTY_STATE_MARKER));
        assertTrue(SpecPreviewRenderer.renderMarkdown(PreviewKind.DELTA_SPEC, "")
                .contains(SpecPreviewRenderer.EMPTY_STATE_MARKER), "blank markdown → empty state");
    }

    @Test
    void everyArtifactUnderAChangeClassifiesAsItsChangesDeltas() {
        for (TreeNodeType type : new TreeNodeType[]{TreeNodeType.ARTIFACT, TreeNodeType.ARTIFACT_DONE,
                TreeNodeType.ARTIFACT_READY, TreeNodeType.ARTIFACT_BLOCKED}) {
            assertEquals(PreviewKind.CHANGE_DELTAS,
                    SpecPreviewRenderer.classify(type, CHANGE_DIR + "/proposal.md", CHANGE_DIR), type.name());
        }
        // A no-CLI fallback artifact that doesn't exist yet has no path at all.
        assertEquals(PreviewKind.CHANGE_DELTAS,
                SpecPreviewRenderer.classify(TreeNodeType.MISSING_ARTIFACT, null, CHANGE_DIR));
        // Globs: the captured specs artifact, and one outside specs/.
        assertEquals(PreviewKind.CHANGE_DELTAS, SpecPreviewRenderer.classify(
                TreeNodeType.ARTIFACT_READY, CHANGE_DIR + "/" + capturedSpecsOutputPath(), CHANGE_DIR));
        assertEquals(PreviewKind.CHANGE_DELTAS, SpecPreviewRenderer.classify(
                TreeNodeType.ARTIFACT_DONE, CHANGE_DIR + "/notes/**/*.md", CHANGE_DIR));
    }

    @Test
    void classifyRoutesOtherNodeTypes() {
        assertEquals(PreviewKind.DELTA_SPEC, SpecPreviewRenderer.classify(
                TreeNodeType.DELTA_SPEC, CHANGE_DIR + "/specs/s/spec.md", CHANGE_DIR));
        // A change node backs a DIRECTORY; every rollup state previews identically.
        assertEquals(PreviewKind.CHANGE_DELTAS, SpecPreviewRenderer.classify(TreeNodeType.CHANGE, CHANGE_DIR, null));
        assertEquals(PreviewKind.CHANGE_DELTAS,
                SpecPreviewRenderer.classify(TreeNodeType.CHANGE_ARCHIVE_READY, CHANGE_DIR, null));
        assertEquals(PreviewKind.NONE, SpecPreviewRenderer.classify(TreeNodeType.CHANGE, null, null));
        // An artifact whose change is unknown, absent paths and non-previewable types collapse to NONE.
        assertEquals(PreviewKind.NONE, SpecPreviewRenderer.classify(TreeNodeType.ARTIFACT_DONE, "/x/proposal.md", null));
        assertEquals(PreviewKind.NONE, SpecPreviewRenderer.classify(TreeNodeType.DELTA_SPEC, null, CHANGE_DIR));
        assertEquals(PreviewKind.NONE, SpecPreviewRenderer.classify(TreeNodeType.HINT, "/s/spec.md", null));
        assertEquals(PreviewKind.NONE, SpecPreviewRenderer.classify(null, "/s/spec.md", null));
    }

    @Test
    void everyFragmentStartsWithTheHeader() {
        String purpose = SpecPreviewRenderer.PURPOSE_LINE;
        String empty = SpecPreviewRenderer.emptyState(true);
        assertTrue(empty.startsWith(HEADER_START + "Change Deltas</h1>"), "nothing selected: header without a change");
        String cli = SpecPreviewRenderer.cliUnavailablePlaceholder("add-auth");
        assertTrue(cli.startsWith(HEADER_START + "Change Deltas — add-auth</h1>"), "CLI unavailable names the change");
        assertTrue(cli.contains(SpecPreviewRenderer.CLI_UNAVAILABLE_MARKER));
        assertTrue(empty.contains(purpose) && cli.contains(purpose), "every header carries the purpose line");
    }

    @Test
    void emptyStateExplainsThePaneAndOffersProposeOnlyWithoutChanges() {
        String withChanges = SpecPreviewRenderer.emptyState(true);
        String noChanges = SpecPreviewRenderer.emptyState(false);
        for (String empty : new String[]{withChanges, noChanges}) {
            assertTrue(empty.contains(SpecPreviewRenderer.EMPTY_STATE_MARKER));
            assertTrue(empty.contains("A change is a proposal"), "explains what a change is");
            assertTrue(empty.contains("Single-click a change") && empty.contains("Double-click a file"),
                    "says how to use the pane");
        }
        assertTrue(noChanges.contains("href=\"" + SpecPreviewRenderer.PROPOSE_HREF + "\""),
                "with no changes, the empty state offers Propose");
        assertFalse(withChanges.contains(SpecPreviewRenderer.PROPOSE_HREF),
                "with changes, there is no Propose link");
    }

    @Test
    void emptyStateOffersExploreBesideProposeOnlyWithoutChanges() {
        String noChanges = SpecPreviewRenderer.emptyState(false);
        int propose = noChanges.indexOf("href=\"" + SpecPreviewRenderer.PROPOSE_HREF + "\"");
        int explore = noChanges.indexOf("href=\"" + SpecPreviewRenderer.EXPLORE_HREF + "\"");

        assertTrue(propose >= 0 && explore > propose, "Explore comes right after Propose: " + noChanges);
        assertTrue(noChanges.contains(">Explore an idea first</a>"), noChanges);
        assertFalse(SpecPreviewRenderer.emptyState(true).contains(SpecPreviewRenderer.EXPLORE_HREF),
                "with changes, there is no Explore link either");
    }

    @Test
    void openLinkSitsDirectlyUnderTheHeader() {
        String deltas = SpecPreviewRenderer.header("c") + "<p class=\"summary\">1 capability</p><h2>auth</h2>";
        String linked = SpecPreviewRenderer.withOpenLink(deltas, "proposal.md");
        String link = "<p class=\"" + SpecPreviewRenderer.OPEN_LINK_CLASS + "\"><a href=\"openspec-open:proposal.md\">"
                + "Open proposal.md in editor</a></p>";
        assertEquals(SpecPreviewRenderer.header("c") + link + "<p class=\"summary\">1 capability</p><h2>auth</h2>", linked);
        assertSame(deltas, SpecPreviewRenderer.withOpenLink(deltas, null), "no path, no link");
    }

    @Test
    void openHrefParsesChangeRelativePathsOnly() {
        assertEquals("proposal.md", SpecPreviewRenderer.openFileFromHref("openspec-open:proposal.md"));
        assertEquals("specs/auth/spec.md", SpecPreviewRenderer.openFileFromHref("openspec-open:specs/auth/spec.md"));
        assertNull(SpecPreviewRenderer.openFileFromHref("openspec-open:../other/proposal.md"));
        assertNull(SpecPreviewRenderer.openFileFromHref("openspec-open:specs/../../x.md"));
        assertNull(SpecPreviewRenderer.openFileFromHref("openspec-open:/etc/x.md"));
        assertNull(SpecPreviewRenderer.openFileFromHref("openspec-open:C:\\x.md"));
        assertNull(SpecPreviewRenderer.openFileFromHref("openspec-open:"));
        assertNull(SpecPreviewRenderer.openFileFromHref("openspec-diff:auth"));
        assertNull(SpecPreviewRenderer.openFileFromHref(null));
    }
}
