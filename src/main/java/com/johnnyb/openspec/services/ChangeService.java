package com.johnnyb.openspec.services;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.johnnyb.openspec.model.Change;
import com.johnnyb.openspec.model.ChangeMetadata;
import com.johnnyb.openspec.model.ChangeStatus;
import com.johnnyb.openspec.util.OpenSpecFileUtil;
import com.johnnyb.openspec.util.OpenSpecNotifier;
import com.johnnyb.openspec.version.VersionSupport;
import com.johnnyb.openspec.settings.OpenSpecSettings;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.error.MarkedYAMLException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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

    public ChangeStatus getStatus(Change change) {
        if (change.getMetadata() != null && change.getMetadata().getStatus() != null) {
            return ChangeStatus.fromString(change.getMetadata().getStatus());
        }
        return ChangeStatus.UNKNOWN;
    }

    public void updateStatus(Change change, ChangeStatus newStatus) throws IOException {
        VirtualFile changeDir = com.intellij.openapi.vfs.VirtualFileManager.getInstance()
                .findFileByUrl(com.intellij.openapi.vfs.VfsUtilCore.pathToUrl(change.getPath()));
        if (changeDir == null) return;

        VirtualFile metaFile = changeDir.findChild(".openspec.yaml");
        if (metaFile == null) return;

        WriteAction.run(() -> {
            String content = new String(metaFile.contentsToByteArray(), StandardCharsets.UTF_8);
            String updated = content.replaceAll("status:\\s*\\w+", "status: " + newStatus.name().toLowerCase());
            metaFile.setBinaryContent(updated.getBytes(StandardCharsets.UTF_8));
        });
    }

    public String archiveFirstActive() throws IOException {
        List<Change> active = getActiveChanges();
        if (active.isEmpty()) return null;
        return archiveChange(active.getFirst());
    }

    public String archiveChange(Change change) throws IOException {
        VirtualFile changesDir = OpenSpecFileUtil.getChangesDir(project);
        VirtualFile archiveDir = OpenSpecFileUtil.getArchiveDir(project);
        if (changesDir == null) throw new IOException("Changes directory not found");

        VirtualFile changeDir = changesDir.findChild(change.getName());
        if (changeDir == null) throw new IOException("Change directory not found: " + change.getName());

        WriteAction.run(() -> {
            VirtualFile targetArchiveDir = archiveDir;
            if (targetArchiveDir == null) {
                VirtualFile root = OpenSpecFileUtil.getOpenSpecRoot(project);
                if (root != null) {
                    targetArchiveDir = root.createChildDirectory(this, "archive");
                }
            }
            if (targetArchiveDir != null) {
                changeDir.move(this, targetArchiveDir);
            }
        });

        // Update status to archived
        change.getMetadata().setStatus("archived");
        return change.getName();
    }

    public List<String> getMissingArtifacts(Change change) {
        String version = OpenSpecSettings.getInstance(project).getEffectiveVersion(project);
        VersionSupport vs = VersionSupport.fromString(version);
        Set<String> required = vs.getRequiredArtifacts();
        List<String> missing = new ArrayList<>();
        for (String artifact : required) {
            if (!change.getArtifactFiles().contains(artifact)) {
                missing.add(artifact);
            }
        }
        return missing;
    }

    public List<String> getDeltaSpecNames(Change change) {
        List<String> names = new ArrayList<>();
        VirtualFile changesDir = OpenSpecFileUtil.getChangesDir(project);
        if (changesDir == null) return names;
        VirtualFile changeDir = changesDir.findChild(change.getName());
        if (changeDir == null) return names;
        VirtualFile specsDir = changeDir.findChild("specs");
        if (specsDir == null) return names;
        for (VirtualFile domainDir : specsDir.getChildren()) {
            if (!domainDir.isDirectory()) continue;
            VirtualFile specFile = domainDir.findChild("spec.md");
            if (specFile != null) {
                names.add(domainDir.getName());
            }
        }
        return names;
    }

    private static final Set<String> RESERVED_DIRS = Set.of("archive");

    private List<Change> getChangesFromDir(VirtualFile dir) {
        List<Change> changes = new ArrayList<>();
        if (dir == null || !dir.exists()) return changes;

        for (VirtualFile changeDir : dir.getChildren()) {
            if (!changeDir.isDirectory()) continue;
            if (RESERVED_DIRS.contains(changeDir.getName())) continue;
            Change change = new Change(changeDir.getName(), changeDir.getPath());

            VirtualFile metaFile = changeDir.findChild(".openspec.yaml");
            if (metaFile != null) {
                try (InputStream is = metaFile.getInputStream()) {
                    Yaml yaml = new Yaml(new Constructor(ChangeMetadata.class, new LoaderOptions()));
                    ChangeMetadata metadata = yaml.loadAs(is, ChangeMetadata.class);
                    change.setMetadata(metadata);
                } catch (MarkedYAMLException e) {
                    String problem = e.getProblem() != null ? e.getProblem() : "invalid YAML";
                    LOG.warn("Failed to parse change metadata: " + metaFile.getPath() + " — " + problem, e);
                    OpenSpecNotifier.notify(project, OpenSpecNotifier.GROUP_SYSTEM, "Configuration",
                            ".openspec.yaml parse error in '" + changeDir.getName() + "': " + problem,
                            com.intellij.notification.NotificationType.WARNING);
                } catch (Exception e) {
                    LOG.warn("Failed to read change metadata: " + metaFile.getPath(), e);
                }
            }

            for (VirtualFile child : changeDir.getChildren()) {
                if (child.isDirectory() && "specs".equals(child.getName())) {
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
