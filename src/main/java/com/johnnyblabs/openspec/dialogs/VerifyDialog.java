package com.johnnyblabs.openspec.dialogs;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import com.johnnyblabs.openspec.model.ArchiveReadinessResult;
import com.johnnyblabs.openspec.model.ArchiveReadinessResult.Category;
import com.johnnyblabs.openspec.model.ArchiveReadinessResult.Finding;
import com.johnnyblabs.openspec.model.ArchiveReadinessResult.Severity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * The single pre-archive <em>Verify</em> dialog (upstream's word — the plugin's former "Compliance
 * Pre-Flight"). It renders the three native archive-readiness states from {@link ArchiveReadinessResult}
 * (READY green, IN_PROGRESS <b>neutral</b>, never red, BLOCKED red), in one of two {@link Form}s:
 * <ul>
 *   <li>{@link Form#REPORT}, from Verify: OK reads <b>Close</b> and never archives, because upstream's verify
 *       workflow doesn't. When the profile includes {@code archive}, a separate <b>Archive…</b> button
 *       ("Archive anyway…" while tasks are open, disabled when blocked) closes with {@link #ARCHIVE_EXIT_CODE}.</li>
 *   <li>{@link Form#ARCHIVE_PREFLIGHT}, from Archive: OK reads <b>Archive</b> ("Archive anyway" while tasks are
 *       open) and is disabled only for a hard validation block, mirroring upstream's bypassable {@code --yes} task
 *       gate.</li>
 * </ul>
 */
public class VerifyDialog extends DialogWrapper {

    /** Which job the dialog does: report on a change, or confirm an archive the user asked for. */
    public enum Form { REPORT, ARCHIVE_PREFLIGHT }

    /** The exit code of the report form's Archive… button. */
    public static final int ARCHIVE_EXIT_CODE = NEXT_USER_EXIT_CODE;

    private final ArchiveReadinessResult result;
    private final Form form;
    private final boolean archiveOffered;

    /** The Archive action's pre-flight: OK archives. */
    public VerifyDialog(@NotNull Project project, @NotNull ArchiveReadinessResult result) {
        this(project, result, Form.ARCHIVE_PREFLIGHT, true);
    }

    /** Verify's report: OK closes; {@code archiveOffered} (the profile includes {@code archive}) adds Archive…. */
    public static VerifyDialog report(@NotNull Project project, @NotNull ArchiveReadinessResult result,
                                      boolean archiveOffered) {
        return new VerifyDialog(project, result, Form.REPORT, archiveOffered);
    }

    private VerifyDialog(@NotNull Project project, @NotNull ArchiveReadinessResult result, Form form,
                         boolean archiveOffered) {
        super(project, false);
        this.result = result;
        this.form = form;
        this.archiveOffered = archiveOffered;

        setTitle("Verify — " + result.getChangeName());
        setOKButtonText(okButtonText(form, result));
        setCancelButtonText("Cancel");

        init();

        setOKActionEnabled(okEnabled(form, result));
    }

    /**
     * Whether the report offers Archive…: the active profile includes {@code archive}. With no profile service, as in a
     * project without OpenSpec configured, it does, as {@code OpenSpecBaseAction} leaves a workflow ungated. It may
     * read the profile from the CLI on first use: call it off the UI thread.
     */
    public static boolean archiveOffered(@NotNull Project project) {
        return archiveOffered(project.getService(com.johnnyblabs.openspec.services.WorkflowProfileService.class));
    }

    /** {@link #archiveOffered(Project)} for a given profile service, or {@code null} for none. */
    static boolean archiveOffered(@Nullable com.johnnyblabs.openspec.services.WorkflowProfileService profile) {
        return profile == null || profile.isWorkflowEnabled("archive");
    }

    /** Whether a report closed with {@code exitCode} asks the caller to archive: only its Archive… button does. */
    public static boolean archiveRequested(int exitCode) {
        return exitCode == ARCHIVE_EXIT_CODE;
    }

    /** The OK label: "Close" for a report; for the pre-flight, {@link #okButtonText(ArchiveReadinessResult)}. */
    static String okButtonText(Form form, ArchiveReadinessResult result) {
        return form == Form.REPORT ? "Close" : okButtonText(result);
    }

    /** OK is always enabled on a report; on the pre-flight only a hard validation block disables it. */
    static boolean okEnabled(Form form, ArchiveReadinessResult result) {
        return form == Form.REPORT || !result.isHardBlocked();
    }

    /**
     * The report's Archive… label, or {@code null} when there is no such button: not a report, or the profile has no
     * {@code archive}. "Archive anyway…" while tasks are open. Enablement is {@link #archiveEnabled}.
     */
    static @Nullable String archiveButtonText(Form form, ArchiveReadinessResult result, boolean archiveOffered) {
        if (form != Form.REPORT || !archiveOffered) return null;
        return result.classify() == ArchiveReadinessResult.ArchiveReadiness.IN_PROGRESS
                ? "Archive anyway…" : "Archive…";
    }

    /** The report's Archive… button is disabled only for a hard validation block. */
    static boolean archiveEnabled(ArchiveReadinessResult result) {
        return !result.isHardBlocked();
    }

    @Override
    protected Action @NotNull [] createActions() {
        if (form != Form.REPORT) return super.createActions();
        String archive = archiveButtonText(form, result, archiveOffered);
        if (archive == null) return new Action[] {getOKAction()};
        Action archiveAction = new DialogWrapperAction(archive) {
            @Override
            protected void doAction(java.awt.event.ActionEvent e) {
                close(ARCHIVE_EXIT_CODE);
            }
        };
        archiveAction.setEnabled(archiveEnabled(result));
        return new Action[] {archiveAction, getOKAction()};
    }

    /**
     * The pre-flight's OK label for a result. IN_PROGRESS reads "Archive anyway" (the bypassable soft
     * gate); every other state reads "Archive". Pure so the dialog's contract is unit-testable
     * without a running IDE. Enablement is separate: {@link #okEnabled}.
     */
    static String okButtonText(ArchiveReadinessResult result) {
        return result.classify() == ArchiveReadinessResult.ArchiveReadiness.IN_PROGRESS
                ? "Archive anyway" : "Archive";
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(JBUI.Borders.empty(8));

        // Status header — native state message + color (IN_PROGRESS is neutral, never red).
        JBLabel statusLabel = new JBLabel(result.stateMessage());
        statusLabel.setIcon(stateIcon(result));
        statusLabel.setForeground(stateColor(result));
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

    /** Header icon per state: OK for ready, a neutral Information "i" for in-progress, Error for blocked. */
    private static Icon stateIcon(ArchiveReadinessResult result) {
        return switch (result.classify()) {
            case READY -> AllIcons.General.InspectionsOK;
            case IN_PROGRESS -> AllIcons.General.Information;
            case BLOCKED -> AllIcons.General.Error;
        };
    }

    /**
     * Header color per state. The load-bearing choice: {@code IN_PROGRESS} is <b>neutral</b>
     * (foreground), never red — a valid-but-unfinished change is not a defect. Lives here (the sole
     * consumer) rather than on the model, keeping {@link ArchiveReadinessResult} UI-free.
     */
    static Color stateColor(ArchiveReadinessResult result) {
        return switch (result.classify()) {
            case READY -> new JBColor(new Color(0, 128, 0), new Color(100, 210, 100));
            case IN_PROGRESS -> JBColor.foreground();
            case BLOCKED -> JBColor.RED;
        };
    }

    private JPanel createCategorySection(Category category) {
        JPanel section = new JPanel(new BorderLayout());
        section.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.setBorder(JBUI.Borders.emptyBottom(8));

        List<Finding> findings = result.getFindings(category);
        boolean passes = result.categoryPasses(category);

        // Header with pass/fail icon
        String icon = passes ? "✓" : "✗"; // ✓ or ✗
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
