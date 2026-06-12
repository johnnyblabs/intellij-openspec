package com.johnnyblabs.openspec.util;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class OpenSpecNotifier {

    public static final String GROUP_WORKFLOW = "OpenSpec.Workflow";
    public static final String GROUP_GENERATION = "OpenSpec.Generation";
    public static final String GROUP_VALIDATION = "OpenSpec.Validation";
    public static final String GROUP_SYSTEM = "OpenSpec.System";
    public static final String GROUP_COMPLIANCE = "OpenSpec.Compliance";

    private OpenSpecNotifier() {
    }

    // --- Legacy convenience (backward-compatible, no title, Workflow group) ---

    public static void info(@NotNull Project project, @NotNull String content) {
        notify(project, GROUP_WORKFLOW, null, content, NotificationType.INFORMATION);
    }

    public static void warn(@NotNull Project project, @NotNull String content) {
        notify(project, GROUP_WORKFLOW, null, content, NotificationType.WARNING);
    }

    public static void error(@NotNull Project project, @NotNull String content) {
        notify(project, GROUP_WORKFLOW, null, content, NotificationType.ERROR);
    }

    // --- Titled convenience (Workflow group) ---

    public static void info(@NotNull Project project, @NotNull String title, @NotNull String content) {
        notify(project, GROUP_WORKFLOW, title, content, NotificationType.INFORMATION);
    }

    public static void warn(@NotNull Project project, @NotNull String title, @NotNull String content) {
        notify(project, GROUP_WORKFLOW, title, content, NotificationType.WARNING);
    }

    public static void error(@NotNull Project project, @NotNull String title, @NotNull String content) {
        notify(project, GROUP_WORKFLOW, title, content, NotificationType.ERROR);
    }

    // --- Specialized notifications ---

    public static void cliMissing(@NotNull Project project) {
        notify(project, GROUP_SYSTEM, "CLI Detection",
                "OpenSpec CLI not found. Built-in features will be used. Install with: npm i -g @fission-ai/openspec",
                NotificationType.WARNING,
                openSettingsAction());
    }

    /**
     * Fired by {@code OpenSpecProjectService.StartupDetection} when the CLI is present but
     * below the v0.3.0 floor of 1.3.0. Once per project open; the user can dismiss
     * permanently via the standard "Don't show again" affordance on the {@code OpenSpec.System}
     * notification group.
     */
    public static void cliBelowFloor(@NotNull Project project, @NotNull String detectedVersion) {
        notify(project, GROUP_SYSTEM, "OpenSpec CLI is older than 1.3.0",
                "Detected version: " + detectedVersion
                        + ". Plugin features that require the CLI may not work as expected."
                        + " Upgrade: npm i -g @fission-ai/openspec@latest",
                NotificationType.WARNING);
    }

    public static void generateAllSummary(@NotNull Project project, int count, long elapsedSeconds) {
        String time = elapsedSeconds >= 60
                ? (elapsedSeconds / 60) + "m " + (elapsedSeconds % 60) + "s"
                : elapsedSeconds + "s";
        notify(project, GROUP_GENERATION, "Generate All",
                "Generated " + count + " artifacts in " + time,
                NotificationType.INFORMATION);
    }

    // --- Core method ---

    public static void notify(@NotNull Project project, @NotNull String groupId,
                              @Nullable String title, @NotNull String content,
                              @NotNull NotificationType type,
                              @NotNull NotificationAction... actions) {
        Notification notification = NotificationGroupManager.getInstance()
                .getNotificationGroup(groupId)
                .createNotification(content, type);
        if (title != null) {
            notification.setTitle(title);
        }
        for (NotificationAction action : actions) {
            notification.addAction(action);
        }
        notification.notify(project);
    }

    // --- Action factories ---

    public static NotificationAction openFileAction(@NotNull VirtualFile file) {
        return new NotificationAction("Open File") {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
                Project project = e.getProject();
                if (project != null) {
                    FileEditorManager.getInstance(project).openFile(file, true);
                }
                notification.expire();
            }
        };
    }

    public static NotificationAction openSettingsAction() {
        return new NotificationAction("Open Settings") {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
                Project project = e.getProject();
                if (project != null) {
                    ShowSettingsUtil.getInstance().showSettingsDialog(project, "OpenSpec");
                }
                notification.expire();
            }
        };
    }

    // --- Compliance notifications (sticky) ---

    public static void compliance(@NotNull Project project, @NotNull String title, @NotNull String content,
                                   @NotNull NotificationType type) {
        notify(project, GROUP_COMPLIANCE, title, content, type);
    }

    public static void complianceError(@NotNull Project project, @NotNull String title, @NotNull String content) {
        compliance(project, title, content, NotificationType.ERROR);
    }

    public static void complianceWarning(@NotNull Project project, @NotNull String title, @NotNull String content) {
        compliance(project, title, content, NotificationType.WARNING);
    }

    public static void complianceInfo(@NotNull Project project, @NotNull String title, @NotNull String content) {
        compliance(project, title, content, NotificationType.INFORMATION);
    }
}
