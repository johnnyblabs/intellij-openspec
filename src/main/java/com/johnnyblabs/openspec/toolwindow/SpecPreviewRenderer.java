package com.johnnyblabs.openspec.toolwindow;

import com.johnnyblabs.openspec.model.ChangeDeltaModel;
import com.johnnyblabs.openspec.model.ChangeDeltaModel.CapabilityGroup;
import com.johnnyblabs.openspec.model.ChangeDeltaModel.Delta;
import com.johnnyblabs.openspec.model.ChangeDeltaModel.Rename;
import com.johnnyblabs.openspec.model.ChangeDeltaModel.Requirement;
import com.johnnyblabs.openspec.model.ChangeDeltaModel.Scenario;
import com.johnnyblabs.openspec.model.DeltaSpecOperation.OperationType;
import com.johnnyblabs.openspec.toolwindow.SpecTreeModel.TreeNodeType;
import com.johnnyblabs.openspec.util.CliJson;
import com.johnnyblabs.openspec.util.CliRunner;
import com.johnnyblabs.openspec.util.CliText;
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

    /** Stable marker class on the header that starts every preview fragment. */
    public static final String HEADER_MARKER = "openspec-preview-header";

    /** The one line under every header saying what the pane is for. */
    static final String PURPOSE_LINE =
            "What this change will add, modify or remove in your specs when it's archived.";

    /** The open-in-editor link inserted under the header for a single-file artifact. */
    public static final String OPEN_LINK_CLASS = "openspec-open-link";
    /** Href scheme of the open-in-editor link; the rest is the file path relative to the change directory. */
    static final String OPEN_HREF_PREFIX = "openspec-open:";
    /** Href of the empty state's Propose link. */
    public static final String PROPOSE_HREF = "openspec-action:propose";

    /** Stable marker class on the empty-state fragment, asserted by tests. */
    public static final String EMPTY_STATE_MARKER = "openspec-preview-empty";

    /**
     * Stable marker class on the CLI-unavailable placeholder — DISTINCT from {@link
     * #EMPTY_STATE_MARKER} so "no CLI" (can't assemble) and "no deltas" (assembled, empty) are never
     * indistinguishable.
     */
    public static final String CLI_UNAVAILABLE_MARKER = "openspec-preview-cli-unavailable";

    /**
     * Stable marker class on the placeholder shown when the CLI ran but couldn't read the change — DISTINCT from
     * {@link #CLI_UNAVAILABLE_MARKER}, because the user doesn't need to install anything.
     */
    public static final String CLI_REJECTED_MARKER = "openspec-preview-cli-rejected";

    /** A warning CLI 1.3–1.5 print on stderr for every change {@code show}, successful or not; never a reason. */
    private static final String IGNORED_FLAGS_WARNING = "Warning: Ignoring flags not applicable";

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
        /** A change's delta spec ({@code changes/<change>/specs/<cap>/spec.md}). */
        DELTA_SPEC,
        /**
         * A change node or any artifact under it — render the change's consolidated, CLI-sourced deltas
         * ({@link #renderChangeDeltas}).
         */
        CHANGE_DELTAS,
        /** Nothing previewable — render the empty state. */
        NONE
    }

    private SpecPreviewRenderer() {
    }

    /**
     * Classifies a node by its type, backing file path and enclosing change directory. A change node,
     * and any artifact under a change (whatever its status, and whether or not its path is a file),
     * previews the change's deltas. A delta spec renders its own markdown. Anything else, including an
     * artifact whose change is unknown, is {@link PreviewKind#NONE}.
     */
    public static PreviewKind classify(TreeNodeType type, String filePath, String changeDir) {
        if (type == null) {
            return PreviewKind.NONE;
        }
        // A change node backs a directory, not a .md file. Every change rollup state previews identically.
        if (type.isChange()) {
            return filePath != null ? PreviewKind.CHANGE_DELTAS : PreviewKind.NONE;
        }
        if (isArtifact(type)) {
            return changeDir != null ? PreviewKind.CHANGE_DELTAS : PreviewKind.NONE;
        }
        if (type == TreeNodeType.DELTA_SPEC && filePath != null && filePath.endsWith(".md")) {
            return PreviewKind.DELTA_SPEC;
        }
        return PreviewKind.NONE;
    }

    private static boolean isArtifact(TreeNodeType type) {
        return switch (type) {
            case ARTIFACT, ARTIFACT_DONE, ARTIFACT_READY, ARTIFACT_BLOCKED, MISSING_ARTIFACT -> true;
            default -> false;
        };
    }

    /** Whether an artifact path is a glob (the CLI's {@code outputPath} for a multi-file artifact), never a file. */
    static boolean isGlob(String path) {
        return path.indexOf('*') >= 0 || path.indexOf('?') >= 0 || path.indexOf('[') >= 0;
    }

    /**
     * Renders the source markdown for a classified node into an HTML fragment (no {@code html/body}
     * wrapper — the Swing layer adds theme CSS via {@link MarkdownHtmlRenderer#wrapInHtml}). Only a
     * delta spec renders here, badged. Any other kind (or absent markdown) yields the empty state. {@link PreviewKind#CHANGE_DELTAS} is NOT rendered here — it is
     * assembled from CLI JSON via {@link #renderChangeDeltas} on a separate pipeline path.
     */
    public static String renderMarkdown(PreviewKind kind, String markdown) {
        if (kind != PreviewKind.DELTA_SPEC || markdown == null || markdown.isBlank()) {
            return emptyState(true);
        }
        // Delta-only interpretation — badge operation headers; anchors let a requirement scroll into view.
        String fragment = DeltaBadgeDecorator.decorate(MarkdownHtmlRenderer.render(markdown));
        return RequirementAnchors.injectAnchors(fragment);
    }

    /**
     * Assembles a change's CLI-sourced deltas into an HTML fragment: the {@link #header} naming the
     * change id, one-line summary counts, then each capability ({@code <h2>}, plugin-imposed
     * alphabetical order) with a diff cross-link, then per operation an {@code <h3>} keyword header
     * (badged by {@link DeltaBadgeDecorator}) over its requirement bodies and scenarios. RENAMED
     * renders a from→to line with NO requirement body and NO operation badge. An empty model yields
     * {@link #EMPTY_STATE_MARKER}. Pure and headless — {@link MarkdownHtmlRenderer#render} only.
     */
    public static String renderChangeDeltas(ChangeDeltaModel model) {
        if (model == null || model.deltas().isEmpty()) {
            return changeDeltasEmptyState(model != null ? model.id() : null);
        }

        // The header names the change id, not the proposal title, so every state of a change reads the same.
        StringBuilder sb = new StringBuilder(header(model.id()));

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

    /**
     * The header that starts every preview fragment: "Change Deltas", the change's name when one is
     * selected, and the one-line purpose. Carries {@link #HEADER_MARKER}.
     */
    public static String header(String changeName) {
        String title = (changeName == null || changeName.isBlank())
                ? "Change Deltas" : "Change Deltas — " + escape(changeName);
        return "<div class=\"" + HEADER_MARKER + "\"><h1>" + title + "</h1><p>" + PURPOSE_LINE + "</p></div>";
    }

    /**
     * Spacing for the header and the placeholder states: Swing's HTML renderer gives their paragraphs
     * no gap, so in the narrow pane they would read as one block.
     */
    public static String paneCss() {
        return "." + HEADER_MARKER + " { margin-bottom: 10px; }"
                + "." + HEADER_MARKER + " p, ." + EMPTY_STATE_MARKER + " p, ." + CLI_UNAVAILABLE_MARKER + " p, ."
                + CLI_REJECTED_MARKER + " p, ."
                + OPEN_LINK_CLASS + " { margin-top: 0; margin-bottom: 8px; }";
    }

    /**
     * The fragment shown when nothing previewable is selected. It explains what the pane shows and how
     * to use it, and offers Propose only when the project has no active changes.
     */
    public static String emptyState(boolean hasChanges) {
        StringBuilder sb = new StringBuilder(header(null));
        sb.append("<div class=\"").append(EMPTY_STATE_MARKER).append("\">")
                .append("<p>A change is a proposal to add, modify or remove requirements in your specs. ")
                .append("This pane shows a change's deltas: every requirement it changes, grouped by capability.</p>")
                .append("<p>Single-click a change, or any of its files, to see its deltas. ")
                .append("Double-click a file to open it in the editor.</p>");
        if (!hasChanges) {
            sb.append("<p><a href=\"").append(PROPOSE_HREF).append("\">Propose a change</a></p>");
        }
        return sb.append("</div>").toString();
    }

    /** Empty state for a change that has no spec-level deltas — informative, not an error. */
    private static String changeDeltasEmptyState(String changeName) {
        return header(changeName)
                + "<div class=\"" + EMPTY_STATE_MARKER + "\"><p>This change has no spec-level deltas yet.</p></div>";
    }

    /**
     * Placeholder shown when a change (or one of its artifacts) is selected but the OpenSpec CLI is
     * unavailable, so the consolidated deltas can't be assembled on-model. Carries {@link #CLI_UNAVAILABLE_MARKER}.
     */
    public static String cliUnavailablePlaceholder(String changeName) {
        return header(changeName)
                + "<div class=\"" + CLI_UNAVAILABLE_MARKER + "\">"
                + "<p>Consolidated deltas require the OpenSpec CLI. Install or configure the CLI to view "
                + "a change's assembled deltas; individual delta-spec files still preview without it.</p>"
                + "</div>";
    }

    /**
     * The fragment for a change the OpenSpec CLI ran for but couldn't read (a renamed change, no proposal yet, an
     * unreadable answer). Quotes the CLI's reason and never says to install the CLI. Carries
     * {@link #CLI_REJECTED_MARKER}.
     */
    public static String cliRejectedPlaceholder(String changeName, String reason) {
        return header(changeName)
                + "<div class=\"" + CLI_REJECTED_MARKER + "\">"
                + "<p><b>Can't read this change.</b> The OpenSpec CLI said: " + escapeAttr(reason) + "</p>"
                + "<p>Fix the change, then select it again.</p>"
                + "</div>";
    }

    /**
     * The CLI's reason from a failed run, for the user: an error envelope's message and fix as given; otherwise the
     * stderr-derived message without the "Ignoring flags" warning and without the {@code ✖ Error:} / {@code Error:}
     * prefix CLI 1.3 puts before it. Falls back to the message as given when nothing is left.
     */
    public static String cliReason(CliJson.Failed failed) {
        if (failed.kind() == CliJson.Kind.ENVELOPE) {
            return failed.describe();
        }
        StringBuilder reason = new StringBuilder();
        for (String line : CliText.stripAnsi(failed.message()).split("\\R")) {
            String text = line.strip();
            if (text.isEmpty() || text.startsWith(IGNORED_FLAGS_WARNING)) continue;
            text = text.replaceFirst("^\u2716\\s*", "").replaceFirst("^Error:\\s*", "");
            if (text.isEmpty()) continue;
            if (reason.length() > 0) reason.append(' ');
            reason.append(text);
        }
        return reason.length() > 0 ? reason.toString() : failed.message();
    }

    /** What the deltas preview shows for one change: its deltas, or one of the two placeholders. */
    public enum ChangeShowKind {DELTAS, REJECTED, UNAVAILABLE}

    /** The deltas preview's fragment for a change, and which kind it is. */
    public record ChangeShowOutcome(ChangeShowKind kind, String fragment) {
        /**
         * Whether the preview may cache this fragment. Only deltas are cached: a rejection or a missing CLI can be
         * fixed a moment later (write the proposal, restore the directory, install the CLI), and reselecting the change
         * must ask again.
         */
        public boolean cacheable() {
            return kind == ChangeShowKind.DELTAS;
        }
    }

    /**
     * Turns one run of {@code openspec show <change> --type change --json} into the preview's fragment. Reads it the
     * way every CLI caller does ({@link CliJson#read}); stdout only, since a successful run can still print a warning
     * on stderr. Any failure the CLI reports, or an answer the plugin can't read, is
     * {@link ChangeShowKind#REJECTED}: the CLI is there, so the user must not be told to install it.
     */
    public static ChangeShowOutcome changeShowOutcome(String changeName, CliRunner.CliResult result) {
        return switch (CliJson.read(result)) {
            case CliJson.Ok ok -> {
                try {
                    String fragment = renderChangeDeltas(ChangeDeltaModel.parse(ok.payload().toString()));
                    yield new ChangeShowOutcome(ChangeShowKind.DELTAS, fragment);
                } catch (RuntimeException e) {
                    yield new ChangeShowOutcome(ChangeShowKind.REJECTED, cliRejectedPlaceholder(changeName,
                            "The plugin couldn't read the OpenSpec CLI's answer."));
                }
            }
            case CliJson.Failed failed ->
                    new ChangeShowOutcome(ChangeShowKind.REJECTED, cliRejectedPlaceholder(changeName, cliReason(failed)));
        };
    }

    /**
     * The preview's fragment when running the CLI threw. A timeout ({@link CliRunner.CliException#timedOut()}) means the
     * CLI started but didn't answer, so it is {@link ChangeShowKind#REJECTED} with the timeout as the reason; any other
     * failure to run it is {@link ChangeShowKind#UNAVAILABLE}.
     */
    public static ChangeShowOutcome changeShowOutcome(String changeName, CliRunner.CliException error) {
        if (error.timedOut()) {
            return new ChangeShowOutcome(ChangeShowKind.REJECTED, cliRejectedPlaceholder(changeName, error.getMessage()));
        }
        return new ChangeShowOutcome(ChangeShowKind.UNAVAILABLE, cliUnavailablePlaceholder(changeName));
    }

    /**
     * Inserts an "Open {@code <file>} in editor" link directly after the fragment's header. The href
     * carries the path relative to the change directory, resolved by {@link #openFileFromHref}.
     */
    public static String withOpenLink(String fragment, String relativePath) {
        if (fragment == null || relativePath == null || relativePath.isBlank()) {
            return fragment;
        }
        String name = relativePath.substring(relativePath.lastIndexOf('/') + 1);
        String link = "<p class=\"" + OPEN_LINK_CLASS + "\"><a href=\""
                + escapeAttr(OPEN_HREF_PREFIX + relativePath) + "\">Open " + escape(name) + " in editor</a></p>";
        String headerEnd = "</p></div>";
        int at = fragment.startsWith("<div class=\"" + HEADER_MARKER + "\">") ? fragment.indexOf(headerEnd) : -1;
        if (at < 0) {
            return link + fragment;
        }
        int cut = at + headerEnd.length();
        return fragment.substring(0, cut) + link + fragment.substring(cut);
    }

    /**
     * The change-relative file path an open-in-editor href names, or null when the href is not one, or
     * names an absolute path or one that climbs out of the change directory.
     */
    public static String openFileFromHref(String href) {
        if (href == null || !href.startsWith(OPEN_HREF_PREFIX)) {
            return null;
        }
        String rel = href.substring(OPEN_HREF_PREFIX.length());
        if (rel.isBlank() || rel.startsWith("/") || rel.startsWith("\\") || rel.matches("^[A-Za-z]:.*")) {
            return null;
        }
        for (String segment : rel.split("[/\\\\]")) {
            if (segment.equals("..")) {
                return null;
            }
        }
        return rel;
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
