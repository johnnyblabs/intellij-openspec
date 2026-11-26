package com.johnnyblabs.openspec.dialogs;

import com.intellij.icons.AllIcons;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.ui.JBColor;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import com.johnnyblabs.openspec.services.AiToolDetectionService;
import com.johnnyblabs.openspec.services.AiToolDetectionService.ToolInfo;
import com.johnnyblabs.openspec.services.AiToolDetectionService.ToolStatus;
import com.johnnyblabs.openspec.services.CliDetectionService;
import com.johnnyblabs.openspec.util.CliRunner;
import com.johnnyblabs.openspec.util.OpenSpecNotifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class ManageAiToolsDialog extends DialogWrapper {

    private final Project project;
    private final SearchTextField searchField;
    private final JPanel toolListPanel;
    private List<ToolInfo> allTools = List.of();
    /** The last scan's built-in add decisions, by tool name, for the Add tooltips. */
    private java.util.Map<String, com.johnnyblabs.openspec.services.AiToolRegistry.BuiltInAdd> builtInAdds = java.util.Map.of();
    /** Bumped by each scan, so an older scan finishing late can't replace a newer one's list. */
    private final java.util.concurrent.atomic.AtomicLong scanGeneration = new java.util.concurrent.atomic.AtomicLong();
    /**
     * The dialog's modality, captured once after {@code init()}. Background results posted without it are held by
     * the platform until this modal dialog closes (see {@code BulkArchiveDialog}).
     */
    private ModalityState dialogModality;
    /** Where the project's skills are, with "Install into this project" for a machine-scoped project. */
    private final SkillsScopeLine scopeLine;
    /** The subtitle, reworded once the scan knows the CLI line. */
    private @Nullable JBLabel subtitleLabel;

    public ManageAiToolsDialog(Project project) {
        super(project, false);
        this.project = project;
        setTitle("Manage AI Tools");
        setOKButtonText("Close");

        searchField = new SearchTextField(false);
        searchField.addDocumentListener(new com.intellij.ui.DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull javax.swing.event.DocumentEvent e) {
                filterTools(searchField.getText().trim());
            }
        });

        toolListPanel = new JPanel();
        toolListPanel.setLayout(new BoxLayout(toolListPanel, BoxLayout.Y_AXIS));
        scopeLine = new SkillsScopeLine(project, this::installIntoProject);

        init();
        // A headless peer has no content panel; fall back rather than fail (that path is never modal anyway).
        JComponent content = getContentPanel();
        dialogModality = content != null ? ModalityState.stateForComponent(content) : ModalityState.defaultModalityState();
        loadTools();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setPreferredSize(new Dimension(JBUI.scale(520), JBUI.scale(480)));

        // Header
        JPanel headerPanel = new JPanel(new BorderLayout(JBUI.scale(8), 0));
        headerPanel.setBorder(JBUI.Borders.emptyBottom(8));

        CliDetectionService detected = project.getService(CliDetectionService.class);
        // From the detected version here; with no CLI, the tool scan refines it from the project's layout.
        JBLabel title = new JBLabel(subtitle(detected != null && detected.isAvailable()
                && cli2x(detected.getDetectedVersion(), null)));
        subtitleLabel = title;
        title.setForeground(JBColor.GRAY);
        headerPanel.add(title, BorderLayout.NORTH);

        searchField.getTextEditor().getEmptyText().setText("Filter tools...");
        headerPanel.add(searchField, BorderLayout.CENTER);

        JButton refreshButton = new JButton("Refresh");
        refreshButton.addActionListener(e -> loadTools());
        headerPanel.add(refreshButton, BorderLayout.EAST);

        mainPanel.add(headerPanel, BorderLayout.NORTH);

        // Tool list
        JBScrollPane scrollPane = new JBScrollPane(toolListPanel);
        scrollPane.setBorder(JBUI.Borders.empty());
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        // Footer: the skills scope, then the CLI hint when there is no CLI.
        JPanel footer = new JPanel(new BorderLayout());
        footer.setBorder(JBUI.Borders.emptyTop(8));
        footer.add(scopeLine.component(), BorderLayout.NORTH);
        mainPanel.add(footer, BorderLayout.SOUTH);
        CliDetectionService cliService = project.getService(CliDetectionService.class);
        if (cliService == null || !cliService.isAvailable()) {
            JBLabel cliHint = new JBLabel(
                    "<html>" + com.johnnyblabs.openspec.util.CliInstallHints.manageToolsHintBody(com.intellij.openapi.util.SystemInfo.isWindows) + "</html>");
            cliHint.setForeground(JBColor.GRAY);
            cliHint.setFont(cliHint.getFont().deriveFont(Font.PLAIN, 11f));
            JPanel hint = new JPanel(new BorderLayout());
            hint.setBorder(JBUI.Borders.emptyTop(4));
            hint.add(cliHint, BorderLayout.CENTER);
            hint.add(SetupWizardDialog.installInstructionsLink(), BorderLayout.SOUTH);
            footer.add(hint, BorderLayout.CENTER);
        }

        return mainPanel;
    }

    @Override
    protected Action @NotNull [] createActions() {
        return new Action[]{getOKAction()};
    }

    /**
     * Rescans the tools on a pooled thread (it reads the project and the home folder) and fills the list under the
     * dialog's modality; "Loading…" until then. A newer scan wins over an older one that finishes later.
     */
    private void loadTools() {
        AiToolDetectionService toolService = project.getService(AiToolDetectionService.class);
        if (dialogModality != null) scopeLine.refresh(dialogModality, ignored -> isDisposed());
        if (toolService == null || dialogModality == null) {
            allTools = List.of();
            filterTools("");
            return;
        }
        showLoading();
        long generation = scanGeneration.incrementAndGet();
        CliDetectionService cli = project.getService(CliDetectionService.class);
        String version = cli != null && cli.isAvailable() ? cli.getDetectedVersion() : null;
        String basePath = project.getBasePath();
        scanThenPost(toolService, version, basePath == null ? null : java.nio.file.Path.of(basePath), dialogModality,
                ignored -> isDisposed() || project.isDisposed(), scan -> {
                    if (scanGeneration.get() != generation) return;
                    allTools = scan.tools();
                    builtInAdds = scan.builtInAdds();
                    if (subtitleLabel != null) subtitleLabel.setText(subtitle(scan.cli2x()));
                    filterTools(searchField.getText().trim());
                });
    }

    /**
     * What a scan found: the tools to list, whether the CLI line is 2.x (with no CLI, by the project's layout), and
     * each tool's built-in add decision by name (it reads the project, so the tooltips don't).
     */
    record Scan(List<ToolInfo> tools, boolean cli2x,
                java.util.Map<String, com.johnnyblabs.openspec.services.AiToolRegistry.BuiltInAdd> builtInAdds) {
    }

    /** Runs {@code service}'s scan on a pooled thread, then hands it to {@code sink} on the EDT under {@code modality}. */
    @VisibleForTesting
    static void scanThenPost(AiToolDetectionService service, @Nullable String cliVersion,
                             @Nullable java.nio.file.Path project, ModalityState modality, Condition<?> expired,
                             java.util.function.Consumer<Scan> sink) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            Scan scan;
            try {
                service.detect();
                List<ToolInfo> tools = service.getAllToolsWithStatus();
                java.util.Map<String, com.johnnyblabs.openspec.services.AiToolRegistry.BuiltInAdd> adds = new java.util.HashMap<>();
                for (ToolInfo tool : tools) {
                    var entry = com.johnnyblabs.openspec.services.AiToolRegistry.byName(tool.name());
                    var decision = entry == null || project == null ? null
                            : com.johnnyblabs.openspec.services.AiToolRegistry.builtInAdd(entry, project);
                    if (decision != null) adds.put(tool.name(), decision);
                }
                scan = new Scan(tools, cli2x(cliVersion, project), adds);
            } catch (RuntimeException e) {
                // A scan that fails still ends "Loading…": an empty list, the error in the log.
                com.intellij.openapi.diagnostic.Logger.getInstance(ManageAiToolsDialog.class).warn("Tool scan failed", e);
                scan = new Scan(List.of(), false, java.util.Map.of());
            }
            Scan result = scan;
            ApplicationManager.getApplication().invokeLater(() -> sink.accept(result), modality, expired);
        });
    }

    private void showLoading() {
        toolListPanel.removeAll();
        toolListPanel.add(new JBLabel("Loading…"));
        toolListPanel.revalidate();
        toolListPanel.repaint();
    }

    /**
     * "Install into this project": ticks the tool list off the EDT, opens it over this dialog, and on Install runs
     * {@code init --tools <ticked> --scope project}. Cancelling runs nothing.
     */
    private void installIntoProject() {
        String basePath = project.getBasePath();
        if (basePath == null) return;
        Component parent = getContentPanel();
        String preferred = com.johnnyblabs.openspec.settings.OpenSpecSettings.getInstance(project).getPreferredTool();
        Condition<?> expired = ignored -> isDisposed() || project.isDisposed();
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            List<com.johnnyblabs.openspec.services.InstallToolTicks.Row> rows =
                    com.johnnyblabs.openspec.services.InstallToolTicks.rows(java.nio.file.Path.of(basePath),
                            com.johnnyblabs.openspec.services.HomeDirectory.current(), System.getenv("XDG_CONFIG_HOME"),
                            preferred);
            ApplicationManager.getApplication().invokeLater(() -> {
                InstallToolListDialog list = new InstallToolListDialog(parent, rows);
                if (!list.showAndGet()) return;
                runCliAction(installArgs(list.ticked()), "Installing skills into this project", null, null);
            }, dialogModality, expired);
        });
    }

    /** The install's command: the ticked tools, into the project. */
    static com.johnnyblabs.openspec.util.CliArgs installArgs(List<com.johnnyblabs.openspec.services.AiToolRegistry.AiTool> ticked) {
        return com.johnnyblabs.openspec.util.CliCommands.initInProject(
                com.johnnyblabs.openspec.services.InstallToolTicks.toolsArg(ticked));
    }

    private void filterTools(String query) {
        toolListPanel.removeAll();

        String lowerQuery = query.toLowerCase();
        List<ToolInfo> filtered = allTools.stream()
                .filter(t -> query.isEmpty() || t.name().toLowerCase().contains(lowerQuery))
                .toList();

        // Group by status
        List<ToolInfo> configured = filtered.stream().filter(t -> t.status() == ToolStatus.CONFIGURED).toList();
        List<ToolInfo> detected = filtered.stream().filter(t -> t.status() == ToolStatus.DETECTED).toList();
        List<ToolInfo> available = filtered.stream().filter(t -> t.status() == ToolStatus.AVAILABLE).toList();

        if (!configured.isEmpty()) {
            addSectionHeader("CONFIGURED");
            configured.forEach(this::addToolRow);
        }
        if (!detected.isEmpty()) {
            addSectionHeader("DETECTED — needs OpenSpec skills");
            detected.forEach(this::addToolRow);
        }
        if (!available.isEmpty()) {
            addSectionHeader("AVAILABLE");
            available.forEach(this::addToolRow);
        }

        if (filtered.isEmpty()) {
            JBLabel empty = new JBLabel("No tools match \"" + query + "\"");
            empty.setForeground(JBColor.GRAY);
            empty.setHorizontalAlignment(SwingConstants.CENTER);
            empty.setBorder(JBUI.Borders.empty(20));
            toolListPanel.add(empty);
        }

        toolListPanel.revalidate();
        toolListPanel.repaint();
    }

    private void addSectionHeader(String text) {
        JBLabel header = new JBLabel(text);
        header.setFont(header.getFont().deriveFont(Font.BOLD, 11f));
        header.setForeground(JBColor.GRAY);
        header.setBorder(JBUI.Borders.empty(12, 4, 4, 4));
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE, JBUI.scale(28)));
        toolListPanel.add(header);
    }

    private void addToolRow(ToolInfo tool) {
        JPanel row = new JPanel(new BorderLayout(JBUI.scale(8), 0));
        row.setBorder(JBUI.Borders.empty(4, 8, 4, 8));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, JBUI.scale(36)));

        // Left: status icon + name
        JPanel namePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(6), 0));
        namePanel.setOpaque(false);

        Icon statusIcon = switch (tool.status()) {
            case CONFIGURED -> AllIcons.General.InspectionsOK;
            case DETECTED -> AllIcons.General.Warning;
            case AVAILABLE -> AllIcons.General.Information;
        };
        namePanel.add(new JBLabel(statusIcon));

        JBLabel nameLabel = new JBLabel(tool.name());
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.PLAIN, 13f));
        namePanel.add(nameLabel);
        if (tool.viaShared()) {
            // Upstream's own label for the folder the tool's skills come from.
            JBLabel via = new JBLabel(VIA_SHARED);
            via.setForeground(JBColor.GRAY);
            via.setFont(via.getFont().deriveFont(Font.PLAIN, 11f));
            namePanel.add(via);
        }

        // Type badge; an unclassified tool gets none.
        String typeText = tool.type().badge();
        if (typeText != null) {
            JBLabel typeBadge = new JBLabel(typeText);
            typeBadge.setFont(typeBadge.getFont().deriveFont(Font.PLAIN, 10f));
            typeBadge.setForeground(JBColor.GRAY);
            typeBadge.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(JBColor.border(), 1),
                    JBUI.Borders.empty(1, 4)));
            namePanel.add(typeBadge);
        }

        row.add(namePanel, BorderLayout.CENTER);

        // Right: action button
        JButton actionButton = createActionButton(tool);
        if (actionButton != null) {
            row.add(actionButton, BorderLayout.EAST);
        }

        // Hover highlight
        row.setOpaque(true);
        row.setBackground(UIManager.getColor("List.background"));
        row.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                row.setBackground(UIManager.getColor("List.selectionInactiveBackground"));
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                row.setBackground(UIManager.getColor("List.background"));
            }
        });

        toolListPanel.add(row);
    }

    private @Nullable JButton createActionButton(ToolInfo tool) {
        CliDetectionService cliService = project.getService(CliDetectionService.class);
        boolean cliAvailable = cliService != null && cliService.isAvailable();

        return switch (tool.status()) {
            case CONFIGURED -> {
                if (!cliAvailable) yield null;
                JButton btn = new JButton("Update");
                btn.setToolTipText("Refresh this tool's OpenSpec skills (and, on CLI 1.x, command files)");
                btn.addActionListener(e -> updateTool(tool, btn));
                yield btn;
            }
            case DETECTED -> {
                JButton btn = new JButton("Configure");
                if (cliAvailable && tool.cliId() != null) {
                    btn.setToolTipText("Generate OpenSpec skills and commands for " + tool.name());
                    btn.addActionListener(e -> runCliAction(() -> addArgs(project, tool.cliId()),
                            "Configuring " + tool.name(), tool.name(), btn));
                } else {
                    btn.setToolTipText("Set as preferred tool (install CLI for full skill generation)");
                    btn.addActionListener(e -> {
                        setPreferredTool(tool.name());
                        btn.setText("Selected");
                        btn.setEnabled(false);
                    });
                }
                yield btn;
            }
            case AVAILABLE -> {
                JButton btn = new JButton("Add");
                if (cliAvailable && tool.cliId() != null) {
                    btn.setToolTipText("Add " + tool.name() + " to your project with OpenSpec skills");
                    btn.addActionListener(e -> runCliAction(() -> addArgs(project, tool.cliId()),
                            "Adding " + tool.name(), tool.name(), btn));
                } else {
                    btn.setToolTipText(addTooltip(tool.name(), builtInAdds.get(tool.name())));
                    btn.addActionListener(e -> {
                        addToolBuiltIn(tool);
                        btn.setText("Added");
                        btn.setEnabled(false);
                    });
                }
                yield btn;
            }
        };
    }

    /**
     * The Update button. {@code openspec update} always covers every configured tool, so on CLI 1.15+ the preview shows
     * the whole plan, with this tool's group first; older CLIs run the update directly.
     */
    private void updateTool(ToolInfo tool, JButton sourceButton) {
        com.johnnyblabs.openspec.services.UpdatePreviewService preview =
                com.johnnyblabs.openspec.services.UpdatePreviewService.getInstance(project);
        if (preview == null || !preview.supportsPreview()) {
            runCliAction(com.johnnyblabs.openspec.util.CliCommands.update(), "Updating " + tool.name(), null, sourceButton);
            return;
        }
        String title = "Updating " + tool.name();
        previewWithBusyButton(preview, tool.cliId(), sourceButton, () -> isDisposed() || project.isDisposed(), result -> {
            if (result.isSuccess()) {
                String basePath = project.getBasePath();
                VirtualFile projectDir = basePath != null
                        ? LocalFileSystem.getInstance().findFileByPath(basePath) : null;
                refreshThenPost(projectDir, dialogModality, ignored -> isDisposed() || project.isDisposed(), () -> {
                    loadTools();
                    OpenSpecNotifier.info(project, "AI Tools", title + " — done");
                });
            } else {
                OpenSpecNotifier.warn(project, "AI Tools", title + " failed: " + result.stderr().trim());
            }
        });
    }

    /**
     * Runs the update preview with {@code button} busy until the preview finishes, whatever its outcome, so a second
     * click can't start another preview. After a successful apply the caller's reload rebuilds the row; until then, and
     * on every other outcome, the button is usable again, reading "Retry" after a failed apply. Package-private for tests.
     */
    static void previewWithBusyButton(com.johnnyblabs.openspec.services.UpdatePreviewService preview,
                                      @Nullable String focusTool, JButton button, java.util.function.BooleanSupplier gone,
                                      java.util.function.Consumer<CliRunner.CliResult> onApplied) {
        String idleText = button.getText();
        boolean[] applyFailed = {false};
        button.setEnabled(false);
        button.setText("...");
        preview.previewAndApply(com.johnnyblabs.openspec.services.UpdatePreviewService.Origin.MANAGE_TOOLS, focusTool,
                result -> {
                    if (gone.getAsBoolean()) return;
                    applyFailed[0] = !result.isSuccess();
                    onApplied.accept(result);
                },
                () -> {
                    if (gone.getAsBoolean()) return;
                    button.setEnabled(true);
                    button.setText(applyFailed[0] ? "Retry" : idleText);
                });
    }

    /**
     * The dialog's subtitle: these are agents, and generating inside the IDE is something else. On CLI 2.x OpenSpec
     * writes skills, on 1.x its {@code /opsx} commands.
     */
    static String subtitle(boolean cli2x) {
        return "AI coding agents that run OpenSpec's " + (cli2x ? "skills" : "/opsx commands") + ". "
                + "To generate inside the IDE instead, set up Direct API in Settings.";
    }

    /**
     * What adding or configuring {@code tool} did, the CLI's setup note for it when there is one, and, without Direct
     * API, how to generate inside the IDE instead.
     */
    static String toolSetUpNotice(String tool, boolean directApiConfigured, boolean cli2x, @Nullable String setupNote) {
        String notice = tool + " is set up: OpenSpec wrote its " + (cli2x ? "skills" : "/opsx commands")
                + " to this project. Prompts are labeled \"Copy for " + tool + "\"; paste them into the agent.";
        if (setupNote != null) notice += " Setup required for " + tool + ": " + setupNote;
        return directApiConfigured ? notice : notice + " To generate inside the IDE instead, set up Direct API.";
    }

    /** Whether the CLI line is 2.x: the detected CLI's version, or with none detected, the project's layout. Reads disk. */
    static boolean cli2x(@Nullable String cliVersion, @Nullable java.nio.file.Path project) {
        if (cliVersion != null && !cliVersion.isBlank()) return com.johnnyblabs.openspec.util.CliVersion.atLeast(cliVersion, "2.0.0");
        return project != null && com.johnnyblabs.openspec.services.DetectionRules.fromDisk(project).is2x();
    }



    /**
     * Says a CLI action succeeded: for setting up a tool ({@code setUpTool} non-null), what that did and, without Direct
     * API, a "Set up Direct API…" action; otherwise that {@code title} is done.
     */
    static void notifySucceeded(Project project, String title, @Nullable String setUpTool, boolean directApiConfigured,
                                boolean cli2x, @Nullable String setupNote) {
        if (setUpTool == null) {
            OpenSpecNotifier.info(project, "AI Tools", title + " \u2014 done");
            return;
        }
        String content = toolSetUpNotice(setUpTool, directApiConfigured, cli2x, setupNote);
        if (directApiConfigured) {
            OpenSpecNotifier.notify(project, OpenSpecNotifier.GROUP_SYSTEM, "AI Tools", content, NotificationType.INFORMATION);
        } else {
            OpenSpecNotifier.notify(project, OpenSpecNotifier.GROUP_SYSTEM, "AI Tools", content, NotificationType.INFORMATION,
                    new NotificationAction(SET_UP_DIRECT_API) {
                        @Override
                        public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
                            // The notice outlives the dialog, so resolve the project now rather than trusting the capture.
                            Project target = e.getProject() != null ? e.getProject() : project;
                            if (!target.isDisposed()) {
                                com.johnnyblabs.openspec.actions.OpenSpecOpenSettingsAction.open(target);
                            }
                            notification.expire();
                        }
                    });
        }
    }

    static final String SET_UP_DIRECT_API = "Set up Direct API\u2026";

    private boolean directApiConfigured() {
        com.johnnyblabs.openspec.ai.DirectApiService api = project.getService(com.johnnyblabs.openspec.ai.DirectApiService.class);
        return api != null && api.isConfigured();
    }

    /**
     * Add and Configure's command: the project scope, unless the project uses only the machine's skills, where no
     * scope is passed so CLI 2.1+ keeps it on the machine. Reads the project and the home snapshot: off the EDT.
     */
    static com.johnnyblabs.openspec.util.CliArgs addArgs(Project project, String cliId) {
        return addArgs(project.getBasePath() == null ? null : java.nio.file.Path.of(project.getBasePath()),
                com.johnnyblabs.openspec.services.GlobalSkillsCache.getInstance(project).snapshot(), cliId);
    }

    /** {@link #addArgs(Project, String)} for a project directory and home snapshot already in hand. */
    static com.johnnyblabs.openspec.util.CliArgs addArgs(@Nullable java.nio.file.Path project,
                                                         com.johnnyblabs.openspec.services.HomeSkills home, String cliId) {
        boolean onMachine = project != null && com.johnnyblabs.openspec.services.ProjectSkillsScope.of(project, home)
                == com.johnnyblabs.openspec.services.ProjectSkillsScope.ON_MACHINE;
        return onMachine ? com.johnnyblabs.openspec.util.CliCommands.initKeepingScope(cliId)
                : com.johnnyblabs.openspec.util.CliCommands.initInProject(cliId);
    }

    /** Runs the command {@code args} builds, built on the background thread so it may read the disk. */
    private void runCliAction(java.util.function.Supplier<com.johnnyblabs.openspec.util.CliArgs> args, String title,
                              @Nullable String setUpTool, @Nullable JButton sourceButton) {
        setBusy(sourceButton, true);

        ProgressManager.getInstance().run(new Task.Backgroundable(project, title, false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                Condition<?> expired = ignored -> isDisposed() || project.isDisposed();
                try {
                    com.johnnyblabs.openspec.util.CliArgs command = args.get();
                    CliRunner.CliResult result = CliRunner.run(project, command);
                    if (result.isSuccess()) {
                        // The wording reads the detected version and, with none, the project's layout: decided here.
                        CliDetectionService cli = project.getService(CliDetectionService.class);
                        String version = cli != null && cli.isAvailable() ? cli.getDetectedVersion() : null;
                        boolean twoX = cli2x(version, project.getBasePath() == null ? null : java.nio.file.Path.of(project.getBasePath()));
                        com.johnnyblabs.openspec.services.AiToolRegistry.AiTool entry =
                                setUpTool == null ? null : com.johnnyblabs.openspec.services.AiToolRegistry.byName(setUpTool);
                        String note = entry == null ? null
                                : com.johnnyblabs.openspec.services.AiToolRegistry.setupNoteAfter(entry, version, command);
                        // The CLI wrote files outside the VFS. A synchronous refresh is illegal here (off the EDT),
                        // so refresh the project directory asynchronously and reload the list once it finishes.
                        // loadTools() rebuilds every row, which also replaces the "..." button.
                        String basePath = project.getBasePath();
                        VirtualFile projectDir = basePath != null
                                ? LocalFileSystem.getInstance().findFileByPath(basePath) : null;
                        refreshThenPost(projectDir, dialogModality, expired, () -> {
                            loadTools();
                            notifySucceeded(project, title, setUpTool, directApiConfigured(), twoX, note);
                        });
                    } else {
                        ApplicationManager.getApplication().invokeLater(() -> {
                            OpenSpecNotifier.warn(project, "AI Tools",
                                    title + " failed: " + result.stderr().trim());
                            setBusy(sourceButton, false);
                        }, dialogModality, expired);
                    }
                } catch (ProcessCanceledException ex) {
                    // Cancellation must propagate, but the button still comes back.
                    ApplicationManager.getApplication().invokeLater(() -> {
                        setBusy(sourceButton, false);
                    }, dialogModality, expired);
                    throw ex;
                } catch (Exception ex) {
                    // Any failure, not only a CLI error, must restore the button and tell the user.
                    String reason = ex instanceof CliRunner.CliException ? ex.getMessage() : ex.toString();
                    ApplicationManager.getApplication().invokeLater(() -> {
                        OpenSpecNotifier.error(project, "AI Tools", title + " failed: " + reason);
                        setBusy(sourceButton, false);
                    }, dialogModality, expired);
                }
            }
        });
    }

    static final String VIA_SHARED = "via shared .agents skills";

    /** The button a run started from: "..." while it runs, "Retry" after a failure. The install link has none. */
    private static void setBusy(@Nullable JButton button, boolean busy) {
        if (button == null) return;
        button.setEnabled(!busy);
        button.setText(busy ? "..." : "Retry");
    }

    /** {@link #runCliAction(java.util.function.Supplier, String, String, JButton)} for a fixed command. */
    private void runCliAction(com.johnnyblabs.openspec.util.CliArgs args, String title, @Nullable String setUpTool,
                              JButton sourceButton) {
        runCliAction(() -> args, title, setUpTool, sourceButton);
    }

    /**
     * Refreshes {@code dir} asynchronously, then runs {@code post} on the EDT under {@code modality}. Safe to call from
     * a background thread (never while holding a read action). The modality must be passed to the refresh itself:
     * {@code LocalFileSystem.refreshFiles} uses the calling thread's default modality, which on a
     * {@code Task.Backgroundable} is non-modal, so the refresh, and anything chained after it, would be held until the
     * dialog closes. With no directory, {@code post} is scheduled directly.
     */
    @VisibleForTesting
    static void refreshThenPost(@Nullable VirtualFile dir, ModalityState modality, Condition<?> expired, Runnable post) {
        Runnable onEdt = () -> ApplicationManager.getApplication().invokeLater(post, modality, expired);
        if (dir == null) {
            onEdt.run();
            return;
        }
        RefreshQueue.getInstance().refresh(true, true, onEdt, modality, List.of(dir));
    }

    private void addToolBuiltIn(ToolInfo tool) {
        com.johnnyblabs.openspec.services.AiToolRegistry.AiTool entry =
                com.johnnyblabs.openspec.services.AiToolRegistry.byName(tool.name());
        if (entry == null) return;
        com.johnnyblabs.openspec.services.AiToolRegistry.BuiltInAdd done = applyBuiltInAdd(project, entry, this);
        if (done == null || done == com.johnnyblabs.openspec.services.AiToolRegistry.BuiltInAdd.NEEDS_CLI) return;
        // syncRefresh() is legal only because this runs on the EDT (a button handler); never call it from runCliAction's
        // background task.
        VirtualFileManager.getInstance().syncRefresh();
        setPreferredTool(tool.name());
        loadTools();
        OpenSpecNotifier.info(project, "AI Tools",
                tool.name() + " directory created. Install the OpenSpec CLI to generate skills.");
    }

    /**
     * Adds {@code tool} without the CLI as {@link com.johnnyblabs.openspec.services.AiToolRegistry#builtInAdd} allows:
     * creates its folder, writes the single-id tool list only when the decision says so (never on a 2.x project, never
     * over an existing list), or, when only the CLI can record the tool, writes nothing and says so. Returns the
     * decision, or {@code null} when nothing could be done. EDT (a button handler): a handful of file checks.
     */
    @VisibleForTesting
    static com.johnnyblabs.openspec.services.AiToolRegistry.@Nullable BuiltInAdd applyBuiltInAdd(
            Project project, com.johnnyblabs.openspec.services.AiToolRegistry.AiTool entry, Object requestor) {
        String basePath = project.getBasePath();
        if (basePath == null) return null;
        String dirName = com.johnnyblabs.openspec.services.AiToolRegistry.builtInDir(entry);
        if (dirName == null) return null;
        com.johnnyblabs.openspec.services.AiToolRegistry.BuiltInAdd decision =
                com.johnnyblabs.openspec.services.AiToolRegistry.builtInAdd(entry, java.nio.file.Path.of(basePath));
        if (decision == com.johnnyblabs.openspec.services.AiToolRegistry.BuiltInAdd.NEEDS_CLI) {
            OpenSpecNotifier.warn(project, "AI Tools", builtInNeedsCliNotice(entry.name()));
            return decision;
        }
        boolean writeList = decision == com.johnnyblabs.openspec.services.AiToolRegistry.BuiltInAdd.CREATE_DIR_AND_LIST
                && com.johnnyblabs.openspec.services.AiToolRegistry.builtInMarker(entry) != null;
        try {
            com.intellij.openapi.vfs.VirtualFile baseDir =
                    com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath(basePath);
            if (baseDir == null) {
                OpenSpecNotifier.error(project, "AI Tools", "Project directory not found");
                return null;
            }
            com.intellij.openapi.application.WriteAction.run(() -> {
                com.intellij.openapi.vfs.VirtualFile dir = com.intellij.openapi.vfs.VfsUtil.createDirectoryIfMissing(baseDir, dirName);
                if (writeList && dir != null && dir.findChild(".openspec-target") == null) {
                    com.intellij.openapi.vfs.VfsUtil.saveText(dir.createChildData(requestor, ".openspec-target"), entry.id() + "\n");
                }
            });
            return decision;
        } catch (Exception ex) {
            OpenSpecNotifier.error(project, "AI Tools",
                    "Failed to create " + dirName + ": " + ex.getMessage());
            return null;
        }
    }

    /** Why a tool can't be added without the CLI here: only the CLI records it in the project's tool list. */
    @VisibleForTesting
    static String builtInNeedsCliNotice(String toolName) {
        return toolName + " can't be set up in this project without the OpenSpec CLI: the CLI records it in the "
                + "project's list of AI tools (.agents/skills/.openspec-target). Install the OpenSpec CLI, then add "
                + toolName + " again.";
    }

    /**
     * The Add button's tooltip without the CLI. Only the two tools the tool list alone records need a disk check, so
     * the other rows cost nothing.
     */
    @VisibleForTesting
    static String addTooltip(Project project, ToolInfo tool) {
        var entry = com.johnnyblabs.openspec.services.AiToolRegistry.byName(tool.name());
        String basePath = project.getBasePath();
        var decision = entry == null || basePath == null ? null
                : com.johnnyblabs.openspec.services.AiToolRegistry.builtInAdd(entry, java.nio.file.Path.of(basePath));
        return addTooltip(tool.name(), decision);
    }

    /** The Add button's tooltip without the CLI, from the decision the scan made off the EDT. */
    static String addTooltip(String toolName,
                             com.johnnyblabs.openspec.services.AiToolRegistry.@Nullable BuiltInAdd decision) {
        if (decision == com.johnnyblabs.openspec.services.AiToolRegistry.BuiltInAdd.NEEDS_CLI) {
            return "Install the OpenSpec CLI to add " + toolName + " to this project";
        }
        return "Create tool directory and set as preferred (install CLI for skill generation)";
    }

    private void setPreferredTool(String toolName) {
        com.johnnyblabs.openspec.settings.OpenSpecSettings settings =
                com.johnnyblabs.openspec.settings.OpenSpecSettings.getInstance(project);
        settings.setPreferredTool(toolName);
    }
}
