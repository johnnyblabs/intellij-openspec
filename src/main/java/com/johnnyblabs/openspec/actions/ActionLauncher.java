package com.johnnyblabs.openspec.actions;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.wm.WindowManager;
import org.jetbrains.annotations.NotNull;

import java.awt.Component;

/**
 * Runs a plugin action from an in-panel link or button through the IDE's standard action pipeline.
 *
 * <p>The data context comes from {@code source} (the component the user clicked, or the project frame when that
 * component is gone), and the action's own {@code update()} runs first, so a disabled action does not run. This is
 * the one place that knows which platform API does that: {@code ActionManager.tryToExecute} exists and is not
 * deprecated across the plugin's whole range (2024.2+), unlike the {@code ActionUtil.invokeAction} overloads and the
 * {@code AnActionEvent} factories it replaces. Call on the EDT; with {@code now = true} it runs synchronously.
 */
public final class ActionLauncher {

    private ActionLauncher() {
    }

    public static @NotNull ActionCallback launch(@NotNull AnAction action, @NotNull Component source, @NotNull String place) {
        return ActionManager.getInstance().tryToExecute(action, null, source, place, true);
    }

    /**
     * A notification button that runs the action {@code actionId} in {@code project}, e.g. "Update OpenSpec…" from a
     * notice. Its data context comes from the project's frame, not whatever has focus: a notice can be clicked from the
     * Notifications tool window or while another project's window is focused.
     */
    public static @NotNull NotificationAction notificationAction(@NotNull Project project, @NotNull String text,
                                                                 @NotNull String actionId) {
        return new NotificationAction(text) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
                AnAction action = ActionManager.getInstance().getAction(actionId);
                Component frame = project.isDisposed() ? null : WindowManager.getInstance().getFrame(project);
                if (action != null && frame != null) {
                    launch(action, frame, e.getPlace());
                }
                notification.expire();
            }
        };
    }
}
