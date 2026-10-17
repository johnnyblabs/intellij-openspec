package com.johnnyblabs.openspec.dialogs;

import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.model.Change;

import java.util.List;

/** Test-only bridge to {@link BulkArchiveDialog}'s package-private archiving seams, for tests in other packages. */
public final class BulkArchiveDialogTestAccess {

    private BulkArchiveDialogTestAccess() {
    }

    public static List<String> productionCheck(Project project, String changeName) throws Exception {
        return BulkArchiveDialog.Archiver.forProject(project).check(changeName);
    }

    /** Runs the real archive loop with the production archiver for one change; returns that row's final status. */
    public static String archiveWithProductionArchiver(Project project, Change change, ModalityState modality) {
        BulkArchiveDialog.ChangeRow row = new BulkArchiveDialog.ChangeRow(change);
        BulkArchiveDialog.archiveAll(List.of(row), modality, BulkArchiveDialog.Archiver.forProject(project),
                () -> { }, (i, name) -> { });
        return row.archiveStatus;
    }
}
