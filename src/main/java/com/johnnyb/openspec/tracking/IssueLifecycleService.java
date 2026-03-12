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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Service(Service.Level.PROJECT)
public final class IssueLifecycleService {

    private static final Logger LOG = Logger.getInstance(IssueLifecycleService.class);

    private final Project project;

    public IssueLifecycleService(@NotNull Project project) {
        this.project = project;
    }

    public void onPropose(String changeName, String changeDir) {
        OpenSpecSettings settings = OpenSpecSettings.getInstance(project);
        if (!settings.isForgejoEnabled() && !settings.isPlaneEnabled()) return;

        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            String title = toTitleCase(changeName);
            String body = readProposal(changeDir);

            if (settings.isForgejoEnabled()) {
                createForgejoIssue(changeName, changeDir, title, body);
            }
            if (settings.isPlaneEnabled()) {
                createPlaneWorkItem(changeName, changeDir, title, body);
            }
        });
    }

    public void onApply(String changeName, String changeDir) {
        OpenSpecSettings settings = OpenSpecSettings.getInstance(project);
        if (!settings.isForgejoEnabled() && !settings.isPlaneEnabled()) return;

        ChangeMetadata.TrackingMetadata tracking = getTrackingMetadata(changeName);
        if (tracking == null) return;

        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            if (settings.isForgejoEnabled() && tracking.getForgejo() != null) {
                updateForgejoOnApply(tracking.getForgejo().getIssueNumber());
            }
            if (settings.isPlaneEnabled() && tracking.getPlane() != null) {
                updatePlaneOnApply(tracking.getPlane().getWorkItemId());
            }
        });
    }

    public void onArchive(String changeName, String changeDir) {
        OpenSpecSettings settings = OpenSpecSettings.getInstance(project);
        if (!settings.isForgejoEnabled() && !settings.isPlaneEnabled()) return;

        ChangeMetadata.TrackingMetadata tracking = getTrackingMetadata(changeName);
        if (tracking == null) return;

        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            if (settings.isForgejoEnabled() && tracking.getForgejo() != null) {
                closeForgejoIssue(tracking.getForgejo().getIssueNumber());
            }
            if (settings.isPlaneEnabled() && tracking.getPlane() != null) {
                closePlaneWorkItem(tracking.getPlane().getWorkItemId());
            }
        });
    }

    private void createForgejoIssue(String changeName, String changeDir, String title, String body) {
        try {
            ForgejoService forgejo = project.getService(ForgejoService.class);
            ForgejoService.IssueResult result = forgejo.createIssue(title, body, List.of("enhancement"));
            TrackingMetadataWriter.writeForgejoRef(Path.of(changeDir), result.issueNumber(), result.issueUrl());
            ApplicationManager.getApplication().invokeLater(() ->
                    OpenSpecNotifier.notify(project, OpenSpecNotifier.GROUP_TRACKER, "Forgejo",
                            "Issue #" + result.issueNumber() + " created for \"" + changeName + "\"",
                            com.intellij.notification.NotificationType.INFORMATION));
        } catch (Exception e) {
            LOG.warn("Failed to create Forgejo issue for " + changeName, e);
            ApplicationManager.getApplication().invokeLater(() ->
                    OpenSpecNotifier.notify(project, OpenSpecNotifier.GROUP_TRACKER, "Forgejo",
                            e.getMessage(), com.intellij.notification.NotificationType.WARNING));
        }
    }

    private void createPlaneWorkItem(String changeName, String changeDir, String title, String body) {
        try {
            PlaneService plane = project.getService(PlaneService.class);
            String html = PlaneService.markdownToHtml(body);
            PlaneService.WorkItemResult result = plane.createWorkItem(title, html);
            TrackingMetadataWriter.writePlaneRef(Path.of(changeDir), result.workItemId(), result.workItemUrl());
            ApplicationManager.getApplication().invokeLater(() ->
                    OpenSpecNotifier.notify(project, OpenSpecNotifier.GROUP_TRACKER, "Plane",
                            "Work item created for \"" + changeName + "\"",
                            com.intellij.notification.NotificationType.INFORMATION));
        } catch (Exception e) {
            LOG.warn("Failed to create Plane work item for " + changeName, e);
            ApplicationManager.getApplication().invokeLater(() ->
                    OpenSpecNotifier.notify(project, OpenSpecNotifier.GROUP_TRACKER, "Plane",
                            e.getMessage(), com.intellij.notification.NotificationType.WARNING));
        }
    }

    private void updateForgejoOnApply(int issueNumber) {
        try {
            ForgejoService forgejo = project.getService(ForgejoService.class);
            forgejo.addComment(issueNumber, "Implementation started — Apply triggered");
            forgejo.updateIssue(issueNumber, null, List.of("in-progress"));
        } catch (Exception e) {
            LOG.warn("Failed to update Forgejo issue #" + issueNumber + " on Apply", e);
            ApplicationManager.getApplication().invokeLater(() ->
                    OpenSpecNotifier.notify(project, OpenSpecNotifier.GROUP_TRACKER, "Forgejo",
                            "Update failed: " + e.getMessage(), com.intellij.notification.NotificationType.WARNING));
        }
    }

    private void updatePlaneOnApply(String workItemId) {
        try {
            PlaneService plane = project.getService(PlaneService.class);
            plane.updateWorkItemState(workItemId, "In Progress");
        } catch (Exception e) {
            LOG.warn("Failed to update Plane work item " + workItemId + " on Apply", e);
            ApplicationManager.getApplication().invokeLater(() ->
                    OpenSpecNotifier.notify(project, OpenSpecNotifier.GROUP_TRACKER, "Plane",
                            "Update failed: " + e.getMessage(), com.intellij.notification.NotificationType.WARNING));
        }
    }

    private void closeForgejoIssue(int issueNumber) {
        try {
            ForgejoService forgejo = project.getService(ForgejoService.class);
            forgejo.addComment(issueNumber, "Change archived");
            forgejo.updateIssue(issueNumber, "closed", List.of("done"));
            ApplicationManager.getApplication().invokeLater(() ->
                    OpenSpecNotifier.notify(project, OpenSpecNotifier.GROUP_TRACKER, "Forgejo",
                            "Issue #" + issueNumber + " closed", com.intellij.notification.NotificationType.INFORMATION));
        } catch (Exception e) {
            LOG.warn("Failed to close Forgejo issue #" + issueNumber, e);
            ApplicationManager.getApplication().invokeLater(() ->
                    OpenSpecNotifier.notify(project, OpenSpecNotifier.GROUP_TRACKER, "Forgejo",
                            "Close failed: " + e.getMessage(), com.intellij.notification.NotificationType.WARNING));
        }
    }

    private void closePlaneWorkItem(String workItemId) {
        try {
            PlaneService plane = project.getService(PlaneService.class);
            plane.updateWorkItemState(workItemId, "Done");
            ApplicationManager.getApplication().invokeLater(() ->
                    OpenSpecNotifier.notify(project, OpenSpecNotifier.GROUP_TRACKER, "Plane",
                            "Work item closed", com.intellij.notification.NotificationType.INFORMATION));
        } catch (Exception e) {
            LOG.warn("Failed to close Plane work item " + workItemId, e);
            ApplicationManager.getApplication().invokeLater(() ->
                    OpenSpecNotifier.notify(project, OpenSpecNotifier.GROUP_TRACKER, "Plane",
                            "Close failed: " + e.getMessage(), com.intellij.notification.NotificationType.WARNING));
        }
    }

    private ChangeMetadata.TrackingMetadata getTrackingMetadata(String changeName) {
        ChangeService changeService = project.getService(ChangeService.class);
        for (Change change : changeService.getActiveChanges()) {
            if (change.getName().equals(changeName) && change.getMetadata() != null) {
                return change.getMetadata().getTracking();
            }
        }
        return null;
    }

    private String readProposal(String changeDir) {
        try {
            Path proposalPath = Path.of(changeDir, "proposal.md");
            if (Files.exists(proposalPath)) {
                return Files.readString(proposalPath);
            }
        } catch (IOException e) {
            LOG.warn("Failed to read proposal.md", e);
        }
        return "";
    }

    static String toTitleCase(String kebab) {
        if (kebab == null || kebab.isEmpty()) return kebab;
        String[] parts = kebab.split("-");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                if (!sb.isEmpty()) sb.append(' ');
                sb.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) sb.append(part.substring(1));
            }
        }
        return sb.toString();
    }
}
