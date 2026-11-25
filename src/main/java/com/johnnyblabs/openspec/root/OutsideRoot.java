package com.johnnyblabs.openspec.root;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

/**
 * Whether a file belongs to a resolved OpenSpec root that lies outside the project (a store, or a parent folder's
 * {@code openspec/}). Shared by the write-access extension and the editor banner. Reads only the resolver's cached
 * snapshot: it never creates the service, schedules a resolution, reads the disk or runs the CLI, because the editor
 * asks for every file it opens or writes.
 */
public final class OutsideRoot {

    private OutsideRoot() {
    }

    /** The resolution whose root holds {@code file} and lies outside the project, or {@code null}. */
    public static @Nullable RootResolution holding(Project project, VirtualFile file) {
        if (project.isDisposed()) return null;
        OpenSpecRootResolver resolver = project.getServiceIfCreated(OpenSpecRootResolver.class);
        if (resolver == null) return null;
        OpenSpecRootResolver.Snapshot snapshot = resolver.current();
        VirtualFile dir = snapshot.openspecDir();
        if (dir == null || !dir.isValid()) return null;
        Path base = project.getBasePath() == null ? null : Path.of(project.getBasePath());
        if (RootProvenance.isLocal(snapshot.resolution(), base)) return null;
        return VfsUtilCore.isAncestor(dir, file, false) ? snapshot.resolution() : null;
    }
}
