package com.johnnyblabs.openspec.statusbar;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.StatusBarWidgetFactory;
import com.johnnyblabs.openspec.util.OpenSpecFileUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Factory for {@link OpenSpecProfileStatusBarWidget}. Hides the widget on non-OpenSpec
 * projects (matches the same {@code isOpenSpecProject} check used by {@code OpenSpecBaseAction}).
 */
public final class OpenSpecProfileStatusBarWidgetFactory implements StatusBarWidgetFactory {

    @Override
    public @NotNull String getId() {
        return OpenSpecProfileStatusBarWidget.ID;
    }

    @Override
    public @NotNull String getDisplayName() {
        return "OpenSpec Workflow Profile";
    }

    @Override
    public boolean isAvailable(@NotNull Project project) {
        return OpenSpecFileUtil.isOpenSpecProject(project);
    }

    @Override
    public @NotNull StatusBarWidget createWidget(@NotNull Project project) {
        return new OpenSpecProfileStatusBarWidget(project);
    }

    @Override
    public boolean canBeEnabledOn(@NotNull StatusBar statusBar) {
        return true;
    }
}
