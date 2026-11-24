package com.johnnyblabs.openspec.scaffolding;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.johnnyblabs.openspec.services.AiToolDetectionService;
import com.johnnyblabs.openspec.services.CliDetectionService;
import com.johnnyblabs.openspec.settings.OpenSpecSettings;
import com.johnnyblabs.openspec.util.CliRunner;
import com.johnnyblabs.openspec.util.OpenSpecFileUtil;
import com.johnnyblabs.openspec.util.OpenSpecNotifier;
import org.jetbrains.annotations.Nullable;

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
                // A CLI can fail after writing openspec/ (2.1.0 creates it, then fails to fetch the skills); that
                // tree is the CLI's own, so it is kept rather than set up again.
                VirtualFile written = LocalFileSystem.getInstance().refreshAndFindFileByPath(basePath + "/openspec");
                OpenSpecNotifier.warn(project, failedInitNotice(String.valueOf(e.getMessage()),
                        cliService.getDetectedVersion(), written != null));
                if (written != null) {
                    VfsUtil.markDirtyAndRefresh(false, true, true, written);
                    return written;
                }
            }
        }

        // Built-in fallback
        return initBuiltIn(basePath);
    }

    /** {@code init --tools <ids>} for the detected tools, or {@code --tools none} when there are none. */
    static com.johnnyblabs.openspec.util.CliArgs initArgs(List<String> toolIds) {
        return com.johnnyblabs.openspec.util.CliCommands.initInProject(toolIds.isEmpty() ? "none" : String.join(",", toolIds));
    }

    private void initWithCli() throws CliRunner.CliException {
        AiToolDetectionService toolService = project.getService(AiToolDetectionService.class);
        if (toolService != null) {
            toolService.detect();
        }

        List<String> toolIds = toolService != null ? toolService.getDetectedCliToolIds() : List.of();
        CliRunner.CliResult result = CliRunner.run(project, initArgs(toolIds));
        if (!result.isSuccess()) throw new CliRunner.CliException(cliErrorText(result));
    }

    /**
     * The CLI's own words for a failed run: its stderr, plus the {@code Failed:} lines its stdout summary carries (2.1
     * names the reason there, and only the tool on stderr). With an empty stderr, its whole stdout.
     */
    static String cliErrorText(CliRunner.CliResult result) {
        String err = result.stderr() == null ? "" : result.stderr().strip();
        String out = result.stdout() == null ? "" : result.stdout().strip();
        if (err.isEmpty()) return out.isEmpty() ? "openspec init exited with code " + result.exitCode() : out;
        StringBuilder text = new StringBuilder(err);
        for (String line : out.split("\\R")) {
            if (line.startsWith("Failed:")) text.append('\n').append(line);
        }
        return text.toString();
    }

    /** CLI 2.1.0 clones its skills with Git during init; 2.1.1 ships them in the package. */
    static final String GIT_NETWORK_HINT = "OpenSpec CLI 2.1.0 downloads its skills with Git during init; check that "
            + "Git is installed and the network is reachable, or use CLI 2.1.1 or later.";

    /**
     * The warning for a failed CLI init: the CLI's text as it wrote it, and what the plugin did instead (kept the
     * {@code openspec/} the CLI wrote, or ran the built-in init).
     */
    static String failedInitNotice(String cliText, @Nullable String detectedVersion, boolean keptCliTree) {
        StringBuilder notice = new StringBuilder(keptCliTree
                ? "The OpenSpec CLI set up openspec/ but couldn't finish. The CLI said:"
                : "The OpenSpec CLI couldn't set up this project, so the plugin's built-in setup ran instead. "
                + "The CLI said:")
                .append("<br><pre>").append(StringUtil.escapeXmlEntities(cliText)).append("</pre>");
        if (detectedVersion != null && "2.1.0".equals(detectedVersion.trim())) notice.append(GIT_NETWORK_HINT);
        return notice.toString();
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
