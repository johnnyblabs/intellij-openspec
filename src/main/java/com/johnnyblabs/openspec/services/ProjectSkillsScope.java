package com.johnnyblabs.openspec.services;

import com.johnnyblabs.openspec.services.AiToolRegistry.AiTool;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Where a project's OpenSpec skills are, as the scope line says it: in the project, only on this machine, or nowhere.
 * <p>
 * "In the project" means any OpenSpec skill in a spot workflow lookup reads in the project (the shared
 * {@code .agents/skills/}, a tool's link folder, its own or legacy skills folder), so the line and the lookup never
 * disagree. A project that is the home folder holds the machine's skills as its own, so it counts as in the project.
 * <p>
 * Pure nio, a few hundred attribute reads: call it off the EDT.
 */
public enum ProjectSkillsScope {
    /** The project holds OpenSpec skills of its own. */
    IN_PROJECT,
    /** The project holds none, and the home folder's {@code .agents/skills/} does. */
    ON_MACHINE,
    /** Neither holds any. */
    NONE;

    static final String SHARED = ".agents/skills";

    /** The verdict for {@code project}, with the home folder's skills as {@code home} read them. */
    public static ProjectSkillsScope of(Path project, HomeSkills home) {
        if (holdsOwnSkills(project)) return IN_PROJECT;
        return home.holdsAny(SHARED) ? ON_MACHINE : NONE;
    }

    /** Whether {@code project} holds any OpenSpec skill of its own, read without the home folder. */
    static boolean holdsOwnSkills(Path project) {
        for (String skill : AiToolRegistry.SKILL_NAMES) {
            if (Files.isRegularFile(project.resolve(SHARED).resolve(skill).resolve("SKILL.md"))) return true;
        }
        List<String> listed = SkillToolList.ids(SkillToolList.read(project));
        for (AiTool tool : AiToolRegistry.all()) {
            for (String skill : AiToolRegistry.SKILL_NAMES) {
                if (SkillLocator.find(project, listed, HomeSkills.NONE, tool, skill, false) != null) return true;
            }
        }
        return false;
    }

    /** {@link #of} for a project path that may be unknown. */
    public static ProjectSkillsScope of(@Nullable String projectBase, HomeSkills home) {
        return projectBase == null ? NONE : of(Path.of(projectBase), home);
    }
}
