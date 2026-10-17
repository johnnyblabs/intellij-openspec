package com.johnnyblabs.openspec.scaffolding;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.johnnyblabs.openspec.services.AiToolDetectionService;
import com.johnnyblabs.openspec.services.CliDetectionService;
import com.johnnyblabs.openspec.settings.OpenSpecSettings;
import com.johnnyblabs.openspec.util.CliRunner;
import com.johnnyblabs.openspec.util.OpenSpecFileUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

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

            // .openspec.yaml — schema+created only, matching real `openspec new change` (no status:)
            writeFile(changeDir, ".openspec.yaml",
                    TemplateProvider.openspecYamlTemplate(
                            OpenSpecSettings.getInstance(project).getEffectiveSchema(project)));

            // proposal.md, from what the user entered in the Propose dialog
            writeFile(changeDir, "proposal.md",
                    TemplateProvider.proposalTemplate(changeName, why, whatChanges));

            // No design.md or tasks.md: `openspec new change` doesn't create them, and a template file would read as
            // done to `openspec status` (which decides done-ness by the file existing). They are generated or written
            // next; until then the CLI reports them ready or blocked.

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

        // Try CLI-delegated init first
        CliDetectionService cliService = project.getService(CliDetectionService.class);
        if (cliService != null && cliService.isAvailable()) {
            try {
                initWithCli();
                // Synchronous VFS refresh to pick up files created by CLI
                String openspecPath = basePath + "/openspec";
                VirtualFile openspecDir = LocalFileSystem.getInstance()
                        .refreshAndFindFileByPath(openspecPath);
                if (openspecDir != null) {
                    // Ensure children (config.yaml, specs/, changes/) are also indexed
                    VfsUtil.markDirtyAndRefresh(false, true, true, openspecDir);
                    return openspecDir;
                }
            } catch (Exception e) {
                LOG.warn("CLI init failed, falling back to built-in: " + e.getMessage());
            }
        }

        // Built-in fallback
        return initBuiltIn(basePath);
    }

    private void initWithCli() throws CliRunner.CliException {
        AiToolDetectionService toolService = project.getService(AiToolDetectionService.class);
        if (toolService != null) {
            toolService.detect();
        }

        List<String> toolIds = toolService != null ? toolService.getDetectedCliToolIds() : List.of();
        String toolsArg = toolIds.isEmpty() ? "none" : String.join(",", toolIds);

        CliRunner.CliResult result = CliRunner.run(project, "init", "--tools", toolsArg);
        if (!result.isSuccess()) {
            throw new CliRunner.CliException("openspec init exited with code " + result.exitCode()
                    + ": " + result.stderr());
        }
    }

    private VirtualFile initBuiltIn(String basePath) throws IOException {
        VirtualFile baseDir = LocalFileSystem.getInstance().findFileByPath(basePath);
        if (baseDir == null) throw new IOException("Project base directory not found");

        return WriteAction.compute(() -> {
            VirtualFile openspecDir = baseDir.createChildDirectory(this, "openspec");

            // config.yaml — schema-only, matching upstream `openspec init`. Honor the user's Default
            // schema setting; fall back to "spec-driven". No plugin-invented version:/profile: fields.
            String schema = OpenSpecSettings.getInstance(project).getEffectiveSchema(project);
            writeFile(openspecDir, "config.yaml", TemplateProvider.configYamlTemplate(schema));

            // specs/
            openspecDir.createChildDirectory(this, "specs");
            // changes/
            VirtualFile changesDir = openspecDir.createChildDirectory(this, "changes");
            // archive/ inside changes/
            changesDir.createChildDirectory(this, "archive");

            return openspecDir;
        });
    }

    private VirtualFile writeFile(VirtualFile dir, String name, String content) throws IOException {
        VirtualFile file = dir.createChildData(this, name);
        file.setBinaryContent(content.getBytes(StandardCharsets.UTF_8));
        return file;
    }
}
