package com.johnnyblabs.openspec.toolwindow;

import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.DiffManager;
import com.intellij.diff.DiffRequestPanel;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.diff.util.DiffUserDataKeys;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Splitter;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import com.johnnyblabs.openspec.services.ArtifactOrchestrationService.PlannedWrite;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.nio.file.Path;
import java.util.List;

/**
 * The review before a Direct API regeneration writes: every file the result would write, each compared with the
 * current one, and one Accept or Reject for all of them. Both sides are read-only; a new file is compared with an empty
 * side. Nothing is written by this dialog; the caller writes on Accept.
 */
public class ArtifactReviewDialog extends DialogWrapper {

    static final String CURRENT_TITLE = "Current";
    static final String NEW_FILE_TITLE = "No file yet";
    static final String GENERATED_TITLE = "Generated";

    private final Project project;
    private final List<PlannedWrite> writes;
    private final List<String> labels;
    private final JBList<String> fileList;
    private final DiffRequestPanel diffPanel;

    public ArtifactReviewDialog(@NotNull Project project, @NotNull String artifactId, @NotNull String changeDir,
                                @NotNull List<PlannedWrite> writes) {
        super(project, true);
        this.project = project;
        this.writes = List.copyOf(writes);
        this.labels = this.writes.stream().map(w -> label(changeDir, w)).toList();
        setTitle("Review Regenerated " + artifactId);
        setOKButtonText("Accept");
        setCancelButtonText("Reject");
        fileList = new JBList<>(labels);
        fileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        diffPanel = DiffManager.getInstance().createRequestPanel(project, getDisposable(), null);
        fileList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) showSelected();
        });
        init();
        fileList.setSelectedIndex(0);
        showSelected();
    }

    /** A file's label in the list: its path inside the change, marked "(new)" when it doesn't exist yet. */
    static String label(String changeDir, PlannedWrite write) {
        Path path = write.path();
        String shown;
        try {
            shown = Path.of(changeDir).relativize(path).toString().replace('\\', '/');
        } catch (IllegalArgumentException e) {
            shown = path.getFileName().toString();
        }
        return write.isNew() ? shown + " (new)" : shown;
    }

    private void showSelected() {
        int index = Math.max(0, fileList.getSelectedIndex());
        if (index < writes.size()) diffPanel.setRequest(request(project, writes.get(index)));
    }

    /** The diff for one file: current (or an empty side for a new file) against the generated text, both read-only. */
    static SimpleDiffRequest request(@Nullable Project project, PlannedWrite write) {
        DiffContentFactory factory = DiffContentFactory.getInstance();
        FileType type = FileTypeManager.getInstance().getFileTypeByFileName(write.path().getFileName().toString());
        DiffContent current = write.isNew() ? factory.createEmpty() : factory.create(project, write.currentText(), type);
        DiffContent generated = factory.create(project, write.newText(), type);
        SimpleDiffRequest request = new SimpleDiffRequest(write.path().getFileName().toString(), current, generated,
                write.isNew() ? NEW_FILE_TITLE : CURRENT_TITLE, GENERATED_TITLE);
        request.putUserData(DiffUserDataKeys.FORCE_READ_ONLY, true);
        return request;
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JComponent diff = diffPanel.getComponent();
        if (writes.size() == 1) {
            diff.setPreferredSize(JBUI.size(900, 560));
            return diff;
        }
        Splitter splitter = new Splitter(false, 0.22f);
        splitter.setFirstComponent(new JBScrollPane(fileList));
        splitter.setSecondComponent(diff);
        splitter.setPreferredSize(JBUI.size(1100, 560));
        return splitter;
    }

    @Override
    public @Nullable JComponent getPreferredFocusedComponent() {
        return writes.size() == 1 ? diffPanel.getPreferredFocusedComponent() : fileList;
    }

    @TestOnly
    List<String> fileLabelsForTest() {
        return labels;
    }
}
