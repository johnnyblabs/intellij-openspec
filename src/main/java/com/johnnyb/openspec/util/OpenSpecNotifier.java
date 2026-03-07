package com.johnnyb.openspec.util;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class OpenSpecNotifier {

    private static final String GROUP_ID = "OpenSpec Notifications";

    private OpenSpecNotifier() {
    }

    public static void info(@NotNull Project project, @NotNull String content) {
        notify(project, content, NotificationType.INFORMATION);
    }

    public static void warn(@NotNull Project project, @NotNull String content) {
        notify(project, content, NotificationType.WARNING);
    }

    public static void error(@NotNull Project project, @NotNull String content) {
        notify(project, content, NotificationType.ERROR);
    }

    public static void cliMissing(@NotNull Project project) {
        warn(project, "OpenSpec CLI not found. Built-in features will be used. " +
                "Install with: npm i -g openspec-dev");
    }

    private static void notify(@NotNull Project project, @NotNull String content,
                               @NotNull NotificationType type) {
        NotificationGroupManager.getInstance()
                .getNotificationGroup(GROUP_ID)
                .createNotification(content, type)
                .notify(project);
    }
}
