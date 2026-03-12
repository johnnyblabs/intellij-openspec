package com.johnnyb.openspec.tracking;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.johnnyb.openspec.model.Change;
import com.johnnyb.openspec.model.ChangeMetadata;
import com.johnnyb.openspec.services.ChangeService;
import com.johnnyb.openspec.settings.OpenSpecSettings;
import com.johnnyb.openspec.util.OpenSpecNotifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Orchestrates post-archive sync reconciliation with issue trackers.
 * Runs after archive filesystem success to close/update tracker issues.
 * Supports retry on failure without re-archiving.
 */
@Service(Service.Level.PROJECT)
public final class ArchiveSyncService {

    private static final Logger LOG = Logger.getInstance(ArchiveSyncService.class);

    private final Project project;

    public ArchiveSyncService(@NotNull Project project) {
        this.project = project;
    }

    /**
     * Result of a sync reconciliation attempt.
     */
    public record SyncResult(SyncState state, @Nullable String message) {

        public boolean isSuccess() {
            return state == SyncState.SUCCESS;
        }

        public boolean isRetryable() {
            return state == SyncState.PARTIAL_FAILURE || state == SyncState.FAILURE;
        }
    }

    /**
     * Outcome states for sync reconciliation.
     */
    public enum SyncState {
        /** Both trackers updated successfully (or not enabled). */
        SUCCESS,
        /** One tracker succeeded, the other failed. */
        PARTIAL_FAILURE,
        /** All enabled tracker updates failed. */
        FAILURE,
        /** No trackers are enabled — nothing to sync. */
        SKIPPED
    }

    /**
     * Runs sync reconciliation for the given change. Can be called after archive
     * or as a manual retry. Does not re-archive.
     *
     * <p>This method is safe to call from a background thread. It performs
     * idempotent tracker updates (checks state before mutating).</p>
     */
    public SyncResult sync(String changeName) {
        OpenSpecSettings settings = OpenSpecSettings.getInstance(project);
        boolean forgejoEnabled = settings.isForgejoEnabled();
        boolean planeEnabled = settings.isPlaneEnabled();

        if (!forgejoEnabled && !planeEnabled) {
            return new SyncResult(SyncState.SKIPPED, "No trackers enabled");
        }

        ChangeMetadata.TrackingMetadata tracking = findTrackingMetadata(changeName);
        if (tracking == null) {
            // No tracking metadata — skip tracker updates but don't fail
            LOG.info("No tracking metadata found for change: " + changeName + ", skipping sync");
            return new SyncResult(SyncState.SKIPPED, "No tracking metadata");
        }

        boolean forgejoOk = true;
        boolean planeOk = true;
        StringBuilder errors = new StringBuilder();

        // Forgejo sync
        if (forgejoEnabled && tracking.getForgejo() != null) {
            try {
                syncForgejo(tracking.getForgejo().getIssueNumber());
            } catch (Exception e) {
                forgejoOk = false;
                LOG.warn("Forgejo sync failed for change: " + changeName, e);
                errors.append("Forgejo: ").append(e.getMessage());
            }
        }

        // Plane sync
        if (planeEnabled && tracking.getPlane() != null) {
            try {
                syncPlane(tracking.getPlane().getWorkItemId());
            } catch (Exception e) {
                planeOk = false;
                LOG.warn("Plane sync failed for change: " + changeName, e);
                if (!errors.isEmpty()) errors.append("; ");
                errors.append("Plane: ").append(e.getMessage());
            }
        }

        if (forgejoOk && planeOk) {
            return new SyncResult(SyncState.SUCCESS, "Trackers synchronized");
        } else if (forgejoOk || planeOk) {
            return new SyncResult(SyncState.PARTIAL_FAILURE, errors.toString());
        } else {
            return new SyncResult(SyncState.FAILURE, errors.toString());
        }
    }

    /**
     * Runs sync asynchronously and notifies the user of the outcome.
     */
    public void syncAsync(String changeName, @Nullable Runnable onComplete) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            SyncResult result = sync(changeName);
            ApplicationManager.getApplication().invokeLater(() -> {
                notifyResult(changeName, result);
                if (onComplete != null) {
                    onComplete.run();
                }
            });
        });
    }

    /**
     * Idempotent Forgejo close: checks issue state before closing.
     */
    private void syncForgejo(int issueNumber) throws Exception {
        ForgejoService forgejo = project.getService(ForgejoService.class);
        if (forgejo == null) throw new IllegalStateException("ForgejoService not available");

        // Check current state to avoid duplicate operations
        if (forgejo.isIssueClosed(issueNumber)) {
            LOG.info("Forgejo issue #" + issueNumber + " already closed, skipping");
            return;
        }

        forgejo.addComment(issueNumber, "Change archived");
        forgejo.updateIssue(issueNumber, "closed", java.util.List.of("done"));
    }

    /**
     * Idempotent Plane close: checks work item state before updating.
     */
    private void syncPlane(String workItemId) throws Exception {
        PlaneService plane = project.getService(PlaneService.class);
        if (plane == null) throw new IllegalStateException("PlaneService not available");

        // Check current state to avoid duplicate operations
        if (plane.isWorkItemInState(workItemId, "Done")) {
            LOG.info("Plane work item " + workItemId + " already Done, skipping");
            return;
        }

        plane.updateWorkItemState(workItemId, "Done");
    }

    private void notifyResult(String changeName, SyncResult result) {
        switch (result.state()) {
            case SUCCESS -> OpenSpecNotifier.notify(project, OpenSpecNotifier.GROUP_TRACKER, "Tracker Sync",
                    "Sync complete for \"" + changeName + "\"", com.intellij.notification.NotificationType.INFORMATION);
            case PARTIAL_FAILURE -> OpenSpecNotifier.notify(project, OpenSpecNotifier.GROUP_TRACKER, "Tracker Sync",
                    "Sync partially failed for \"" + changeName + "\": " + result.message() + ". Use Retry Sync to try again.",
                    com.intellij.notification.NotificationType.WARNING);
            case FAILURE -> OpenSpecNotifier.notify(project, OpenSpecNotifier.GROUP_TRACKER, "Tracker Sync",
                    "Sync failed for \"" + changeName + "\": " + result.message() + ". Use Retry Sync to try again.",
                    com.intellij.notification.NotificationType.ERROR);
            case SKIPPED -> { /* silent */ }
        }
    }

    /**
     * Finds tracking metadata for a change by searching both active and archived changes.
     */
    @Nullable
    private ChangeMetadata.TrackingMetadata findTrackingMetadata(String changeName) {
        ChangeService changeService = project.getService(ChangeService.class);
        if (changeService == null) return null;

        // Check active changes first, then archived
        for (Change change : changeService.getActiveChanges()) {
            if (change.getName().equals(changeName) && change.getMetadata() != null) {
                return change.getMetadata().getTracking();
            }
        }
        for (Change change : changeService.getArchivedChanges()) {
            if (change.getName().equals(changeName) && change.getMetadata() != null) {
                return change.getMetadata().getTracking();
            }
        }
        return null;
    }
}
