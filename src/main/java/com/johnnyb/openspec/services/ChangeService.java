package com.johnnyb.openspec.services;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.johnnyb.openspec.model.Change;
import com.johnnyb.openspec.model.ChangeMetadata;
import com.johnnyb.openspec.util.OpenSpecFileUtil;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Service(Service.Level.PROJECT)
public final class ChangeService {
    private static final Logger LOG = Logger.getInstance(ChangeService.class);

    private final Project project;

    public ChangeService(Project project) {
        this.project = project;
    }

    public List<Change> getActiveChanges() {
        return getChangesFromDir(OpenSpecFileUtil.getChangesDir(project));
    }

    public List<Change> getArchivedChanges() {
        return getChangesFromDir(OpenSpecFileUtil.getArchiveDir(project));
    }

    private List<Change> getChangesFromDir(VirtualFile dir) {
        List<Change> changes = new ArrayList<>();
        if (dir == null || !dir.exists()) return changes;

        for (VirtualFile changeDir : dir.getChildren()) {
            if (!changeDir.isDirectory()) continue;
            Change change = new Change(changeDir.getName(), changeDir.getPath());

            VirtualFile metaFile = changeDir.findChild(".openspec.yaml");
            if (metaFile != null) {
                try (InputStream is = metaFile.getInputStream()) {
                    Yaml yaml = new Yaml();
                    ChangeMetadata metadata = yaml.loadAs(is, ChangeMetadata.class);
                    change.setMetadata(metadata);
                } catch (Exception e) {
                    LOG.warn("Failed to parse change metadata: " + metaFile.getPath(), e);
                }
            }

            for (VirtualFile child : changeDir.getChildren()) {
                if (child.isDirectory() && "delta-specs".equals(child.getName())) {
                    // delta specs handled separately
                    continue;
                }
                if (!child.isDirectory() && !child.getName().startsWith(".")) {
                    change.getArtifactFiles().add(child.getName());
                }
            }

            changes.add(change);
        }
        return changes;
    }
}
