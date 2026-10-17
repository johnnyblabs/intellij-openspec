package com.johnnyblabs.openspec.actions;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.util.ActionCallback;
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
}
