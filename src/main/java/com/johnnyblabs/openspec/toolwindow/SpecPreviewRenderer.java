package com.johnnyblabs.openspec.toolwindow;

import com.johnnyblabs.openspec.model.ChangeDeltaModel;
import com.johnnyblabs.openspec.model.ChangeDeltaModel.CapabilityGroup;
import com.johnnyblabs.openspec.model.ChangeDeltaModel.Delta;
import com.johnnyblabs.openspec.model.ChangeDeltaModel.Rename;
import com.johnnyblabs.openspec.model.ChangeDeltaModel.Requirement;
import com.johnnyblabs.openspec.model.ChangeDeltaModel.Scenario;
import com.johnnyblabs.openspec.model.DeltaSpecOperation.OperationType;
import com.johnnyblabs.openspec.toolwindow.SpecTreeModel.TreeNodeType;
import com.johnnyblabs.openspec.util.MarkdownHtmlRenderer;

import java.util.List;

/**
 * Pure renderer for the Browse preview pane. Classifies the selected node, then renders the
 * <em>source markdown</em> for it — never a reconstruction from the CLI's curated JSON — applying
 * delta operation badges (delta specs only) and requirement anchors. One exception is on-model: a
 * <b>change</b> node renders a consolidated {@code CHANGE_DELTAS} view assembled from the CLI's
 * {@code show <change> --type change --json} output (see {@link #renderChangeDeltas}), because the
 * CLI — not the plugin — owns the cross-capability delta assembly.
 *
 * <p>This class is pure of the file read and of the CLI call: the caller performs the VFS read / CLI
 * spawn off the UI thread and hands the resulting string here. Main specs and delta specs are routed
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

    /**
     * Stable marker class on the CLI-unavailable placeholder — DISTINCT from {@link
     * #EMPTY_STATE_MARKER} so "no CLI" (can't assemble) and "no deltas" (assembled, empty) are never
     * indistinguishable.
     */
    public static final String CLI_UNAVAILABLE_MARKER = "openspec-preview-cli-unavailable";

    /** Wraps each rendered requirement body in the consolidated deltas view. */
    public static final String DELTA_REQUIREMENT_CLASS = "openspec-delta-requirement";
    /** The from→to line rendered for a RENAMED delta (carries no requirement body, no op badge). */
    public static final String DELTA_RENAME_CLASS = "openspec-delta-rename";
    /** The per-capability diff cross-link paragraph. */
    public static final String DELTA_DIFF_LINK_CLASS = "openspec-diff-link";
    /** The one-line summary-counts paragraph under the deltas header. */
    public static final String DELTA_SUMMARY_CLASS = "openspec-delta-summary";

    /** How a selected node maps to a rendering interpretation. */
    public enum PreviewKind {
        /** A main capability spec ({@code specs/<cap>/spec.md}) or a requirement within it. */
        MAIN_SPEC,
        /** A change's delta spec ({@code changes/<change>/specs/<cap>/spec.md}). */
        DELTA_SPEC,
        /** A change artifact: {@code proposal.md}, {@code design.md}, or {@code tasks.md}. */
        CHANGE_ARTIFACT,
        /** A change node — render its consolidated, CLI-sourced deltas ({@link #renderChangeDeltas}). */
        CHANGE_DELTAS,
        /** Nothing previewable — render the empty state. */
        NONE
    }

    private SpecPreviewRenderer() {
    }

    /**
     * Classifies a node by its type and backing file path. A change node's path is the change
     * <em>directory</em> (not a {@code .md} file), so it is recognized BEFORE the markdown-file guard.
     * A node with no backing markdown file, or whose file is not markdown, is {@link PreviewKind#NONE}.
     */
    public static PreviewKind classify(TreeNodeType type, String filePath) {
        // A change node backs a directory, not a .md file — route it before the markdown-file guard.
        // CHANGE_DONE is a change node too (apply-ready rollup badge), so it previews identically.
        if ((type == TreeNodeType.CHANGE || type == TreeNodeType.CHANGE_DONE) && filePath != null) {
            return PreviewKind.CHANGE_DELTAS;
        }
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
     * yields {@link #emptyState()}. {@link PreviewKind#CHANGE_DELTAS} is NOT rendered here — it is
     * assembled from CLI JSON via {@link #renderChangeDeltas} on a separate pipeline path.
     */
    public static String renderMarkdown(PreviewKind kind, String markdown) {
        if (kind == null || kind == PreviewKind.NONE || kind == PreviewKind.CHANGE_DELTAS
                || markdown == null || markdown.isBlank()) {
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

    /**
     * Assembles a change's CLI-sourced deltas into an HTML fragment: a header ({@code Deltas —
     * <change>}) and one-line summary counts, then each capability ({@code <h2>}, plugin-imposed
     * alphabetical order) with a diff cross-link, then per operation an {@code <h3>} keyword header
     * (badged by {@link DeltaBadgeDecorator}) over its requirement bodies and scenarios. RENAMED
     * renders a from→to line with NO requirement body and NO operation badge. An empty model yields
     * {@link #EMPTY_STATE_MARKER}. Pure and headless — {@link MarkdownHtmlRenderer#render} only.
     */
    public static String renderChangeDeltas(ChangeDeltaModel model) {
        if (model == null || model.deltas().isEmpty()) {
            return changeDeltasEmptyState();
        }

        StringBuilder sb = new StringBuilder();
        String heading = (model.title() == null || model.title().isBlank()) ? model.id() : model.title();
        sb.append("<h1>Deltas — ").append(escape(heading)).append("</h1>");

        int caps = model.capabilityCount();
        sb.append("<p class=\"").append(DELTA_SUMMARY_CLASS).append("\">")
                .append(caps).append(caps == 1 ? " capability" : " capabilities")
                .append(" · ").append(model.operationCount(OperationType.ADDED)).append(" ADDED")
                .append(" · ").append(model.operationCount(OperationType.MODIFIED)).append(" MODIFIED")
                .append(" · ").append(model.operationCount(OperationType.REMOVED)).append(" REMOVED")
                .append(" · ").append(model.operationCount(OperationType.RENAMED)).append(" RENAMED")
                .append("</p>");

        for (CapabilityGroup group : model.groupedByCapability()) {
            sb.append("<h2>").append(escape(group.capability())).append("</h2>");
            // Diff cross-link — resolved by the pane's HyperlinkListener to DeltaSpecDiffAction.
            sb.append("<p class=\"").append(DELTA_DIFF_LINK_CLASS).append("\"><a href=\"")
                    .append(escapeAttr(DeltaDiffAnchor.diffAnchorHref(group.capability())))
                    .append("\">Preview diff vs current main spec</a></p>");

            // Requirement operations first, in the fixed ADDED → MODIFIED → REMOVED order; within an
            // operation the CLI's authored order is preserved (stream keeps encounter order).
            for (OperationType op : List.of(OperationType.ADDED, OperationType.MODIFIED, OperationType.REMOVED)) {
                List<Delta> ofOp = group.deltas().stream().filter(d -> d.operation() == op).toList();
                if (ofOp.isEmpty()) {
                    continue;
                }
                sb.append("<h3>").append(op.name()).append("</h3>");
                for (Delta d : ofOp) {
                    appendRequirementBody(sb, d.requirement());
                }
            }

            // RENAMED last: a from→to line, no requirement body, no op-keyword header (so no badge).
            for (Delta d : group.deltas()) {
                if (d.operation() == OperationType.RENAMED) {
                    appendRename(sb, d.rename());
                }
            }
        }

        String fragment = sb.toString();
        fragment = DeltaBadgeDecorator.decorate(fragment);
        fragment = RequirementAnchors.injectAnchors(fragment);
        return fragment;
    }

    private static void appendRequirementBody(StringBuilder sb, Requirement requirement) {
        sb.append("<div class=\"").append(DELTA_REQUIREMENT_CLASS).append("\">");
        if (requirement != null) {
            sb.append(MarkdownHtmlRenderer.render(requirement.text()));
            for (Scenario scenario : requirement.scenarios()) {
                sb.append("<div class=\"openspec-delta-scenario\">")
                        .append(MarkdownHtmlRenderer.render(scenario.rawText()))
                        .append("</div>");
            }
        }
        sb.append("</div>");
    }

    private static void appendRename(StringBuilder sb, Rename rename) {
        String from = rename != null ? rename.from() : "";
        String to = rename != null ? rename.to() : "";
        sb.append("<p class=\"").append(DELTA_RENAME_CLASS).append("\">Renamed: <code>")
                .append(escape(from)).append("</code> → <code>").append(escape(to)).append("</code></p>");
    }

    /** The placeholder fragment shown when nothing previewable is selected. */
    public static String emptyState() {
        return "<div class=\"" + EMPTY_STATE_MARKER + "\" style=\"text-align:center; padding-top:40px;\">"
                + "<p>Select a spec, requirement, change artifact, or a change to see its consolidated deltas.</p>"
                + "</div>";
    }

    /** Empty state for a change that has no spec-level deltas — informative, not an error. */
    private static String changeDeltasEmptyState() {
        return "<div class=\"" + EMPTY_STATE_MARKER + "\" style=\"text-align:center; padding-top:40px;\">"
                + "<p>This change has no spec-level deltas.</p>"
                + "</div>";
    }

    /**
     * Placeholder shown when a change node is selected but the OpenSpec CLI is unavailable, so the
     * consolidated deltas can't be assembled on-model. Carries {@link #CLI_UNAVAILABLE_MARKER}.
     */
    public static String cliUnavailablePlaceholder() {
        return "<div class=\"" + CLI_UNAVAILABLE_MARKER + "\" style=\"text-align:center; padding-top:40px;\">"
                + "<p>Consolidated deltas require the OpenSpec CLI. Install or configure the CLI to view "
                + "a change's assembled deltas; individual delta-spec files still preview without it.</p>"
                + "</div>";
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String escapeAttr(String s) {
        return escape(s).replace("\"", "&quot;");
    }
}
