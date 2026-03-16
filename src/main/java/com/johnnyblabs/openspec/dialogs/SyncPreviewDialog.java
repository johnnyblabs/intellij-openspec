package com.johnnyblabs.openspec.dialogs;

import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.DiffManager;
import com.intellij.diff.DiffRequestPanel;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.util.ui.JBUI;
import com.johnnyblabs.openspec.model.SpecSyncResult;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class SyncPreviewDialog extends DialogWrapper {

    private final Project project;
    private final List<SpecSyncResult> results;
    private final List<DiffRequestPanel> diffPanels = new ArrayList<>();

    public SyncPreviewDialog(Project project, List<SpecSyncResult> results) {
        super(project, true);
        this.project = project;
        this.results = results;
        setTitle("Sync Specs Preview");
        setOKButtonText("Apply");
        setCancelButtonText("Cancel");
        init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setPreferredSize(JBUI.size(800, 600));

        // Collect all warnings
        List<String> allWarnings = new ArrayList<>();
        for (SpecSyncResult r : results) {
            allWarnings.addAll(r.warnings());
        }

        // Warnings panel at top
        if (!allWarnings.isEmpty()) {
            JPanel warningsPanel = new JPanel();
            warningsPanel.setLayout(new BoxLayout(warningsPanel, BoxLayout.Y_AXIS));
            warningsPanel.setBorder(JBUI.Borders.compound(
                    JBUI.Borders.customLine(JBColor.YELLOW, 0, 0, 1, 0),
                    JBUI.Borders.empty(8)));
            JBLabel header = new JBLabel("Warnings:");
            header.setForeground(JBColor.ORANGE);
            header.setAlignmentX(Component.LEFT_ALIGNMENT);
            warningsPanel.add(header);
            for (String w : allWarnings) {
                JBLabel label = new JBLabel("  " + w);
                label.setForeground(JBColor.ORANGE);
                label.setAlignmentX(Component.LEFT_ALIGNMENT);
                warningsPanel.add(label);
            }
            mainPanel.add(warningsPanel, BorderLayout.NORTH);
        }

        // Filter to only results with actual changes
        List<SpecSyncResult> changed = results.stream()
                .filter(SpecSyncResult::hasChanges)
                .toList();

        if (changed.isEmpty()) {
            mainPanel.add(new JBLabel("No spec changes to apply."), BorderLayout.CENTER);
            setOKButtonText("OK");
            return mainPanel;
        }

        if (changed.size() == 1) {
            // Single diff — no tabs needed
            DiffRequestPanel panel = createDiffPanel(changed.getFirst());
            mainPanel.add(panel.getComponent(), BorderLayout.CENTER);
        } else {
            // Multiple capabilities — use tabs
            JBTabbedPane tabs = new JBTabbedPane();
            for (SpecSyncResult r : changed) {
                DiffRequestPanel panel = createDiffPanel(r);
                tabs.addTab(r.capabilityName(), panel.getComponent());
            }
            mainPanel.add(tabs, BorderLayout.CENTER);
        }

        return mainPanel;
    }

    private DiffRequestPanel createDiffPanel(SpecSyncResult result) {
        DiffContentFactory factory = DiffContentFactory.getInstance();
        String original = result.originalContent() != null ? result.originalContent() : "";
        String projected = result.projectedContent() != null ? result.projectedContent() : "";

        SimpleDiffRequest request = new SimpleDiffRequest(
                result.capabilityName(),
                factory.create(project, original, PlainTextFileType.INSTANCE),
                factory.create(project, projected, PlainTextFileType.INSTANCE),
                "Current: specs/" + result.capabilityName() + "/spec.md",
                "After sync"
        );

        DiffRequestPanel panel = DiffManager.getInstance().createRequestPanel(project, getDisposable(), null);
        panel.setRequest(request);
        diffPanels.add(panel);
        return panel;
    }
}
