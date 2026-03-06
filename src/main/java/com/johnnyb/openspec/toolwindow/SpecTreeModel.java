package com.johnnyb.openspec.toolwindow;

import com.intellij.openapi.project.Project;
import com.johnnyb.openspec.model.Change;
import com.johnnyb.openspec.model.Requirement;
import com.johnnyb.openspec.model.SpecFile;
import com.johnnyb.openspec.services.ChangeService;
import com.johnnyb.openspec.services.SpecParsingService;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.util.List;

public class SpecTreeModel {

    private final Project project;

    public SpecTreeModel(Project project) {
        this.project = project;
    }

    public DefaultTreeModel buildModel() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("OpenSpec");

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

        for (Change change : changes) {
            DefaultMutableTreeNode changeNode = new DefaultMutableTreeNode(
                    new TreeNodeData(change.getName(), TreeNodeType.CHANGE, change.getPath()));

            for (String artifact : change.getArtifactFiles()) {
                changeNode.add(new DefaultMutableTreeNode(
                        new TreeNodeData(artifact, TreeNodeType.ARTIFACT, change.getPath() + "/" + artifact)));
            }

            changesNode.add(changeNode);
        }

        return changesNode;
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
        SPECS, SPEC_DOMAIN, REQUIREMENT, CHANGES, CHANGE, ARTIFACT, ARCHIVE
    }

    public record TreeNodeData(String label, TreeNodeType type, String filePath) {
        @Override
        public String toString() {
            return label;
        }
    }
}
