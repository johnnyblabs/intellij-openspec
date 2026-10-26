package com.johnnyblabs.openspec.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.settings.OpenSpecConfigurable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.function.Consumer;

/**
 * "Settings…": opens Settings | Tools | OpenSpec (CLI, delivery, Direct API) from the OpenSpec menu and the tool window
 * toolbar. Like Manage AI Tools it's available with any open project, before OpenSpec is initialized.
 */
public class OpenSpecOpenSettingsAction extends AnAction implements DumbAware {

    private static final Consumer<Project> SHOW_SETTINGS =
            project -> ShowSettingsUtil.getInstance().showSettingsDialog(project, OpenSpecConfigurable.class);

    /** Opens the settings page; replaced in tests, which can't show the Settings dialog. */
    private static volatile Consumer<Project> opener = SHOW_SETTINGS;

    @TestOnly
    static void setOpenerForTest(@Nullable Consumer<Project> testOpener) {
        opener = testOpener == null ? SHOW_SETTINGS : testOpener;
    }

    /** Opens the OpenSpec settings page for {@code project}. */
    public static void open(@NotNull Project project) {
        opener.accept(project);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabledAndVisible(e.getProject() != null);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project != null) open(project);
    }
}
