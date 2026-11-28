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
            // Whether the refusal asks for the marker, decided here: the check reads the disk and may run the CLI.
            // Never at the cost of the failure report: an offer that can't be decided is simply not made.
            if (e instanceof ArchiveRefusedException refused) {
                try {
                    refused.markerOffer(RetireMarkerFix.offer(project, change.getName(), java.util.List.of(refused.describe())));
                } catch (com.intellij.openapi.progress.ProcessCanceledException pce) {
                    throw pce;
                } catch (RuntimeException offerFailed) {
                    com.intellij.openapi.diagnostic.Logger.getInstance(ArchiveDispatch.class)
                            .warn("Could not decide the retirement-marker offer", offerFailed);
                }
            }
            ApplicationManager.getApplication().invokeLater(() -> onError.accept(e), project.getDisposed());
        }
    }

    /** The user-facing reason for a failed archive: OpenSpec's message and fix when it refused. */
    public static String reason(Exception e) {
        return e instanceof ArchiveRefusedException refused ? refused.describe() : String.valueOf(e.getMessage());
    }

    /**
     * Reports a failed archive. A refusal that asks for the retirement marker gets an action that adds it and opens
     * the Sync preview; the archive is not run again. Call on the UI thread.
     */
    public static void notifyFailure(Project project, String title, String prefix, Exception error) {
        RetireMarkerFix.Offer offer = error instanceof ArchiveRefusedException refused ? refused.markerOffer() : null;
        if (offer == null) {
            com.johnnyblabs.openspec.util.OpenSpecNotifier.error(project, title, prefix + reason(error));
            return;
        }
        com.johnnyblabs.openspec.util.OpenSpecNotifier.notify(project, com.johnnyblabs.openspec.util.OpenSpecNotifier.GROUP_WORKFLOW,
                title, prefix + reason(error), com.intellij.notification.NotificationType.ERROR,
                new com.intellij.notification.NotificationAction(MARKER_ACTION) {
                    @Override
                    public void actionPerformed(@NotNull com.intellij.openapi.actionSystem.AnActionEvent e,
                                                @NotNull com.intellij.notification.Notification notification) {
                        notification.expire();
                        RetireMarkerFix.apply(project, offer,
                                () -> com.johnnyblabs.openspec.actions.OpenSpecSyncAction.syncChange(project, offer.changeName()));
                    }
                });
    }

    /** The archive-failure notification's action text. */
    static final String MARKER_ACTION = "Add retirement marker…";

    /** A one-line summary of a successful archive. */
    public static String summary(String changeName, ArchiveJson.Outcome outcome) {
        StringBuilder s = new StringBuilder(changeName).append(" archived as ").append(outcome.archivedAs())
                .append(outcome.specsUpdated() ? "; main specs updated." : ".");
        if (!outcome.retired().isEmpty()) s.append(" Retired: ").append(String.join(", ", outcome.retired())).append(".");
        for (String w : outcome.warnings()) s.append(" ").append(w);
        return s.toString();
    }
}
