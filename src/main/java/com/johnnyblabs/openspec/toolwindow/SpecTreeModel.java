package com.johnnyblabs.openspec.toolwindow;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.model.*;
import com.johnnyblabs.openspec.services.ArtifactOrchestrationService;
import com.johnnyblabs.openspec.services.ChangeService;
import com.johnnyblabs.openspec.services.CliDetectionService;
import com.johnnyblabs.openspec.services.ConfigService;
import com.johnnyblabs.openspec.services.SpecParsingService;
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
        return buildModel(null);
    }

    public DefaultTreeModel buildModel(String query) {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("OpenSpec");

        if (!OpenSpecFileUtil.isOpenSpecProject(project)) {
            String hint = "No openspec/ directory found — click Initialize to set up.";
            root.add(new DefaultMutableTreeNode(
                    new TreeNodeData(hint, TreeNodeType.HINT, null, null, null, hint)));
            return new DefaultTreeModel(root);
        }

        String normalizedQuery = (query != null && !query.isBlank()) ? query.trim().toLowerCase() : null;

        DefaultMutableTreeNode specsNode = buildSpecsNode();
        DefaultMutableTreeNode changesNode = buildChangesNode();
        DefaultMutableTreeNode archiveNode = buildArchiveNode();
        DefaultMutableTreeNode configNode = buildConfigNode();

        if (normalizedQuery == null) {
            root.add(specsNode);
            root.add(changesNode);
            root.add(archiveNode);
            root.add(configNode);
        } else {
            DefaultMutableTreeNode filteredSpecs = filterNode(specsNode, normalizedQuery);
            DefaultMutableTreeNode filteredChanges = filterNode(changesNode, normalizedQuery);
            DefaultMutableTreeNode filteredArchive = filterNode(archiveNode, normalizedQuery);
            DefaultMutableTreeNode filteredConfig = filterNode(configNode, normalizedQuery);

            if (filteredSpecs != null) root.add(filteredSpecs);
            if (filteredChanges != null) root.add(filteredChanges);
            if (filteredArchive != null) root.add(filteredArchive);
            if (filteredConfig != null) root.add(filteredConfig);

            if (root.getChildCount() == 0) {
                String hint = "No results for '" + query.trim() + "'";
                root.add(new DefaultMutableTreeNode(
                        new TreeNodeData(hint, TreeNodeType.HINT, null, null, null, hint)));
            }
        }

        return new DefaultTreeModel(root);
    }

    static DefaultMutableTreeNode filterNode(DefaultMutableTreeNode node, String query) {
        Object userObj = node.getUserObject();
        String label = (userObj instanceof TreeNodeData data) ? data.label() : node.toString();
        String searchText = (userObj instanceof TreeNodeData data) ? data.searchText() : null;
        boolean selfMatches = label.toLowerCase().contains(query)
                || (searchText != null && searchText.toLowerCase().contains(query));

        // Collect filtered children
        java.util.List<DefaultMutableTreeNode> matchingChildren = new java.util.ArrayList<>();
        for (int i = 0; i < node.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) node.getChildAt(i);
            DefaultMutableTreeNode filtered = filterNode(child, query);
            if (filtered != null) {
                matchingChildren.add(filtered);
            }
        }

        if (!selfMatches && matchingChildren.isEmpty()) {
            return null;
        }

        // Clone the node (without children) and add matching children
        DefaultMutableTreeNode clone = new DefaultMutableTreeNode(node.getUserObject());
        if (selfMatches && matchingChildren.isEmpty()) {
            // Self matches — include all original children
            for (int i = 0; i < node.getChildCount(); i++) {
                clone.add(cloneSubtree((DefaultMutableTreeNode) node.getChildAt(i)));
            }
        } else {
            for (DefaultMutableTreeNode child : matchingChildren) {
                clone.add(child);
            }
        }
        return clone;
    }

    private static DefaultMutableTreeNode cloneSubtree(DefaultMutableTreeNode node) {
        DefaultMutableTreeNode clone = new DefaultMutableTreeNode(node.getUserObject());
        for (int i = 0; i < node.getChildCount(); i++) {
            clone.add(cloneSubtree((DefaultMutableTreeNode) node.getChildAt(i)));
        }
        return clone;
    }

    private DefaultMutableTreeNode buildSpecsNode() {
        SpecParsingService parsingService = project.getService(SpecParsingService.class);
        return buildSpecsNode(parsingService.parseAllSpecs());
    }

    /**
     * Builds the Specs subtree from parsed spec files. Pure of the project/service so it is
     * unit-testable headlessly. Each requirement node carries {@code searchText} (name + body +
     * scenario text) so {@link #filterNode} can match content, not just labels.
     */
    static DefaultMutableTreeNode buildSpecsNode(List<SpecFile> specs) {
        DefaultMutableTreeNode specsNode = new DefaultMutableTreeNode(
                new TreeNodeData("Specs", TreeNodeType.SPECS, null, null, null, "Capability specifications"));

        if (specs.isEmpty()) {
            String hint = "No specs found yet.";
            specsNode.add(new DefaultMutableTreeNode(
                    new TreeNodeData(hint, TreeNodeType.HINT, null, null, null, hint)));
            return specsNode;
        }

        for (SpecFile spec : specs) {
            int reqCount = spec.getRequirements().size();
            String domainTooltip = reqCount + " requirement" + (reqCount != 1 ? "s" : "") + " — " + spec.getFilePath();
            DefaultMutableTreeNode domainNode = new DefaultMutableTreeNode(
                    new TreeNodeData(spec.getDomain(), TreeNodeType.SPEC_DOMAIN, spec.getFilePath(), null, null, domainTooltip));

            for (Requirement req : spec.getRequirements()) {
                domainNode.add(new DefaultMutableTreeNode(
                        new TreeNodeData("Requirement: " + req.getName(), TreeNodeType.REQUIREMENT, spec.getFilePath(),
                                null, null, req.getName(), SpecContentMatcher.searchableText(req))));
            }

            specsNode.add(domainNode);
        }

        return specsNode;
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
            ChangeStatus status = changeService.getStatus(change);

            // Resolve the artifact DAG once: it drives both the artifact child nodes and the
            // change-node apply-ready rollup badge.
            ChangeArtifactDag dag = cliAvailable ? loadArtifactDag(change) : null;
            int[] taskCounts = readTaskCounts(change);

            String label = buildChangeLabel(change.getName(), status, taskCounts);
            TreeNodeType changeType = changeNodeType(dag);
            String changeTooltip = buildChangeTooltip(change, dag, taskCounts);

            DefaultMutableTreeNode changeNode = new DefaultMutableTreeNode(
                    new TreeNodeData(label, changeType, change.getPath(), change.getName(), null, changeTooltip));

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
     * Builds the change node's label: {@code name [status] X/Y}. The status suffix is
     * omitted for {@link ChangeStatus#UNKNOWN}; the {@code X/Y} task progress suffix is
     * omitted when {@code taskCounts} is null (no tasks artifact) or has zero tasks.
     */
    static String buildChangeLabel(String name, ChangeStatus status, int[] taskCounts) {
        StringBuilder sb = new StringBuilder(name);
        if (status != null && status != ChangeStatus.UNKNOWN) {
            sb.append(" ").append(status.toLabel());
        }
        if (taskCounts != null && taskCounts.length == 2 && taskCounts[1] > 0) {
            sb.append(" ").append(taskCounts[0]).append("/").append(taskCounts[1]);
        }
        return sb.toString();
    }

    /**
     * Routes a change node to {@link TreeNodeType#CHANGE_DONE} (apply-ready done badge)
     * when the CLI reports every artifact complete, else plain {@link TreeNodeType#CHANGE}.
     */
    static TreeNodeType changeNodeType(ChangeArtifactDag dag) {
        return (dag != null && dag.isComplete()) ? TreeNodeType.CHANGE_DONE : TreeNodeType.CHANGE;
    }

    private String buildChangeTooltip(Change change, ChangeArtifactDag dag, int[] taskCounts) {
        List<String> parts = new ArrayList<>();
        if (dag != null && dag.isComplete()) {
            parts.add("Apply-ready — all artifacts complete");
        }
        if (taskCounts != null && taskCounts.length == 2 && taskCounts[1] > 0) {
            parts.add(taskCounts[0] + "/" + taskCounts[1] + " tasks");
        }
        return parts.isEmpty() ? change.getPath() : String.join(" — ", parts) + " — " + change.getPath();
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

    private DefaultMutableTreeNode buildArchiveNode() {
        DefaultMutableTreeNode archiveNode = new DefaultMutableTreeNode(
                new TreeNodeData("Archive", TreeNodeType.ARCHIVE, null, null, null, "Completed changes"));

        ChangeService changeService = project.getService(ChangeService.class);
        List<Change> archived = changeService.getArchivedChanges();

        for (Change change : archived) {
            archiveNode.add(new DefaultMutableTreeNode(
                    new TreeNodeData(change.getName(), TreeNodeType.CHANGE, change.getPath(), null, null, change.getPath())));
        }

        return archiveNode;
    }

    private DefaultMutableTreeNode buildConfigNode() {
        String configPath = project.getBasePath() + "/openspec/config.yaml";
        ConfigService configService = project.getService(ConfigService.class);
        OpenSpecConfig config = configService != null ? configService.getConfig() : null;
        return buildConfigNode(config, configPath);
    }

    static DefaultMutableTreeNode buildConfigNode(OpenSpecConfig config, String configPath) {
        DefaultMutableTreeNode configNode = new DefaultMutableTreeNode(
                new TreeNodeData("Config", TreeNodeType.CONFIG, configPath, null, null, "openspec/config.yaml"));

        if (config == null) {
            String hint = "No config.yaml found";
            configNode.add(new DefaultMutableTreeNode(
                    new TreeNodeData(hint, TreeNodeType.HINT, null, null, null, hint)));
            return configNode;
        }

        if (config.getSchema() != null && !config.getSchema().isEmpty()) {
            configNode.add(new DefaultMutableTreeNode(
                    new TreeNodeData("schema: " + config.getSchema(), TreeNodeType.CONFIG_ENTRY, configPath)));
        }
        if (config.getVersion() != null && !config.getVersion().isEmpty()) {
            configNode.add(new DefaultMutableTreeNode(
                    new TreeNodeData("version: " + config.getVersion(), TreeNodeType.CONFIG_ENTRY, configPath)));
        }
        if (config.getProfile() != null && !config.getProfile().isEmpty()) {
            String profileName = config.getProfile().getOrDefault("name", "unnamed");
            configNode.add(new DefaultMutableTreeNode(
                    new TreeNodeData("profile: " + profileName, TreeNodeType.CONFIG_ENTRY, configPath,
                            null, null, "Profile: " + config.getProfile().entrySet().stream()
                            .map(e -> e.getKey() + "=" + e.getValue())
                            .collect(Collectors.joining(", ")))));
        }
        if (config.getContext() != null && !config.getContext().isEmpty()) {
            String truncated = config.getContext().length() > 60
                    ? config.getContext().substring(0, 60) + "..."
                    : config.getContext();
            configNode.add(new DefaultMutableTreeNode(
                    new TreeNodeData("context: " + truncated, TreeNodeType.CONFIG_ENTRY, configPath,
                            null, null, config.getContext())));
        }
        if (config.getRules() != null && !config.getRules().isEmpty()) {
            int count = config.getRules().size();
            configNode.add(new DefaultMutableTreeNode(
                    new TreeNodeData("rules: " + count + " defined", TreeNodeType.CONFIG_ENTRY, configPath,
                            null, null, "Rules: " + String.join(", ", config.getRules().keySet()))));
        }

        return configNode;
    }

    /**
     * Resolves the active change name from a tree selection path.
     * Walks up from the selected node to find a CHANGE node, returning its changeName.
     * Returns null if the selection is not under a change (e.g., main specs, config, archive).
     */
    public static String resolveChangeName(TreePath path) {
        if (path == null) return null;
        Object[] nodes = path.getPath();
        for (Object node : nodes) {
            if (node instanceof DefaultMutableTreeNode treeNode) {
                Object userObject = treeNode.getUserObject();
                if (userObject instanceof TreeNodeData data
                        && (data.type() == TreeNodeType.CHANGE || data.type() == TreeNodeType.CHANGE_DONE)
                        && data.changeName() != null) {
                    return data.changeName();
                }
            }
        }
        return null;
    }

    public enum TreeNodeType {
        SPECS, SPEC_DOMAIN, REQUIREMENT, CHANGES, CHANGE, CHANGE_DONE, ARTIFACT, MISSING_ARTIFACT,
        ARTIFACT_DONE, ARTIFACT_READY, ARTIFACT_BLOCKED,
        DELTA_SPEC, ARCHIVE, CONFIG, CONFIG_ENTRY, HINT
    }

    public record TreeNodeData(String label, TreeNodeType type, String filePath, String changeName, String artifactId,
                               String tooltip, String searchText) {
        public TreeNodeData(String label, TreeNodeType type, String filePath, String changeName, String artifactId, String tooltip) {
            this(label, type, filePath, changeName, artifactId, tooltip, null);
        }

        public TreeNodeData(String label, TreeNodeType type, String filePath, String changeName, String artifactId) {
            this(label, type, filePath, changeName, artifactId, null, null);
        }

        public TreeNodeData(String label, TreeNodeType type, String filePath) {
            this(label, type, filePath, null, null, null, null);
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
