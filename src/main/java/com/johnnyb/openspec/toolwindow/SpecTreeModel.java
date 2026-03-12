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
            String hint = "No openspec/ directory found — click Initialize to set up.";
            root.add(new DefaultMutableTreeNode(
                    new TreeNodeData(hint, TreeNodeType.HINT, null, null, null, hint)));
            return new DefaultTreeModel(root);
        }

        root.add(buildSpecsNode());
        root.add(buildChangesNode());
        root.add(buildArchiveNode());

        return new DefaultTreeModel(root);
    }

    private DefaultMutableTreeNode buildSpecsNode() {
        DefaultMutableTreeNode specsNode = new DefaultMutableTreeNode(
                new TreeNodeData("Specs", TreeNodeType.SPECS, null, null, null, "Capability specifications"));

        SpecParsingService parsingService = project.getService(SpecParsingService.class);
        List<SpecFile> specs = parsingService.parseAllSpecs();

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
                                null, null, req.getName())));
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
                    new TreeNodeData(label, TreeNodeType.CHANGE, change.getPath(), change.getName(), null, change.getPath()));

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
                String deltaPath = change.getPath() + "/specs/" + domain + "/spec.md";
                changeNode.add(new DefaultMutableTreeNode(
                        new TreeNodeData(domain, TreeNodeType.DELTA_SPEC, deltaPath,
                                null, null, "Delta spec — " + deltaPath)));
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
                String artifactTooltip = buildArtifactTooltip(artifact, filePath);

                changeNode.add(new DefaultMutableTreeNode(
                        new TreeNodeData(artifactLabel, nodeType, filePath, change.getName(), artifact.id(), artifactTooltip)));
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

    public enum TreeNodeType {
        SPECS, SPEC_DOMAIN, REQUIREMENT, CHANGES, CHANGE, ARTIFACT, MISSING_ARTIFACT,
        ARTIFACT_DONE, ARTIFACT_READY, ARTIFACT_BLOCKED,
        DELTA_SPEC, ARCHIVE, HINT
    }

    public record TreeNodeData(String label, TreeNodeType type, String filePath, String changeName, String artifactId, String tooltip) {
        public TreeNodeData(String label, TreeNodeType type, String filePath, String changeName, String artifactId) {
            this(label, type, filePath, changeName, artifactId, null);
        }

        public TreeNodeData(String label, TreeNodeType type, String filePath) {
            this(label, type, filePath, null, null, null);
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
