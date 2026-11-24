package com.johnnyblabs.openspec.scaffolding;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;

@Service(Service.Level.PROJECT)
public final class ScaffoldingService {
    private static final Logger LOG = Logger.getInstance(ScaffoldingService.class);

    /** How the init's CLI command is run; a test replaces it, from any thread. */
    @FunctionalInterface
    interface InitRunner {
        CliRunner.CliResult run(Project project, com.johnnyblabs.openspec.util.CliArgs args) throws CliRunner.CliException;
    }

    private volatile InitRunner cliRunner = CliRunner::run;

    @org.jetbrains.annotations.TestOnly
    void setCliRunnerForTest(InitRunner runner) {
        cliRunner = runner;
    }

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

    /**
     * Sets up OpenSpec in the project. The CLI's init runs in a background task (CLI 2.1.0 clones its skills over the
     * network); the VFS refresh and the built-in fallback then run on the EDT under {@code modality}, the caller's
     * (a dialog's, when started from one). Without a CLI the built-in init runs at once. Call on the EDT.
     *
     * @param onDone    gets the {@code openspec/} directory, on the EDT
     * @param onFailure gets the error when neither the CLI nor the built-in init could set it up, on the EDT
     */
    public void initOpenSpec(ModalityState modality, Consumer<VirtualFile> onDone, Consumer<IOException> onFailure) {
        String basePath = project.getBasePath();
        if (basePath == null) {
            onFailure.accept(new IOException("Project base path is null"));
            return;
        }
        CliDetectionService cliService = project.getService(CliDetectionService.class);
        if (cliService == null || !cliService.isAvailable()) {
            finishInit(basePath, null, null, onDone, onFailure);
            return;
        }
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Initializing OpenSpec", false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                Exception failure = null;
                try {
                    initWithCli();
                } catch (ProcessCanceledException e) {
                    throw e;
                } catch (Exception e) {
                    failure = e;
                }
                Exception cliFailure = failure;
                ApplicationManager.getApplication().invokeLater(
                        () -> finishInit(basePath, cliService, cliFailure, onDone, onFailure),
                        modality, project.getDisposed());
            }
        });
    }

    /**
     * The EDT half of {@link #initOpenSpec}: picks up what the CLI wrote, or reports its failure and keeps the
     * {@code openspec/} it already wrote, or runs the built-in init. {@code cli} is null when the CLI didn't run.
     */
    private void finishInit(String basePath, @Nullable CliDetectionService cli, @Nullable Exception cliFailure,
                            Consumer<VirtualFile> onDone, Consumer<IOException> onFailure) {
        try {
            VirtualFile written = cli == null ? null
                    : LocalFileSystem.getInstance().refreshAndFindFileByPath(basePath + "/openspec");
            if (cliFailure != null) {
                LOG.warn("CLI init failed, falling back to built-in: " + cliFailure.getMessage());
                // A CLI can fail after writing openspec/ (2.1.0 creates it, then fails to fetch the skills); that
                // tree is the CLI's own, so it is kept rather than set up again.
                OpenSpecNotifier.warn(project, failedInitNotice(String.valueOf(cliFailure.getMessage()),
                        cli.getDetectedVersion(), written != null));
            }
            if (written != null) {
                // Ensure children (config.yaml, specs/, changes/) are also indexed
                VfsUtil.markDirtyAndRefresh(false, true, true, written);
                onDone.accept(written);
                return;
            }
            onDone.accept(initBuiltIn(basePath));
        } catch (IOException e) {
            onFailure.accept(e);
        }
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
        CliRunner.CliResult result = cliRunner.run(project, initArgs(toolIds));
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
