package com.johnnyblabs.openspec.toolwindow;

import com.intellij.icons.AllIcons;
import com.intellij.ide.scratch.ScratchFileService;
import com.intellij.ide.scratch.ScratchRootType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.Alarm;
import com.intellij.util.ui.JBUI;
import com.johnnyblabs.openspec.services.ExploreContextService;
import com.johnnyblabs.openspec.util.OpenSpecNotifier;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.util.List;

/**
 * Tool window panel displaying assembled OpenSpec project context.
 * Provides Copy to Clipboard and Open in Editor actions, and auto-refreshes
 * when files under {@code openspec/} change via a VFS listener with debounce.
 */
public class ExplorePanel extends JPanel implements Disposable {

    private static final int DEBOUNCE_DELAY_MS = 500;

    private final Project project;
    private final JBTextArea textArea;
    private final Alarm debounceAlarm;
    private VirtualFile cachedScratchFile;

    public ExplorePanel(Project project) {
        super(new BorderLayout());
        this.project = project;
        this.debounceAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, this);

        // Toolbar
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        toolbar.setOpaque(false);

        JButton refreshButton = new JButton("Refresh", AllIcons.Actions.Refresh);
        refreshButton.addActionListener(e -> refresh());
        toolbar.add(refreshButton);

        JButton copyButton = new JButton("Copy to Clipboard", AllIcons.Actions.Copy);
        copyButton.addActionListener(e -> copyToClipboard());
        toolbar.add(copyButton);

        JButton openInEditorButton = new JButton("Open in Editor", AllIcons.Actions.EditSource);
        openInEditorButton.addActionListener(e -> openInEditor());
        toolbar.add(openInEditorButton);

        // Text area (read-only)
        textArea = new JBTextArea();
        textArea.setEditable(false);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setFont(JBUI.Fonts.label());
        textArea.setBorder(JBUI.Borders.empty(4));

        JBScrollPane scrollPane = new JBScrollPane(textArea);

        add(toolbar, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);

        // Subscribe to VFS changes filtered to openspec/
        project.getMessageBus().connect(this).subscribe(
                VirtualFileManager.VFS_CHANGES,
                new BulkFileListener() {
                    @Override
                    public void after(@NotNull List<? extends VFileEvent> events) {
                        onVfsChange(events);
                    }
                }
        );

        // Initial load
        refresh();
    }

    /**
     * Refreshes the context display by assembling context on a pooled thread
     * and updating the text area on the EDT.
     */
    public void refresh() {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            ExploreContextService service = project.getService(ExploreContextService.class);
            if (service == null) return;
            String context = service.assembleContext();

            SwingUtilities.invokeLater(() -> {
                textArea.setText(context);
                textArea.setCaretPosition(0);
            });
        });
    }

    private void copyToClipboard() {
        String content = textArea.getText();
        if (content != null && !content.isBlank()) {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(content), null);
            OpenSpecNotifier.info(project, "Explore Context",
                    "Context copied — paste into your AI tool to start exploring.");
        }
    }

    private void openInEditor() {
        String content = textArea.getText();
        if (content == null || content.isBlank()) return;

        ApplicationManager.getApplication().runWriteAction(() -> {
            try {
                if (cachedScratchFile != null && cachedScratchFile.isValid()) {
                    // Reuse existing scratch file
                    cachedScratchFile.setBinaryContent(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                } else {
                    // Create new scratch file
                    cachedScratchFile = ScratchRootType.getInstance().createScratchFile(
                            project,
                            "OpenSpec-Explore.md",
                            PlainTextLanguage.INSTANCE,
                            content,
                            ScratchFileService.Option.create_if_missing
                    );
                }
            } catch (Exception e) {
                OpenSpecNotifier.warn(project, "Explore Context",
                        "Failed to create scratch file: " + e.getMessage());
                return;
            }

            if (cachedScratchFile != null) {
                FileEditorManager.getInstance(project).openFile(cachedScratchFile, true);
            }
        });
    }

    private void onVfsChange(@NotNull List<? extends VFileEvent> events) {
        boolean relevant = events.stream().anyMatch(event -> {
            String path = event.getPath();
            return path.contains("/openspec/");
        });

        if (relevant) {
            // Debounce: reset timer on each new relevant event
            debounceAlarm.cancelAllRequests();
            debounceAlarm.addRequest(this::refresh, DEBOUNCE_DELAY_MS);
        }
    }

    @Override
    public void dispose() {
        // Alarm is disposed automatically since 'this' is its parent Disposable.
        // No additional cleanup needed.
    }
}
