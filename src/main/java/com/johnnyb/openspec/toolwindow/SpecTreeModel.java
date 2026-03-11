package com.johnnyb.openspec.toolwindow;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.johnnyb.openspec.model.*;
import com.johnnyb.openspec.services.ArtifactOrchestrationService;
import com.johnnyb.openspec.services.ChangeService;
import com.johnnyb.openspec.services.CliDetectionService;
import com.johnnyb.openspec.services.SpecParsingService;
import com.johnnyb.openspec.util.OpenSpecFileUtil;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.util.List;

public class SpecTreeModel {
    private static final Logger LOG = Logger.getInstance(SpecTreeModel.class);

    private final Project project;

    public SpecTreeModel(Project project) {
        this.project = project;
    }

    public DefaultTreeModel buildModel() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("OpenSpec");

        if (!OpenSpecFileUtil.isOpenSpecProject(project)) {
            root.add(new DefaultMutableTreeNode(
                    new TreeNodeData("Not an OpenSpec project. Use Init to set up.", TreeNodeType.HINT, null)));
            return new DefaultTreeModel(root);
        }

        root.add(buildSpecsNode());
        root.add(buildChangesNode());
        root.add(buildArchiveNode());

        return new DefaultTreeModel(root);
    }

    private DefaultMutableTreeNode buildSpecsNode() {
        DefaultMutableTreeNode specsNode = new DefaultMutableTreeNode(
                new TreeNodeData("Specs", TreeNodeType.SPECS, null));

        SpecParsingService parsingService = project.getService(SpecParsingService.class);
        List<SpecFile> specs = parsingService.parseAllSpecs();

        if (specs.isEmpty()) {
            specsNode.add(new DefaultMutableTreeNode(
                    new TreeNodeData("No specs found. Run Init to get started.", TreeNodeType.HINT, null)));
            return specsNode;
        }

        for (SpecFile spec : specs) {
            DefaultMutableTreeNode domainNode = new DefaultMutableTreeNode(
                    new TreeNodeData(spec.getDomain(), TreeNodeType.SPEC_DOMAIN, spec.getFilePath()));

            for (Requirement req : spec.getRequirements()) {
                domainNode.add(new DefaultMutableTreeNode(
                        new TreeNodeData("Requirement: " + req.getName(), TreeNodeType.REQUIREMENT, spec.getFilePath())));
            }

            specsNode.add(domainNode);
        }

        return specsNode;
    }

    private DefaultMutableTreeNode buildChangesNode() {
        DefaultMutableTreeNode changesNode = new DefaultMutableTreeNode(
                new TreeNodeData("Changes", TreeNodeType.CHANGES, null));

        ChangeService changeService = project.getService(ChangeService.class);
        List<Change> changes = changeService.getActiveChanges();

        if (changes.isEmpty()) {
            changesNode.add(new DefaultMutableTreeNode(
                    new TreeNodeData("No active changes. Use Propose to create one.", TreeNodeType.HINT, null)));
            return changesNode;
        }

        boolean cliAvailable = isCliAvailable();

        for (Change change : changes) {
            // Add status label to change name
            ChangeStatus status = changeService.getStatus(change);
            String label = change.getName();
            if (status != ChangeStatus.UNKNOWN) {
                label += " " + status.toLabel();
            }
            // Add tracking indicator
            if (change.getMetadata() != null && change.getMetadata().getTracking() != null) {
                ChangeMetadata.TrackingMetadata tracking = change.getMetadata().getTracking();
                boolean hasForgejoLink = tracking.getForgejo() != null && tracking.getForgejo().getIssueNumber() > 0;
                boolean hasPlaneLink = tracking.getPlane() != null && tracking.getPlane().getWorkItemId() != null;
                if (hasForgejoLink || hasPlaneLink) {
                    label += " [linked]";
                }
            }

            DefaultMutableTreeNode changeNode = new DefaultMutableTreeNode(
                    new TreeNodeData(label, TreeNodeType.CHANGE, change.getPath(), change.getName(), null));

            // Try CLI-based artifact DAG first
            if (cliAvailable) {
                boolean dagLoaded = addDagArtifactNodes(changeNode, change);
                if (!dagLoaded) {
                    addFallbackArtifactNodes(changeNode, change, changeService);
                }
            } else {
                addFallbackArtifactNodes(changeNode, change, changeService);
            }

            // Add delta-spec nodes (specs/<domain>/spec.md)
            List<String> deltaSpecs = changeService.getDeltaSpecNames(change);
            for (String domain : deltaSpecs) {
                changeNode.add(new DefaultMutableTreeNode(
                        new TreeNodeData(domain, TreeNodeType.DELTA_SPEC,
                                change.getPath() + "/specs/" + domain + "/spec.md")));
            }

            changesNode.add(changeNode);
        }

        return changesNode;
    }

    private boolean addDagArtifactNodes(DefaultMutableTreeNode changeNode, Change change) {
        try {
            ArtifactOrchestrationService orchestration = project.getService(ArtifactOrchestrationService.class);
            if (orchestration == null) return false;

            ChangeArtifactDag dag = orchestration.getArtifactStatus(change.getName());
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

                changeNode.add(new DefaultMutableTreeNode(
                        new TreeNodeData(artifactLabel, nodeType, filePath, change.getName(), artifact.id())));
            }
            return true;
        } catch (Exception e) {
            LOG.debug("Failed to load DAG for change: " + change.getName(), e);
            return false;
        }
    }

    private String buildArtifactLabel(ArtifactInfo artifact) {
        String icon = artifact.status().toIcon();
        String label = icon + " " + artifact.id();
        if (artifact.status() == ArtifactStatus.BLOCKED && !artifact.missingDeps().isEmpty()) {
            label += " (needs: " + String.join(", ", artifact.missingDeps()) + ")";
        }
        return label;
    }

    private void addFallbackArtifactNodes(DefaultMutableTreeNode changeNode, Change change, ChangeService changeService) {
        for (String artifact : change.getArtifactFiles()) {
            changeNode.add(new DefaultMutableTreeNode(
                    new TreeNodeData(artifact, TreeNodeType.ARTIFACT, change.getPath() + "/" + artifact)));
        }
        List<String> missing = changeService.getMissingArtifacts(change);
        for (String missingArtifact : missing) {
            changeNode.add(new DefaultMutableTreeNode(
                    new TreeNodeData(missingArtifact, TreeNodeType.MISSING_ARTIFACT, null)));
        }
    }

    private boolean isCliAvailable() {
        CliDetectionService detection = project.getService(CliDetectionService.class);
        return detection != null && detection.isAvailable();
    }

    private DefaultMutableTreeNode buildArchiveNode() {
        DefaultMutableTreeNode archiveNode = new DefaultMutableTreeNode(
                new TreeNodeData("Archive", TreeNodeType.ARCHIVE, null));

        ChangeService changeService = project.getService(ChangeService.class);
        List<Change> archived = changeService.getArchivedChanges();

        for (Change change : archived) {
            archiveNode.add(new DefaultMutableTreeNode(
                    new TreeNodeData(change.getName(), TreeNodeType.CHANGE, change.getPath())));
        }

        return archiveNode;
    }

    public enum TreeNodeType {
        SPECS, SPEC_DOMAIN, REQUIREMENT, CHANGES, CHANGE, ARTIFACT, MISSING_ARTIFACT,
        ARTIFACT_DONE, ARTIFACT_READY, ARTIFACT_BLOCKED,
        DELTA_SPEC, ARCHIVE, HINT
    }

    public record TreeNodeData(String label, TreeNodeType type, String filePath, String changeName, String artifactId) {
        /**
         * Backward-compatible constructor for nodes without change/artifact context.
         */
        public TreeNodeData(String label, TreeNodeType type, String filePath) {
            this(label, type, filePath, null, null);
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
