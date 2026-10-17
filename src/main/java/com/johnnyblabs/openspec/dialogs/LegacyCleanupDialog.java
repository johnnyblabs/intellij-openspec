package com.johnnyblabs.openspec.dialogs;

import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.ActionLink;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import com.johnnyblabs.openspec.util.UpdateOutputParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Consent dialog for the {@code openspec update} legacy-file cleanup: every file the CLI
 * listed appears as a checkbox next to a link that opens it for inspection. The dialog quotes the
 * guidance line the CLI printed, and the boxes start checked only when that line certifies the files
 * hold no user content (CLIs before 1.14); from 1.14 the CLI warns to back up custom content, so they
 * start unchecked and deletion is an explicit choice. Three exits, none destructive
 * by default: Remove Selected (surgical, undoable), Run in Terminal (the CLI's own
 * interactive flow), Not Now (dismiss without re-nagging while the set is unchanged).
 */
public class LegacyCleanupDialog extends DialogWrapper {

    public enum Outcome {REMOVE_SELECTED, RUN_IN_TERMINAL, NOT_NOW}

    private static final int RUN_IN_TERMINAL_EXIT_CODE = NEXT_USER_EXIT_CODE;

    private final Project project;
    private final List<String> files;
    private final @Nullable String guidance;
    private final boolean preselect;
    private final List<JCheckBox> checkboxes = new ArrayList<>();
    private String introHtml;

    public LegacyCleanupDialog(Project project, UpdateOutputParser.LegacyCleanup block) {
        super(project, false);
        this.project = project;
        this.files = block.files();
        this.guidance = block.guidance();
        this.preselect = block.certifiesNoUserContent();
        setTitle("OpenSpec Legacy File Cleanup");
        setOKButtonText("Remove Selected");
        setCancelButtonText("Not Now");
        init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel list = new JPanel();
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        for (String path : files) {
            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(4), 0));
            JCheckBox box = new JCheckBox("", preselect);
            checkboxes.add(box);
            row.add(box);
            row.add(new ActionLink(path, (java.awt.event.ActionListener) e -> openFile(path)));
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            list.add(row);
        }

        JPanel panel = new JPanel(new BorderLayout(0, JBUI.scale(8)));
        introHtml = introHtml(guidance, preselect);
        panel.add(new JBLabel(introHtml), BorderLayout.NORTH);
        panel.add(new JBScrollPane(list), BorderLayout.CENTER);
        panel.add(new JBLabel("<html><small>The plugin never runs <code>openspec update --force</code> on your behalf.</small></html>"),
                BorderLayout.SOUTH);
        return panel;
    }

    @Override
    protected Action @NotNull [] createActions() {
        Action terminal = new DialogWrapperAction("Run in Terminal") {
            @Override
            protected void doAction(java.awt.event.ActionEvent e) {
                close(RUN_IN_TERMINAL_EXIT_CODE);
            }
        };
        return new Action[]{getOKAction(), terminal, getCancelAction()};
    }

    /** The explanation above the list: what the CLI reported, quoting its own guidance line. */
    static String introHtml(@Nullable String guidance, boolean preselect) {
        StringBuilder html = new StringBuilder(
                "<html>OpenSpec has migrated to agent skills and reports these files as leftovers<br>"
                        + "from the previous format.");
        if (guidance != null && !guidance.isBlank()) {
            html.append(" The CLI says: <i>\"").append(StringUtil.escapeXmlEntities(guidance)).append("\"</i>");
        }
        html.append("<br>");
        if (!preselect) {
            html.append("Nothing is selected: open the files to check them, then tick the ones to remove.<br>");
        }
        html.append("Removal happens through the IDE (single undo step, covered by Local History).</html>");
        return html.toString();
    }

    @TestOnly
    String introHtmlForTest() {
        return introHtml;
    }

    /** Shows the dialog and maps the exit code onto the three-way outcome. */
    public Outcome showAndGetOutcome() {
        show();
        int code = getExitCode();
        if (code == OK_EXIT_CODE) return Outcome.REMOVE_SELECTED;
        if (code == RUN_IN_TERMINAL_EXIT_CODE) return Outcome.RUN_IN_TERMINAL;
        return Outcome.NOT_NOW;
    }

    /** The checked subset of the CLI-listed files, in original order. */
    public List<String> getSelectedFiles() {
        List<String> selected = new ArrayList<>();
        for (int i = 0; i < files.size(); i++) {
            if (checkboxes.get(i).isSelected()) {
                selected.add(files.get(i));
            }
        }
        return selected;
    }

    private void openFile(String relativePath) {
        String base = project.getBasePath();
        if (base == null) return;
        VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByPath(base + "/" + relativePath);
        if (vf != null) {
            new OpenFileDescriptor(project, vf).navigate(true);
        }
    }
}
