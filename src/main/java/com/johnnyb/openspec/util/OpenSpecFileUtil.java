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
        VirtualFile baseDir = getBaseDir(project);
        if (baseDir == null) return false;
        VirtualFile openspecDir = baseDir.findChild("openspec");
        return openspecDir != null && openspecDir.isDirectory();
    }

    public static VirtualFile getOpenSpecRoot(Project project) {
        VirtualFile baseDir = getBaseDir(project);
        if (baseDir == null) return null;
        return baseDir.findChild("openspec");
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
        VirtualFile root = getOpenSpecRoot(project);
        if (root == null) return null;
        return root.findChild("archive");
    }

    public static boolean isSpecFile(VirtualFile file) {
        return file != null && "spec.md".equals(file.getName());
    }

    public static boolean isDeltaSpecFile(VirtualFile file) {
        return file != null && file.getName().endsWith(".md")
                && file.getParent() != null
                && "delta-specs".equals(file.getParent().getName());
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
