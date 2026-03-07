package com.johnnyb.openspec.util;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;

public final class OpenSpecFileUtil {

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

    public static VirtualFile getOpenSpecRoot(Project project) {
        VirtualFile baseDir = getBaseDir(project);
        if (baseDir == null) return null;
        VirtualFile root = baseDir.findChild("openspec");
        if (root != null && root.isDirectory()) return root;
        // Fallback: VFS may not have indexed yet, try direct path
        if (project.getBasePath() != null) {
            root = LocalFileSystem.getInstance()
                    .refreshAndFindFileByPath(project.getBasePath() + "/openspec");
            if (root != null && root.isDirectory()) return root;
        }
        return null;
    }

    public static VirtualFile getConfigFile(Project project) {
        VirtualFile root = getOpenSpecRoot(project);
        if (root == null) return null;
        VirtualFile config = root.findChild("config.yaml");
        if (config != null) return config;
        // Fallback: direct path
        return LocalFileSystem.getInstance()
                .refreshAndFindFileByPath(root.getPath() + "/config.yaml");
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
        VirtualFile root = getOpenSpecRoot(project);
        if (root == null || file == null) return false;
        String rootPath = root.getPath();
        return file.getPath().startsWith(rootPath + "/");
    }

    public static String getDomainName(VirtualFile specFile) {
        if (specFile == null || specFile.getParent() == null) return null;
        return specFile.getParent().getName();
    }
}
