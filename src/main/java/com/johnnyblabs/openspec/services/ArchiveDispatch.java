package com.johnnyblabs.openspec.services;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.model.Change;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/**
 * Runs an archive in the background and reports on the UI thread. The archive may run the OpenSpec CLI, so it never
 * runs on the UI thread; the panel's Archive and the Archive/Verify actions all go through here.
 */
public final class ArchiveDispatch {

    private ArchiveDispatch() {
    }

    /**
     * Saves every open document. Call on the UI thread before an archive: the OpenSpec CLI works on the files on
     * disk, so unsaved edits to a delta or main spec would be left out, and the folder move would invalidate their
     * editors.
     */
    public static void saveBeforeArchive() {
        com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().saveAllDocuments();
    }

    /** Saves open documents, then starts the archive in a background task. Call on the UI thread. */
    public static void start(Project project, Change change, boolean syncSpecs,
                             Consumer<ArchiveJson.Outcome> onDone, Consumer<Exception> onError) {
        saveBeforeArchive();
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Archiving " + change.getName() + "...", false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                ArchiveDispatch.run(project, change, syncSpecs, onDone, onError);
            }
        });
    }

    /** The task's body: archive on the calling (background) thread, then report once on the UI thread. */
    static void run(Project project, Change change, boolean syncSpecs,
                    Consumer<ArchiveJson.Outcome> onDone, Consumer<Exception> onError) {
        try {
            ArchiveJson.Outcome outcome = project.getService(ChangeService.class).archiveChange(change, syncSpecs);
            ApplicationManager.getApplication().invokeLater(() -> onDone.accept(outcome), project.getDisposed());
        } catch (Exception e) {
            ApplicationManager.getApplication().invokeLater(() -> onError.accept(e), project.getDisposed());
        }
    }

    /** The user-facing reason for a failed archive: OpenSpec's message and fix when it refused. */
    public static String reason(Exception e) {
        return e instanceof ArchiveRefusedException refused ? refused.describe() : String.valueOf(e.getMessage());
    }

    /** A one-line summary of a successful archive. */
    public static String summary(String changeName, ArchiveJson.Outcome outcome) {
        StringBuilder s = new StringBuilder(changeName).append(" archived as ").append(outcome.archivedAs())
                .append(outcome.specsUpdated() ? "; main specs updated." : ".");
        for (String w : outcome.warnings()) s.append(" ").append(w);
        return s.toString();
    }
}
