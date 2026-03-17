package com.johnnyblabs.openspec.dialogs;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import com.johnnyblabs.openspec.model.ComplianceResult;
import com.johnnyblabs.openspec.model.ComplianceResult.Category;
import com.johnnyblabs.openspec.model.ComplianceResult.Finding;
import com.johnnyblabs.openspec.model.ComplianceResult.Severity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class CompliancePreFlightDialog extends DialogWrapper {

    private final ComplianceResult result;

    public CompliancePreFlightDialog(@NotNull Project project, @NotNull ComplianceResult result) {
        super(project, false);
        this.result = result;

        setTitle("Compliance Check — " + result.getChangeName());
        setOKButtonText("Archive");
        setCancelButtonText("Cancel");

        init();

        // Disable Archive button if there are errors
        if (!result.isCompliant()) {
            setOKActionEnabled(false);
        }
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(JBUI.Borders.empty(8));

        // Status header
        JBLabel statusLabel = new JBLabel();
        if (result.isCompliant() && result.warningCount() == 0) {
            statusLabel.setText("All compliance checks passed.");
            statusLabel.setForeground(new JBColor(new java.awt.Color(0, 128, 0), new java.awt.Color(100, 210, 100)));
        } else if (result.isCompliant()) {
            statusLabel.setText(result.warningCount() + " warning(s) found — archive is allowed.");
            statusLabel.setForeground(JBColor.ORANGE);
        } else {
            statusLabel.setText(result.errorCount() + " error(s) found — archive is blocked.");
            statusLabel.setForeground(JBColor.RED);
        }
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD, 13f));
        statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        statusLabel.setBorder(JBUI.Borders.emptyBottom(12));
        panel.add(statusLabel);

        // Category sections
        for (Category category : Category.values()) {
            panel.add(createCategorySection(category));
        }

        JBScrollPane scrollPane = new JBScrollPane(panel);
        scrollPane.setPreferredSize(new Dimension(500, 350));
        scrollPane.setBorder(JBUI.Borders.empty());
        return scrollPane;
    }

    private JPanel createCategorySection(Category category) {
        JPanel section = new JPanel(new BorderLayout());
        section.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.setBorder(JBUI.Borders.emptyBottom(8));

        List<Finding> findings = result.getFindings(category);
        boolean passes = result.categoryPasses(category);

        // Header with pass/fail icon
        String icon = passes ? "\u2713" : "\u2717"; // ✓ or ✗
        JBColor color = passes ? new JBColor(new java.awt.Color(0, 128, 0), new java.awt.Color(100, 210, 100)) : JBColor.RED;
        JBLabel header = new JBLabel(icon + " " + category.getDisplayName());
        header.setForeground(color);
        header.setFont(header.getFont().deriveFont(Font.BOLD));
        section.add(header, BorderLayout.NORTH);

        if (!findings.isEmpty()) {
            JPanel findingsList = new JPanel();
            findingsList.setLayout(new BoxLayout(findingsList, BoxLayout.Y_AXIS));
            findingsList.setBorder(JBUI.Borders.emptyLeft(16));

            for (Finding finding : findings) {
                String prefix = finding.severity() == Severity.ERROR ? "ERROR: " : "WARNING: ";
                JBLabel findingLabel = new JBLabel(prefix + finding.message());
                findingLabel.setForeground(finding.severity() == Severity.ERROR
                        ? JBColor.RED : JBColor.ORANGE);
                findingLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
                findingsList.add(findingLabel);
            }
            section.add(findingsList, BorderLayout.CENTER);
        }

        return section;
    }
}
