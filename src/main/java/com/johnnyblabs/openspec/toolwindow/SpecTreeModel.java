package com.johnnyblabs.openspec.toolwindow;

import com.intellij.ide.util.treeView.PathElementIdProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.model.*;
import com.johnnyblabs.openspec.services.ArtifactOrchestrationService;
import com.johnnyblabs.openspec.services.ChangeService;
import com.johnnyblabs.openspec.services.CliDetectionService;
import com.johnnyblabs.openspec.settings.OpenSpecSettings;
import com.johnnyblabs.openspec.util.ApplyPromptBuilder;
import com.johnnyblabs.openspec.util.OpenSpecFileUtil;
import com.johnnyblabs.openspec.version.VersionSupport;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class SpecTreeModel {
    private static final Logger LOG = Logger.getInstance(SpecTreeModel.class);

    private final Project project;

    public SpecTreeModel(Project project) {
        this.project = project;
    }

    public DefaultTreeModel buildModel() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("OpenSpec");

        if (!OpenSpecFileUtil.isOpenSpecProject(project)) {
            String hint = "No openspec/ directory found — click Initialize to set up.";
            root.add(new DefaultMutableTreeNode(
                    new TreeNodeData(hint, TreeNodeType.HINT, null, null, null, hint)));
            return new DefaultTreeModel(root);
        }

        // The tool-window tree shows only the model/process surface — the Changes subtree. File
        // navigation (spec files, archived changes, config.yaml) is owned by the Project View, and
        // spec content-search is re-homed to the Search Everywhere contributor — so there is no
        // second file-navigation tree here and no always-on tree filter.
        root.add(buildChangesNode());
        return new DefaultTreeModel(root);
    }

    private DefaultMutableTreeNode buildChangesNode() {
        DefaultMutableTreeNode changesNode = new DefaultMutableTreeNode(
                new TreeNodeData("Changes", TreeNodeType.CHANGES, null, null, null, "Active changes"));

        ChangeService changeService = project.getService(ChangeService.class);
        List<Change> changes = changeService.getActiveChanges();

        if (changes.isEmpty()) {
            String hint = "No active changes — double-click to propose one.";
            changesNode.add(new DefaultMutableTreeNode(
                    new TreeNodeData(hint, TreeNodeType.HINT, null, null, null, hint)));
            return changesNode;
        }

        boolean cliAvailable = isCliAvailable();

        for (Change change : changes) {
            // Resolve the artifact DAG once: it drives both the artifact child nodes and the
            // change-node apply-ready rollup badge.
            ChangeArtifactDag dag = cliAvailable ? loadArtifactDag(change) : null;
            int[] taskCounts = readTaskCounts(change);

            String label = buildChangeLabel(change.getName(), taskCounts);
            TreeNodeType changeType = changeNodeType(dag, taskCounts);
            String changeTooltip = buildChangeTooltip(change, dag, taskCounts);

            DefaultMutableTreeNode changeNode = new DefaultMutableTreeNode(
                    new TreeNodeData(label, changeType, change.getPath(), change.getName(), null, changeTooltip, null,
                            new TreeNodeData.ChangeLabelParts(change.getName(), taskCounts)));

            // Try CLI-based artifact DAG first; fall back to on-disk artifact listing otherwise.
            boolean dagLoaded = addDagArtifactNodes(changeNode, change, dag);
            if (!dagLoaded) {
                addFallbackArtifactNodes(changeNode, change, changeService);
            }

            // Add delta-spec nodes (specs/<domain>/spec.md)
            List<String> deltaSpecs = changeService.getDeltaSpecNames(change);
            for (String domain : deltaSpecs) {
                String deltaPath = change.getPath() + "/specs/" + domain + "/spec.md";
                changeNode.add(new DefaultMutableTreeNode(
                        new TreeNodeData(domain, TreeNodeType.DELTA_SPEC, deltaPath,
                                null, null, "Delta spec — " + deltaPath)));
            }

            changesNode.add(changeNode);
        }

        return changesNode;
    }

    /**
     * Loads the CLI artifact DAG for a change, or null if unavailable/failed.
     */
    private ChangeArtifactDag loadArtifactDag(Change change) {
        try {
            ArtifactOrchestrationService orchestration = project.getService(ArtifactOrchestrationService.class);
            if (orchestration == null) return null;
            return orchestration.getArtifactStatus(change.getName());
        } catch (Exception e) {
            LOG.debug("Failed to load DAG for change: " + change.getName(), e);
            return null;
        }
    }

    /**
     * Builds the change node's label: {@code name X/Y}. The {@code X/Y} task-progress suffix is
     * omitted when {@code taskCounts} is null (no tasks artifact) or has zero tasks. (The invented
     * {@code [status]} tag is retired — active vs archived is directory location, not a metadata field.)
     */
    static String buildChangeLabel(String name, int[] taskCounts) {
        StringBuilder sb = new StringBuilder(name);
        if (taskCounts != null && taskCounts.length == 2 && taskCounts[1] > 0) {
            sb.append(" ").append(taskCounts[0]).append("/").append(taskCounts[1]);
        }
        return sb.toString();
    }

    /**
     * Routes a change node to its rollup state, from two client-owned signals:
     * <ul>
     *   <li>{@link TreeNodeType#CHANGE_ARCHIVE_READY} — the CLI reports every artifact complete AND every task
     *       is done ({@code completed == total}, {@code total > 0}; counts use the client's task-line grammar).
     *       The same condition under which {@code openspec archive} raises no open-tasks warning — a suggestion,
     *       never a guarantee (archive can still be refused, e.g. by validation).</li>
     *   <li>{@link TreeNodeType#CHANGE_DONE} — every artifact complete (apply-ready), tasks open or uncounted.</li>
     *   <li>{@link TreeNodeType#CHANGE} — otherwise, including when the CLI is unavailable ({@code dag == null}):
     *       the artifact half is never guessed.</li>
     * </ul>
     */
    static TreeNodeType changeNodeType(ChangeArtifactDag dag, int[] taskCounts) {
        if (dag == null || !dag.isComplete()) {
            return TreeNodeType.CHANGE;
        }
        return allTasksDone(taskCounts) ? TreeNodeType.CHANGE_ARCHIVE_READY : TreeNodeType.CHANGE_DONE;
    }

    private static boolean allTasksDone(int[] taskCounts) {
        return taskCounts != null && taskCounts.length == 2 && taskCounts[1] > 0 && taskCounts[0] == taskCounts[1];
    }

    private String buildChangeTooltip(Change change, ChangeArtifactDag dag, int[] taskCounts) {
        return changeTooltip(changeNodeType(dag, taskCounts), taskCounts,
                displayPath(project.getBasePath(), change.getPath()));
    }

    /**
     * The change path as shown in its tooltip: relative to the project root when the change lives under it
     * ({@code openspec/changes/<name>}), otherwise the absolute path unchanged (e.g. a change in a store outside
     * the project, or no project root).
     */
    static String displayPath(String projectBase, String changePath) {
        if (projectBase == null || changePath == null) {
            return changePath;
        }
        try {
            Path base = Path.of(projectBase).normalize();
            Path change = Path.of(changePath).normalize();
            if (change.startsWith(base) && !change.equals(base)) {
                return base.relativize(change).toString().replace('\\', '/');
            }
        } catch (java.nio.file.InvalidPathException ignored) {
            // fall through to the absolute path
        }
        return changePath;
    }

    /**
     * The change node's tooltip, one fact per line: the status first in bold (as every badged node names its
     * status), then the task count, then — for archive-ready — a hint deferring to Verify (which also checks
     * validation), and the path last in small text. A change with no status or count shows just its path.
     */
    static String changeTooltip(TreeNodeType type, int[] taskCounts, String path) {
        List<String> lines = new ArrayList<>();
        if (type == TreeNodeType.CHANGE_ARCHIVE_READY) {
            lines.add("<b>All tasks done</b> — ready to archive (suggestion)");
        } else if (type == TreeNodeType.CHANGE_DONE) {
            lines.add("<b>Apply-ready</b> — all artifacts complete");
        }
        if (taskCounts != null && taskCounts.length == 2 && taskCounts[1] > 0) {
            lines.add(taskCounts[0] + "/" + taskCounts[1] + " tasks");
        }
        if (lines.isEmpty()) {
            return path;
        }
        if (type == TreeNodeType.CHANGE_ARCHIVE_READY) {
            lines.add("<i>Run Verify to confirm — archiving may still be blocked by validation.</i>");
        }
        lines.add("<small>" + com.intellij.openapi.util.text.StringUtil.escapeXmlEntities(path) + "</small>");
        return "<html>" + String.join("<br>", lines) + "</html>";
    }

    /**
     * Reads {@code tasks.md} for the change and returns {@code [complete, total]} checkbox
     * counts, or null when no tasks artifact exists (or it has no checkboxes).
     */
    private int[] readTaskCounts(Change change) {
        Path tasksPath = Path.of(change.getPath(), "tasks.md");
        if (!Files.isRegularFile(tasksPath)) return null;
        try {
            int[] counts = ApplyPromptBuilder.countTasks(Files.readString(tasksPath));
            return counts[1] > 0 ? counts : null;
        } catch (IOException e) {
            return null;
        }
    }

    private boolean addDagArtifactNodes(DefaultMutableTreeNode changeNode, Change change, ChangeArtifactDag dag) {
        try {
            if (dag == null || dag.getArtifacts().isEmpty()) return false;

            for (ArtifactInfo artifact : dag.getArtifacts()) {
                TreeNodeType nodeType = switch (artifact.status()) {
                    case DONE -> TreeNodeType.ARTIFACT_DONE;
                    case READY -> TreeNodeType.ARTIFACT_READY;
                    case BLOCKED -> TreeNodeType.ARTIFACT_BLOCKED;
                    default -> TreeNodeType.ARTIFACT;
                };

                String artifactLabel = buildArtifactLabel(artifact);
                String filePath = artifact.outputPath() != null
                        ? change.getPath() + "/" + artifact.outputPath() : null;
                String artifactTooltip = buildArtifactTooltip(artifact, filePath);

                changeNode.add(new DefaultMutableTreeNode(
                        new TreeNodeData(artifactLabel, nodeType, filePath, change.getName(), artifact.id(), artifactTooltip)));
            }

            // Add MISSING_ARTIFACT nodes for required artifacts not in the DAG
            Set<String> dagIds = dag.getArtifacts().stream()
                    .map(ArtifactInfo::id)
                    .collect(Collectors.toSet());
            String versionStr = OpenSpecSettings.getInstance(project).getEffectiveVersion(project);
            VersionSupport version = VersionSupport.fromString(versionStr);
            for (String required : version.getRequiredArtifacts()) {
                if (!dagIds.contains(required)) {
                    changeNode.add(new DefaultMutableTreeNode(
                            new TreeNodeData(required, TreeNodeType.MISSING_ARTIFACT, null, change.getName(), required, "Not yet created")));
                }
            }

            return true;
        } catch (Exception e) {
            LOG.debug("Failed to load DAG for change: " + change.getName(), e);
            return false;
        }
    }

    private String buildArtifactTooltip(ArtifactInfo artifact, String filePath) {
        return switch (artifact.status()) {
            case DONE -> "Complete" + (filePath != null ? " — " + filePath : "");
            case READY -> "Ready to generate";
            case BLOCKED -> "Blocked by: " + String.join(", ", artifact.missingDeps());
            default -> filePath != null ? filePath : artifact.id();
        };
    }

    /**
     * Builds a change-artifact node label. Status is now conveyed by the node's icon badge
     * (see {@link SpecTreeCellRenderer}), so the label is just the artifact id — the former
     * {@code ✓/○/−} glyph prefix is retired. The {@code (needs: …)} suffix is kept for a
     * blocked artifact because it names the specific unmet dependencies, which a badge cannot.
     */
    static String buildArtifactLabel(ArtifactInfo artifact) {
        String label = artifact.id();
        if (artifact.status() == ArtifactStatus.BLOCKED && !artifact.missingDeps().isEmpty()) {
            label += " (needs: " + String.join(", ", artifact.missingDeps()) + ")";
        }
        return label;
    }

    private void addFallbackArtifactNodes(DefaultMutableTreeNode changeNode, Change change, ChangeService changeService) {
        for (String artifact : change.getArtifactFiles()) {
            String path = change.getPath() + "/" + artifact;
            changeNode.add(new DefaultMutableTreeNode(
                    new TreeNodeData(artifact, TreeNodeType.ARTIFACT, path, null, null, path)));
        }
        List<String> missing = changeService.getMissingArtifacts(change);
        for (String missingArtifact : missing) {
            changeNode.add(new DefaultMutableTreeNode(
                    new TreeNodeData(missingArtifact, TreeNodeType.MISSING_ARTIFACT, null, null, null, "Not yet created")));
        }
    }

    private boolean isCliAvailable() {
        CliDetectionService detection = project.getService(CliDetectionService.class);
        return detection != null && detection.isAvailable();
    }

    /**
     * Resolves the active change name from a tree selection path.
     * Walks up from the selected node to find a CHANGE node, returning its changeName.
     * Returns null if the selection is not under a change.
     */
    public static String resolveChangeName(TreePath path) {
        if (path == null) return null;
        Object[] nodes = path.getPath();
        for (Object node : nodes) {
            if (node instanceof DefaultMutableTreeNode treeNode) {
                Object userObject = treeNode.getUserObject();
                if (userObject instanceof TreeNodeData data
                        && data.type().isChange()
                        && data.changeName() != null) {
                    return data.changeName();
                }
            }
        }
        return null;
    }

    public enum TreeNodeType {
        CHANGES, CHANGE, CHANGE_DONE, CHANGE_ARCHIVE_READY, ARTIFACT, MISSING_ARTIFACT,
        ARTIFACT_DONE, ARTIFACT_READY, ARTIFACT_BLOCKED,
        DELTA_SPEC, HINT;

        /** A change node in any rollup state (plain, apply-ready, archive-ready). */
        public boolean isChange() {
            return this == CHANGE || this == CHANGE_DONE || this == CHANGE_ARCHIVE_READY;
        }
    }

    public record TreeNodeData(String label, TreeNodeType type, String filePath, String changeName, String artifactId,
                               String tooltip, String searchText, ChangeLabelParts changeParts)
            implements PathElementIdProvider {
        public TreeNodeData(String label, TreeNodeType type, String filePath, String changeName, String artifactId, String tooltip, String searchText) {
            this(label, type, filePath, changeName, artifactId, tooltip, searchText, null);
        }

        public TreeNodeData(String label, TreeNodeType type, String filePath, String changeName, String artifactId, String tooltip) {
            this(label, type, filePath, changeName, artifactId, tooltip, null, null);
        }

        public TreeNodeData(String label, TreeNodeType type, String filePath, String changeName, String artifactId) {
            this(label, type, filePath, changeName, artifactId, null, null, null);
        }

        public TreeNodeData(String label, TreeNodeType type, String filePath) {
            this(label, type, filePath, null, null, null, null, null);
        }

        @Override
        public String toString() {
            return label;
        }

        /**
         * What the node is, for matching it across a tree rebuild ({@code TreeState}). Never the label or the raw
         * type: a change's label carries its task count and its type moves through the rollup states, and an
         * artifact's label gains {@code (needs: …)} and its type moves from missing to ready to done.
         */
        @Override
        public @org.jetbrains.annotations.NotNull String getPathElementId() {
            if (type == TreeNodeType.CHANGES) return "changes";
            if (type.isChange()) return "change:" + changeName;
            return switch (type) {
                case DELTA_SPEC -> "delta:" + label;
                case HINT -> "hint";
                // CLI-built artifact nodes carry their artifact id; the CLI-absent fallback nodes have only the
                // file name, which is stable within that mode.
                default -> "artifact:" + (artifactId != null ? artifactId : label);
            };
        }

        /**
         * Structured pieces of a change node's label, so the cell renderer can fragment it — name in
         * the default (primary) color and the {@code X/Y} task count dimmed — instead of painting one
         * concatenated string in one color. Null for every non-change node. The flat {@link #label}
         * still carries the concatenated form for search/tooltip/tests.
         */
        public record ChangeLabelParts(String name, int[] taskCounts) {}
    }
}
