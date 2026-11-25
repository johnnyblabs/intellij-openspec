package com.johnnyblabs.openspec.services;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.util.CliVersion;
import com.johnnyblabs.openspec.util.OpenSpecNotifier;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

/**
 * Shows {@link MigrationNotice} once per project, from the startup activity after the first CLI detection. Runs off the
 * UI thread: the check reads the project's tool folders. The notice's Update… button runs the Update OpenSpec… action,
 * which on CLI 1.15+ opens the update preview.
 */
public final class MigrationNoticeStartup {

    static final String UPDATE_ACTION = "Update…";
    static final String UPDATE_ACTION_ID = "OpenSpec.Update";

    private MigrationNoticeStartup() {
    }

    /** Shows the notice when it applies and hasn't been shown for this project; returns whether it was shown. */
    public static boolean notifyOnce(Project project, @Nullable String cliVersion) {
        if (cliVersion == null || !CliVersion.atLeast(cliVersion, "2.0.0")) return false;
        String base = project.getBasePath();
        return base != null && notifyOnce(project, cliVersion, Path.of(base));
    }

    /** As {@link #notifyOnce(Project, String)}, for the project's files at {@code root}. Package-private for tests. */
    static boolean notifyOnce(Project project, @Nullable String cliVersion, Path root) {
        if (cliVersion == null || !CliVersion.atLeast(cliVersion, "2.0.0")) return false;
        PropertiesComponent state = PropertiesComponent.getInstance(project);
        if (state.getBoolean(MigrationNotice.SHOWN_KEY, false)) return false;
        if (!MigrationNotice.applies(root, cliVersion)) return false;
        state.setValue(MigrationNotice.SHOWN_KEY, true);
        OpenSpecNotifier.notify(project, OpenSpecNotifier.GROUP_SYSTEM, MigrationNotice.TITLE, MigrationNotice.MESSAGE,
                NotificationType.INFORMATION, updateAction(project));
        return true;
    }

    /** Runs the Update action, so the notice's button does exactly what the menu's Update OpenSpec… does. */
    static NotificationAction updateAction(Project project) {
        return com.johnnyblabs.openspec.actions.ActionLauncher.notificationAction(project, UPDATE_ACTION, UPDATE_ACTION_ID);
    }
}
