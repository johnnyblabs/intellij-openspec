package com.johnnyblabs.openspec.services;

import com.johnnyblabs.openspec.services.AiToolRegistry.AiTool;
import com.johnnyblabs.openspec.util.CliVersion;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/**
 * Which release line's tool-detection rules apply. Upstream's verdicts differ between lines for the same files (a lone
 * {@code .gsd} folder is {@code agents} to 1.17 and {@code gsd} to 2.x; 2.1 never counts a machine-wide install), so
 * detection follows the installed CLI's line, while finding a skill file is decided from disk alone.
 * <p>
 * The 2.x differences the plugin's port depends on, read from the published packages:
 * <ul>
 *   <li>2.0: list ids are taken as written (no alias resolution); a listed tool with files of its own is present
 *       through its link folder only; MiniMax Code installs into the home directory.</li>
 *   <li>2.1: list ids go through upstream's aliases; a listed tool is also present through its former own folder;
 *       MiniMax Code is a project tool linked from {@code .minimax/skills}.</li>
 *   <li>2.1.1: a {@code skills-lock.json} recording OpenSpec's skills also names the {@code agents} tool.</li>
 * </ul>
 */
public enum DetectionRules {
    V1(null, false),
    V2_0(SkillLinkDirs.Line.V2_0, false),
    V2_1(SkillLinkDirs.Line.V2_1, false),
    /** 2.1.1 and later: {@link #V2_1} plus the {@code skills-lock.json} inference. */
    V2_1_1(SkillLinkDirs.Line.V2_1, true);

    private final @Nullable SkillLinkDirs.Line linkLine;
    private final boolean readsSkillsLock;

    DetectionRules(@Nullable SkillLinkDirs.Line linkLine, boolean readsSkillsLock) {
        this.linkLine = linkLine;
        this.readsSkillsLock = readsSkillsLock;
    }

    /** The rules of the CLI {@code version}, or {@code null} when no CLI was detected. */
    public static @Nullable DetectionRules forVersion(@Nullable String version) {
        if (version == null || version.isBlank()) return null;
        if (!CliVersion.atLeast(version, "2.0.0")) return V1;
        if (!CliVersion.atLeast(version, "2.1.0")) return V2_0;
        return CliVersion.atLeast(version, "2.1.1") ? V2_1_1 : V2_1;
    }

    /**
     * The rules when no CLI is detected, from what is on disk: the newest 2.x rules when the project's tool list names
     * more than one tool or carries an {@code own-files} mark, or a registry tool's folder holds a link (or link text)
     * into {@code .agents/skills}; otherwise 1.x rules.
     */
    public static DetectionRules fromDisk(Path project) {
        try {
            Path list = SkillToolList.path(project);
            if (Files.isRegularFile(list) && SkillToolList.looks2x(Files.readString(list, StandardCharsets.UTF_8))) {
                return V2_1_1;
            }
        } catch (IOException | RuntimeException e) {
            // An unreadable list says nothing either way.
        }
        Path shared = project.resolve(".agents").resolve("skills");
        for (AiTool tool : AiToolRegistry.all()) {
            for (String dir : SkillLinkDirs.linkDirsAnyLine(tool.id())) {
                for (String skill : AiToolRegistry.SKILL_NAMES) {
                    Path spot = project.resolve(dir).resolve(skill);
                    if (!Files.exists(spot, LinkOption.NOFOLLOW_LINKS)) continue;
                    if (SkillSpot.isOpenSpecLink(SkillSpot.classify(spot, shared.resolve(skill)))) return V2_1_1;
                }
            }
        }
        return V1;
    }

    public boolean is2x() {
        return this != V1;
    }

    /** The link-folder table for this line; {@code null} for 1.x, which makes no links. */
    @Nullable SkillLinkDirs.Line linkLine() {
        return linkLine;
    }

    boolean readsSkillsLock() {
        return readsSkillsLock;
    }

    boolean resolvesListAliases() {
        return this == V2_1 || this == V2_1_1;
    }

    /**
     * Whether this line's registry has the tool, by its release range: 2.0 retired Amazon Q, and 2.1 added the tools
     * of its bundled installer. Under 1.x rules every tool is read, as before.
     */
    boolean knows(AiTool tool) {
        return switch (this) {
            case V1 -> true;
            case V2_0 -> AiToolRegistry.acceptedBy(tool, "2.0.0");
            case V2_1, V2_1_1 -> AiToolRegistry.acceptedBy(tool, "2.1.0");
        };
    }

    /** The home-directory skills folder the tool has on this line, or {@code null}: MiniMax Code became a project tool in 2.1. */
    @Nullable String globalSkillsDir(AiTool tool) {
        if ((this == V2_1 || this == V2_1_1) && "minimax-code".equals(tool.id())) return null;
        return tool.globalSkillsDir();
    }

    /** The project skills folder the tool has on this line, or {@code null}. */
    @Nullable String skillsDir(AiTool tool) {
        if ((this == V2_1 || this == V2_1_1) && "minimax-code".equals(tool.id())) return ".minimax";
        return tool.skillsDir();
    }
}
