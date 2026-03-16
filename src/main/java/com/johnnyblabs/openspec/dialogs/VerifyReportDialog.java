package com.johnnyblabs.openspec.dialogs;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import com.johnnyblabs.openspec.model.VerificationFinding;
import com.johnnyblabs.openspec.model.VerificationFinding.Dimension;
import com.johnnyblabs.openspec.model.VerificationReport;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class VerifyReportDialog extends DialogWrapper {

    private final VerificationReport report;

    public VerifyReportDialog(Project project, VerificationReport report) {
        super(project, false);
        this.report = report;
        setTitle("Verification Report: " + report.getChangeName());
        init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setPreferredSize(new java.awt.Dimension(500, 400));

        // Summary header
        JBLabel summary = new JBLabel(report.getSummary());
        summary.setFont(summary.getFont().deriveFont(Font.BOLD, 14f));
        summary.setIcon(report.isClean() ? AllIcons.General.InspectionsOK :
                report.hasCritical() ? AllIcons.General.Error : AllIcons.General.Warning);
        summary.setBorder(JBUI.Borders.empty(8));
        panel.add(summary, BorderLayout.NORTH);

        if (report.isClean()) {
            JBLabel cleanMsg = new JBLabel("<html><body style='width:" + JBUI.scale(400) + "px'>" +
                    "All checks passed. The change is ready to archive.</body></html>");
            cleanMsg.setBorder(JBUI.Borders.empty(16));
            panel.add(cleanMsg, BorderLayout.CENTER);
            return panel;
        }

        // Findings grouped by dimension
        JPanel findingsPanel = new JPanel();
        findingsPanel.setLayout(new BoxLayout(findingsPanel, BoxLayout.Y_AXIS));
        findingsPanel.setBorder(JBUI.Borders.empty(8));

        for (Dimension dim : Dimension.values()) {
            List<VerificationFinding> findings = report.getFindings(dim);
            if (findings.isEmpty()) continue;

            JBLabel dimLabel = new JBLabel(dim.name());
            dimLabel.setFont(dimLabel.getFont().deriveFont(Font.BOLD, 12f));
            dimLabel.setBorder(JBUI.Borders.emptyTop(8));
            dimLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            findingsPanel.add(dimLabel);

            for (VerificationFinding finding : findings) {
                JBLabel label = new JBLabel(formatFinding(finding));
                label.setIcon(severityIcon(finding.severity()));
                label.setForeground(severityColor(finding.severity()));
                label.setBorder(JBUI.Borders.emptyLeft(16));
                label.setAlignmentX(Component.LEFT_ALIGNMENT);
                findingsPanel.add(label);
            }
        }

        panel.add(new JBScrollPane(findingsPanel), BorderLayout.CENTER);
        return panel;
    }

    private String formatFinding(VerificationFinding finding) {
        StringBuilder sb = new StringBuilder();
        sb.append("<html>").append(finding.description());
        if (finding.filePath() != null) {
            String fileName = finding.filePath().substring(finding.filePath().lastIndexOf('/') + 1);
            sb.append(" <i>(").append(fileName).append(")</i>");
        }
        sb.append("</html>");
        return sb.toString();
    }

    private Icon severityIcon(VerificationFinding.Severity severity) {
        return switch (severity) {
            case CRITICAL -> AllIcons.General.Error;
            case WARNING -> AllIcons.General.Warning;
            case SUGGESTION -> AllIcons.General.Information;
        };
    }

    private Color severityColor(VerificationFinding.Severity severity) {
        return switch (severity) {
            case CRITICAL -> JBColor.RED;
            case WARNING -> JBColor.ORANGE;
            case SUGGESTION -> JBColor.foreground();
        };
    }

    @Override
    protected Action[] createActions() {
        return new Action[]{getOKAction()};
    }
}
