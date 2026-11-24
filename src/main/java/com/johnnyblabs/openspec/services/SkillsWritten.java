package com.johnnyblabs.openspec.services;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.johnnyblabs.openspec.services.AiToolRegistry.AiTool;
import com.johnnyblabs.openspec.util.CliArgs;
import com.johnnyblabs.openspec.util.CliCommands;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * What follows a CLI run that may have written skills ({@code init}, or {@code update} without {@code --dry-run}):
 * the machine-wide skills cache is cleared, and the project's skills folders are refreshed in the IDE's file system.
 * <p>
 * The refresh is recursive from each tool's {@code skills} folder, never from single skill spots, because a run can
 * change a spot's type: on Windows {@code update} turns the text file git leaves for a link into a copy folder, and on
 * any host a copy can become a link or the reverse. Refreshing the parent drops the stale entry.
 */
public final class SkillsWritten {

    private SkillsWritten() {
    }

    /** Whether {@code args} is a run that may write skills. */
    public static boolean writesSkills(CliArgs args) {
        String command = args.command();
        return ("init".equals(command) || "update".equals(command)) && !CliCommands.isDryRun(args);
    }

    /** Called when a run of {@code args} finished, whatever its exit code (a failed run may have written some). */
    public static void afterRun(@Nullable Project project, CliArgs args) {
        if (project == null || project.isDisposed() || !writesSkills(args)) return;
        GlobalSkillsCache cache = project.getService(GlobalSkillsCache.class);
        if (cache != null) cache.invalidate();
        String base = project.getBasePath();
        if (base == null || ApplicationManager.getApplication() == null) return;
        List<Path> folders = folders(Path.of(base));
        if (!folders.isEmpty()) LocalFileSystem.getInstance().refreshNioFiles(folders, true, true, null);
    }

    /** The project's skills folders that exist: the shared one, each tool's link folders and its own skills folder. */
    static List<Path> folders(Path base) {
        Set<String> relative = new LinkedHashSet<>();
        relative.add(".agents/skills");
        for (AiTool tool : AiToolRegistry.all()) {
            relative.addAll(SkillLinkDirs.linkDirsAnyLine(tool.id()));
            if (tool.skillsDir() != null) relative.add(tool.skillsDir() + "/skills");
            for (String legacy : tool.legacySkillsDirs()) relative.add(legacy + "/skills");
        }
        // MiniMax Code's project folder from 2.1, which its 1.x record doesn't name.
        relative.add(".minimax/skills");
        List<Path> out = new ArrayList<>();
        for (String r : relative) {
            Path p = base.resolve(r);
            if (Files.exists(p)) out.add(p);
        }
        return out;
    }
}
