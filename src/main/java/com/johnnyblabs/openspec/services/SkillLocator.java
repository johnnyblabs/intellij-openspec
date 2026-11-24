package com.johnnyblabs.openspec.services;

import com.johnnyblabs.openspec.services.AiToolRegistry.AiTool;
import com.johnnyblabs.openspec.services.SkillSpot.Shape;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Where a tool's OpenSpec skill actually is, decided from what is on disk, never from the CLI version: a 1.x copy in
 * the tool's own folder is found under a 2.x CLI and the other way round. The order mirrors upstream's
 * {@code skillDirsForTool} ({@code dist/core/shared/tool-detection.js}), then adds the home directory:
 * <ol>
 *   <li>the shared {@code .agents/skills/}, when {@code .agents/skills/.openspec-target} lists the tool. Only then is
 *       the shared copy the tool's: every 2.x tool's skills sit there, so the folder alone names no tool;</li>
 *   <li>the tool's link folders (2.1's and 2.0's), read through a link or a copy; a link git checked out as text
 *       leads to the shared copy;</li>
 *   <li>the tool's own {@code <skillsDir>/skills/} (a 1.x copy, or the shared folder for tools whose folder is
 *       {@code .agents}, unless a list leaves the tool out), then its older folders;</li>
 *   <li>the home directory, from a {@link HomeSkills} snapshot: the tool's home link folders and 1.x home folder, then
 *       {@code ~/.agents/skills/} for a tool that reads that folder itself ({@code .agents} tools, or the tool the user
 *       prefers).</li>
 * </ol>
 * Pure nio. Revise and Continue call it on the EDT, as they did their file checks before: a few attribute reads per
 * candidate tool, the same order as the two existence checks each tool cost earlier; a link spot adds its real path.
 */
public final class SkillLocator {

    private static final String SHARED = ".agents/skills";

    private SkillLocator() {
    }

    /**
     * The {@code SKILL.md} of {@code skill} for {@code tool} in {@code project}, or {@code null} when it has none.
     *
     * @param preferred whether {@code tool} is the user's preferred tool, which may read a machine-wide shared skill
     */
    public static @Nullable Path find(Path project, HomeSkills home, AiTool tool, String skill, boolean preferred) {
        return find(project, SkillToolList.ids(SkillToolList.read(project)), home, tool, skill, preferred);
    }

    /** {@link #find}, with the project's listed tools already read (one read for many tools). */
    public static @Nullable Path find(Path project, List<String> listed, HomeSkills home, AiTool tool, String skill,
                                      boolean preferred) {
        Path shared = project.resolve(SHARED).resolve(skill);
        Path sharedFile = shared.resolve("SKILL.md");

        if (listed.contains(tool.id()) && Files.isRegularFile(sharedFile)) {
            return sharedFile;
        }
        List<String> linkDirs = SkillLinkDirs.linkDirsAnyLine(tool.id());
        for (String dir : linkDirs) {
            Path spot = project.resolve(dir).resolve(skill);
            Shape shape = SkillSpot.classify(spot, shared);
            if (shape == Shape.LINK || shape == Shape.COPY) {
                Path file = spot.resolve("SKILL.md");
                if (Files.isRegularFile(file)) return file;
            } else if (shape == Shape.RELEASED && Files.isRegularFile(sharedFile)) {
                return sharedFile;
            }
        }
        // A tool whose own folder is .agents owns what's there only when the list names it, or when there is no list
        // (a 1.x tree): upstream counts nothing under .agents as that tool's own evidence otherwise.
        boolean sharedIsNotItsOwn = ".agents".equals(tool.skillsDir()) && !listed.isEmpty() && !listed.contains(tool.id());
        if (tool.skillsDir() != null && !sharedIsNotItsOwn) {
            Path own = project.resolve(tool.skillsDir()).resolve("skills").resolve(skill).resolve("SKILL.md");
            if (Files.isRegularFile(own)) return own;
        }
        for (String legacy : tool.legacySkillsDirs()) {
            Path file = project.resolve(legacy).resolve("skills").resolve(skill).resolve("SKILL.md");
            if (Files.isRegularFile(file)) return file;
        }
        for (String dir : linkDirs) {
            Path file = home.skill(dir, skill);
            if (file != null) return file;
        }
        if (tool.globalSkillsDir() != null) {
            Path file = home.skill(tool.globalSkillsDir() + "/skills", skill);
            if (file != null) return file;
        }
        if (preferred || ".agents".equals(tool.skillsDir())) {
            return home.skill(SHARED, skill);
        }
        return null;
    }
}
