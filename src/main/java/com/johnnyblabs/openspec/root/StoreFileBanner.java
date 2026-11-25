package com.johnnyblabs.openspec.root;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotificationProvider;
import com.intellij.ui.EditorNotifications;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import java.util.function.Function;

/**
 * A banner on files under a resolved OpenSpec root outside the project: it names the store (or folder) and says to
 * commit from that repository, since the project's version control doesn't track it. Dismissible for the project.
 */
public final class StoreFileBanner implements EditorNotificationProvider, DumbAware {

    static final String DISMISSED_KEY = "openspec.storeFileBanner.dismissed";
    public static final String DONT_SHOW = "Don't show again for this project";

    @Override
    public @Nullable Function<? super @NotNull FileEditor, ? extends @Nullable JComponent> collectNotificationData(
            @NotNull Project project, @NotNull VirtualFile file) {
        if (PropertiesComponent.getInstance(project).getBoolean(DISMISSED_KEY)) return null;
        RootResolution resolution = OutsideRoot.holding(project, file);
        if (resolution == null) return null;
        String text = RootProvenance.bannerText(resolution);
        return editor -> {
            EditorNotificationPanel panel = new EditorNotificationPanel(editor, EditorNotificationPanel.Status.Info);
            panel.setText(text);
            panel.createActionLabel(DONT_SHOW, () -> {
                PropertiesComponent.getInstance(project).setValue(DISMISSED_KEY, true);
                EditorNotifications.getInstance(project).updateAllNotifications();
            });
            return panel;
        };
    }
}
