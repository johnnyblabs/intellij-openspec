package com.johnnyblabs.openspec.toolwindow;

import com.johnnyblabs.openspec.toolwindow.SpecTreeModel.TreeNodeType;
import com.johnnyblabs.openspec.util.MarkdownHtmlRenderer;

/**
 * Pure renderer for the Browse preview pane. Classifies the selected node, then renders the
 * <em>source markdown</em> for it — never a reconstruction from the CLI's curated JSON — applying
 * delta operation badges (delta specs only) and requirement anchors.
 *
 * <p>This class is pure of the file read: the caller performs the VFS read off the UI thread and
 * hands the markdown string plus the node's type/path here. Main specs and delta specs are routed
 * through <em>different</em> interpretations and are never conflated — only a delta spec's render
 * runs {@link DeltaBadgeDecorator}. A non-previewable node yields the empty-state placeholder.
 *
 * <p>Everything here is headless-safe (it uses {@link MarkdownHtmlRenderer#render(String)} — the
 * pure commonmark fragment — and never {@code buildThemeStylesheet}/{@code JBUI}); the Swing layer
 * supplies the theme CSS and wraps the fragment.
 */
public final class SpecPreviewRenderer {

    /** Stable marker class on the empty-state fragment, asserted by tests. */
    public static final String EMPTY_STATE_MARKER = "openspec-preview-empty";

    /** How a selected node maps to a rendering interpretation. */
    public enum PreviewKind {
        /** A main capability spec ({@code specs/<cap>/spec.md}) or a requirement within it. */
        MAIN_SPEC,
        /** A change's delta spec ({@code changes/<change>/specs/<cap>/spec.md}). */
        DELTA_SPEC,
        /** A change artifact: {@code proposal.md}, {@code design.md}, or {@code tasks.md}. */
        CHANGE_ARTIFACT,
        /** Nothing previewable — render the empty state. */
        NONE
    }

    private SpecPreviewRenderer() {
    }

    /**
     * Classifies a node by its type and backing file path. A node with no backing markdown file, or
     * whose file is not markdown, is {@link PreviewKind#NONE}.
     */
    public static PreviewKind classify(TreeNodeType type, String filePath) {
        if (type == null || filePath == null || !filePath.endsWith(".md")) {
            return PreviewKind.NONE;
        }
        return switch (type) {
            // A requirement's file path IS its parent spec file — render the spec (and scroll to it).
            case SPEC_DOMAIN, REQUIREMENT -> PreviewKind.MAIN_SPEC;
            case DELTA_SPEC -> PreviewKind.DELTA_SPEC;
            case ARTIFACT, ARTIFACT_DONE -> PreviewKind.CHANGE_ARTIFACT;
            default -> PreviewKind.NONE;
        };
    }

    /**
     * Renders the source markdown for a classified node into an HTML fragment (no {@code html/body}
     * wrapper — the Swing layer adds theme CSS via {@link MarkdownHtmlRenderer#wrapInHtml}). Delta
     * specs are badged; main specs are not. A {@link PreviewKind#NONE} kind (or absent markdown)
     * yields {@link #emptyState()}.
     */
    public static String renderMarkdown(PreviewKind kind, String markdown) {
        if (kind == null || kind == PreviewKind.NONE || markdown == null || markdown.isBlank()) {
            return emptyState();
        }
        String fragment = MarkdownHtmlRenderer.render(markdown);
        if (kind == PreviewKind.DELTA_SPEC) {
            // Delta-only interpretation — badge operation headers. A main spec never takes this path.
            fragment = DeltaBadgeDecorator.decorate(fragment);
        }
        if (kind == PreviewKind.MAIN_SPEC || kind == PreviewKind.DELTA_SPEC) {
            // Anchors let a Requirement selection scroll to its section; a no-op when absent.
            fragment = RequirementAnchors.injectAnchors(fragment);
        }
        return fragment;
    }

    /** The placeholder fragment shown when nothing previewable is selected. */
    public static String emptyState() {
        return "<div class=\"" + EMPTY_STATE_MARKER + "\" style=\"text-align:center; padding-top:40px;\">"
                + "<p>Select a spec, requirement, or change artifact to preview it here.</p>"
                + "</div>";
    }
}
