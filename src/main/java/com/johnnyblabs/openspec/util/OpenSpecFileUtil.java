package com.johnnyblabs.openspec.util;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.johnnyblabs.openspec.root.OpenSpecRootResolver;
import com.johnnyblabs.openspec.root.RootResolver;

public final class OpenSpecFileUtil {

    public static final String OPENSPEC_DIR = RootResolver.OPENSPEC_DIR;

    private OpenSpecFileUtil() {
    }

    private static VirtualFile getBaseDir(Project project) {
        String basePath = project.getBasePath();
        if (basePath == null) return null;
        return LocalFileSystem.getInstance().findFileByPath(basePath);
    }

    public static boolean isOpenSpecProject(Project project) {
        return getOpenSpecRoot(project) != null;
    }

    /**
     * The resolved {@code openspec/} directory: the project's own, an ancestor's, or a store's ({@link
     * OpenSpecRootResolver}). Reads only the in-memory file system and the resolver's cache: no refresh, no disk read,
     * no CLI, so it is safe in action updates, inspections and file-event handlers. {@code null} while the first
     * resolution is pending or when the project has no root.
     */
    public static VirtualFile getOpenSpecRoot(Project project) {
        // No IDE running (plain unit tests): there is no file system to ask. Path callers then use <project>/openspec.
        if (com.intellij.openapi.application.ApplicationManager.getApplication() == null) return null;
        VirtualFile local = findLocalPlanningRoot(project);
        if (local != null) return local;
        if (project.isDisposed()) return null;
        OpenSpecRootResolver resolver = OpenSpecRootResolver.getInstance(project);
        return resolver == null ? null : resolver.snapshot().openspecDir();
    }

    /**
     * The fast check, from the in-memory file system: {@code <project>/openspec} with a planning shape, then a module
     * content root's {@code openspec/} (for multi-module projects and test fixtures). A content root that is the
     * project directory needs a planning shape too, so a pointer-only config there goes to the resolver. A hit is
     * what the CLI resolves from the project directory, so it needs no cache.
     */
    static VirtualFile findLocalPlanningRoot(Project project) {
        VirtualFile baseDir = getBaseDir(project);
        if (baseDir != null) {
            VirtualFile root = baseDir.findChild(OPENSPEC_DIR);
            if (root != null && root.isDirectory() && hasPlanningShape(root)) return root;
        }
        if (project.isDisposed()) return null;
        ProjectRootManager roots = ProjectRootManager.getInstance(project);
        if (roots == null) return null;
        for (VirtualFile contentRoot : roots.getContentRoots()) {
            if (contentRoot.equals(baseDir)) continue;
            VirtualFile root = contentRoot.findChild(OPENSPEC_DIR);
            if (root != null && root.isDirectory()) return root;
        }
        return null;
    }

    /** {@code specs/} or {@code changes/} is a directory and not a store kept at that path, as the CLI decides. */
    static boolean hasPlanningShape(VirtualFile openspecDir) {
        return isPlanningDir(openspecDir.findChild("specs")) || isPlanningDir(openspecDir.findChild("changes"));
    }

    private static boolean isPlanningDir(VirtualFile dir) {
        return dir != null && dir.isDirectory() && dir.findFileByRelativePath(".openspec-store/store.yaml") == null;
    }

    /**
     * The resolved {@code openspec/} directory as a path, for code that builds paths to files that may not exist yet
     * or reads the disk directly. When nothing resolved (or the root is not on the local disk, as in test fixtures),
     * it is {@code <project>/openspec}, the path init creates. {@code null} only without a project base path.
     */
    public static @org.jetbrains.annotations.Nullable String openspecPath(Project project) {
        VirtualFile root = getOpenSpecRoot(project);
        if (root != null && root.isInLocalFileSystem()) return root.getPath();
        String base = project.getBasePath();
        return base == null ? null : base + "/" + OPENSPEC_DIR;
    }

    /** {@link #openspecPath} as a {@link java.nio.file.Path}; {@code null} only without a project base path. */
    public static java.nio.file.@org.jetbrains.annotations.Nullable Path openspecNioPath(Project project) {
        String path = openspecPath(project);
        return path == null ? null : java.nio.file.Path.of(path);
    }

    public static VirtualFile getConfigFile(Project project) {
        VirtualFile root = getOpenSpecRoot(project);
        if (root == null) return null;
        return root.findChild("config.yaml");
    }

    public static VirtualFile getSpecsDir(Project project) {
        VirtualFile root = getOpenSpecRoot(project);
        if (root == null) return null;
        return root.findChild("specs");
    }

    public static VirtualFile getChangesDir(Project project) {
        VirtualFile root = getOpenSpecRoot(project);
        if (root == null) return null;
        return root.findChild("changes");
    }

    public static VirtualFile getArchiveDir(Project project) {
        VirtualFile changesDir = getChangesDir(project);
        if (changesDir != null) {
            VirtualFile archive = changesDir.findChild("archive");
            if (archive != null && archive.isDirectory()) return archive;
        }
        // Fallback: legacy location at openspec/archive
        VirtualFile root = getOpenSpecRoot(project);
        if (root == null) return null;
        return root.findChild("archive");
    }

    public static boolean isSpecFile(VirtualFile file) {
        return file != null && "spec.md".equals(file.getName());
    }

    public static boolean isDeltaSpecFile(VirtualFile file) {
        if (file == null || !"spec.md".equals(file.getName())) return false;
        // Must be inside a specs/<domain>/spec.md structure within a change
        VirtualFile domainDir = file.getParent();
        if (domainDir == null) return false;
        VirtualFile specsDir = domainDir.getParent();
        return specsDir != null && "specs".equals(specsDir.getName());
    }

    public static boolean isUnderOpenSpec(VirtualFile file, Project project) {
        if (file == null) return false;
        VirtualFile root = getOpenSpecRoot(project);
        return root != null && VfsUtilCore.isAncestor(root, file, true);
    }

    public static String getDomainName(VirtualFile specFile) {
        if (specFile == null || specFile.getParent() == null) return null;
        return specFile.getParent().getName();
    }
}
