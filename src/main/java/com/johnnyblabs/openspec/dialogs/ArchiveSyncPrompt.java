package com.johnnyblabs.openspec.dialogs;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

/**
 * The question asked before archiving a change that has delta specs not yet merged into the main specs. The workflow
 * panel, the Archive menu and Verify all ask it, so the choices and their wording are the same everywhere.
 */
public final class ArchiveSyncPrompt {

    private ArchiveSyncPrompt() {
    }

    /**
     * {@code TRUE} to archive and merge the delta specs, {@code FALSE} to archive without merging them, {@code null} to
     * archive nothing. A change with no delta specs has nothing to merge, so it is archived without asking. Must run on
     * the EDT.
     */
    @Nullable
    public static Boolean choose(@Nullable Project project, String changeName, boolean hasDeltaSpecs) {
        if (!hasDeltaSpecs) {
            return Boolean.TRUE;
        }
        int choice = Messages.showYesNoCancelDialog(project, message(changeName), "Unsynced Delta Specs",
                "Sync and Archive", "Archive Without Syncing", "Cancel", Messages.getWarningIcon());
        return fromChoice(choice);
    }

    @VisibleForTesting
    static String message(String changeName) {
        return "\"" + changeName + "\" has delta specs that haven't been synced to main specs.\n\n"
                + "Sync and Archive merges them into the main specs, then archives the change.\n"
                + "Archive Without Syncing archives the change and leaves its delta specs out of the main specs.";
    }

    @VisibleForTesting
    static @Nullable Boolean fromChoice(int choice) {
        if (choice == Messages.YES) {
            return Boolean.TRUE;
        }
        if (choice == Messages.NO) {
            return Boolean.FALSE;
        }
        return null;
    }
}
