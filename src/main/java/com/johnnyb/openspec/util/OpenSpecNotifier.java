package com.johnnyb.openspec.util;

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
                "OpenSpec CLI not found. Built-in features will be used. Install with: npm i -g openspec-dev",
                NotificationType.WARNING,
                openSettingsAction());
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
}
