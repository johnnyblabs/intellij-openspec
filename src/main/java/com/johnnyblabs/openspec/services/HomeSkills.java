package com.johnnyblabs.openspec.services;

import com.johnnyblabs.openspec.services.AiToolRegistry.AiTool;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Which OpenSpec skill files exist in the home directory, read once: {@code ~/.agents/skills/<skill>/SKILL.md} (where
 * 2.1 installs machine-wide), each tool's home link folder (the same relative folder as in a project, e.g.
 * {@code ~/.claude/skills}) and the 1.x home-directory skills folders (MiniMax Code's {@code ~/.minimax/skills}).
 * <p>
 * An immutable snapshot, so lookups never touch the disk; {@link #scan} does, and must run off the EDT.
 */
public final class HomeSkills {

    public static final HomeSkills NONE = new HomeSkills(null, Set.of());

    private final @Nullable Path home;
    private final Set<String> present;

    private HomeSkills(@Nullable Path home, Set<String> present) {
        this.home = home;
        this.present = Set.copyOf(present);
    }

    /** The folders, relative to home, whose skills are looked for. */
    static Set<String> folders() {
        Set<String> dirs = new LinkedHashSet<>();
        dirs.add(".agents/skills");
        for (AiTool tool : AiToolRegistry.all()) {
            dirs.addAll(SkillLinkDirs.linkDirsAnyLine(tool.id()));
            if (tool.globalSkillsDir() != null) dirs.add(tool.globalSkillsDir() + "/skills");
        }
        return dirs;
    }

    /** Reads which skill files exist under {@code home} (links followed: a skill reached through one is there). */
    public static HomeSkills scan(@Nullable Path home) {
        if (home == null) return NONE;
        Set<String> present = new LinkedHashSet<>();
        for (String dir : folders()) {
            for (String skill : AiToolRegistry.SKILL_NAMES) {
                String rel = dir + "/" + skill + "/SKILL.md";
                if (Files.isRegularFile(home.resolve(rel))) present.add(rel);
            }
        }
        return new HomeSkills(home, present);
    }

    /** The skill file at {@code dir/skill/SKILL.md} under home, or {@code null} when it wasn't there when scanned. */
    public @Nullable Path skill(String dir, String skill) {
        String rel = dir + "/" + skill + "/SKILL.md";
        return home != null && present.contains(rel) ? home.resolve(rel) : null;
    }

    public boolean isEmpty() {
        return present.isEmpty();
    }
}
