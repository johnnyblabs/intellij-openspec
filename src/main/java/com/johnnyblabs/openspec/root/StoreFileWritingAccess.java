package com.johnnyblabs.openspec.root;

import com.intellij.openapi.fileEditor.impl.NonProjectFileWritingAccessExtension;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * Files under the resolved OpenSpec root are the project's specs even when the root lies outside the project (a
 * store, a parent folder), so editing them doesn't ask to unlock a "file that does not belong to the project". The
 * editor banner ({@link StoreFileBanner}) says where they live instead. Files elsewhere keep the platform's prompt.
 */
public final class StoreFileWritingAccess implements NonProjectFileWritingAccessExtension {

    private final Project project;

    public StoreFileWritingAccess(Project project) {
        this.project = project;
    }

    @Override
    public boolean isWritable(@NotNull VirtualFile file) {
        return OutsideRoot.holding(project, file) != null;
    }
}
