package com.johnnyblabs.openspec.scaffolding;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.johnnyblabs.openspec.settings.OpenSpecSettings;
import com.johnnyblabs.openspec.util.OpenSpecFileUtil;
import com.johnnyblabs.openspec.version.VersionSupport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Service(Service.Level.PROJECT)
public final class ScaffoldingService {
    private static final Logger LOG = Logger.getInstance(ScaffoldingService.class);

    private final Project project;

    public ScaffoldingService(Project project) {
        this.project = project;
    }

    public VirtualFile createChange(String changeName, String why, String whatChanges) throws IOException {
        VirtualFile changesDir = OpenSpecFileUtil.getChangesDir(project);
        if (changesDir == null) {
            // Create changes dir if needed
            VirtualFile root = OpenSpecFileUtil.getOpenSpecRoot(project);
            if (root == null) throw new IOException("OpenSpec root not found");
            changesDir = WriteAction.compute(() -> root.createChildDirectory(this, "changes"));
        }

        String safeName = changeName.toLowerCase().replaceAll("[^a-z0-9-]", "-");
        VirtualFile finalChangesDir = changesDir;

        return WriteAction.compute(() -> {
            VirtualFile changeDir = finalChangesDir.createChildDirectory(this, safeName);

            // .openspec.yaml
            writeFile(changeDir, ".openspec.yaml",
                    TemplateProvider.openspecYamlTemplate("proposed"));

            // proposal.md (always required)
            writeFile(changeDir, "proposal.md",
                    TemplateProvider.proposalTemplate(changeName, why, whatChanges));

            // Version-aware artifacts
            VersionSupport version = getVersionSupport();
            if (version.getRequiredArtifacts().contains("design")) {
                writeFile(changeDir, "design.md",
                        TemplateProvider.designTemplate(changeName));
            }
            if (version.getRequiredArtifacts().contains("tasks")) {
                writeFile(changeDir, "tasks.md",
                        TemplateProvider.tasksTemplate(changeName));
            }

            // specs directory for delta specs
            changeDir.createChildDirectory(this, "specs");

            return changeDir;
        });
    }

    public VirtualFile createDeltaSpec(VirtualFile changeDir, String domain) throws IOException {
        return WriteAction.compute(() -> {
            VirtualFile specsDir = changeDir.findChild("specs");
            if (specsDir == null) {
                specsDir = changeDir.createChildDirectory(this, "specs");
            }
            String domainName = domain.toLowerCase().replaceAll("[^a-z0-9-]", "-");
            VirtualFile domainDir = specsDir.findChild(domainName);
            if (domainDir == null) {
                domainDir = specsDir.createChildDirectory(this, domainName);
            }
            return writeFile(domainDir, "spec.md", TemplateProvider.deltaSpecTemplate(domain));
        });
    }

    public VirtualFile initOpenSpec() throws IOException {
        String basePath = project.getBasePath();
        if (basePath == null) throw new IOException("Project base path is null");

        VirtualFile baseDir = LocalFileSystem.getInstance().findFileByPath(basePath);
        if (baseDir == null) throw new IOException("Project base directory not found");

        return WriteAction.compute(() -> {
            VirtualFile openspecDir = baseDir.createChildDirectory(this, "openspec");

            // config.yaml
            VersionSupport version = getVersionSupport();
            writeFile(openspecDir, "config.yaml",
                    TemplateProvider.configYamlTemplate("spec-driven", version.getVersion()));

            // specs/
            openspecDir.createChildDirectory(this, "specs");
            // changes/
            openspecDir.createChildDirectory(this, "changes");
            // archive/
            openspecDir.createChildDirectory(this, "archive");

            return openspecDir;
        });
    }

    private VersionSupport getVersionSupport() {
        String version = OpenSpecSettings.getInstance(project).getEffectiveVersion(project);
        return VersionSupport.fromString(version);
    }

    private VirtualFile writeFile(VirtualFile dir, String name, String content) throws IOException {
        VirtualFile file = dir.createChildData(this, name);
        file.setBinaryContent(content.getBytes(StandardCharsets.UTF_8));
        return file;
    }
}
