package com.johnnyblabs.openspec.services;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.johnnyblabs.openspec.services.AiToolRegistry.AiTool;
import com.johnnyblabs.openspec.services.SkillToolList.Entry;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.*;

/**
 * Tool detection by CLI 2.x rules: a port of upstream's 2.x {@code getAvailableTools} ({@code dist/core/available-tools.js})
 * with what it calls in {@code shared-skill-target.js}, {@code shared/tool-detection.js} ({@code isListedToolPresent},
 * {@code hasSkillLinks}, {@code toolHasAnyConfiguredCommand}) and {@code shared/skill-paths.js}. The line-specific
 * differences come from {@link DetectionRules}. Contract-tested against upstream's verdicts on captured layouts for
 * every 2.x release ({@code fixtures/cli/<version>/detect/}).
 */
final class Detector2x {

    private static final String SHARED_ROOT = ".agents";

    /** Upstream's {@code COMMAND_IDS}: the commands an adapter may have written. */
    static final List<String> COMMAND_IDS = List.of("explore", "new", "continue", "apply", "update", "ff",
            "sync", "archive", "bulk-archive", "verify", "onboard", "propose");

    private Detector2x() {
    }

    static List<AiTool> detect(Path project, @Nullable Path home, DetectionRules rules) {
        Context ctx = new Context(project, rules);
        List<AiTool> detected = new ArrayList<>();
        for (AiTool tool : AiToolRegistry.all()) {
            if (!rules.knows(tool)) continue;
            if (isAvailable(ctx, home, tool)) detected.add(tool);
        }
        return detected;
    }

    /** What one detection run reads once: the listed tools (with inference) and the tools marked {@code own-files}. */
    private static final class Context {
        final Path project;
        final DetectionRules rules;
        final List<String> listed;
        final Set<String> ownFiles;

        Context(Path project, DetectionRules rules) {
            this.project = project;
            this.rules = rules;
            List<Entry> entries = SkillToolList.read(project, rules.resolvesListAliases(),
                    id -> rules.knows(AiToolRegistry.byId(id)));
            Set<String> own = new HashSet<>();
            for (Entry e : entries) if (e.ownFiles()) own.add(e.id());
            this.ownFiles = own;
            List<String> ids = SkillToolList.ids(entries);
            if (ids.isEmpty()) {
                String inferred = inferPreListTool(project);
                if (inferred == null && rules.readsSkillsLock()) inferred = inferSkillsLockTool(project);
                ids = inferred == null ? List.of() : List.of(inferred);
            }
            this.listed = ids;
        }
    }

    private static boolean isAvailable(Context ctx, @Nullable Path home, AiTool tool) {
        String global = ctx.rules.globalSkillsDir(tool);
        String skillsDir = ctx.rules.skillsDir(tool);
        if (skillsDir == null && global == null) return false;
        if (global != null) {
            if (home == null) return false;
            Path skills = home.resolve(global).resolve("skills");
            return AiToolRegistry.SKILL_NAMES.stream().anyMatch(n -> Files.exists(skills.resolve(n).resolve("SKILL.md")));
        }
        boolean shared = SHARED_ROOT.equals(skillsDir);
        boolean hasOwnPath;
        if (!tool.detectionPaths().isEmpty()) {
            hasOwnPath = tool.detectionPaths().stream()
                    .filter(p -> !shared || !isSharedPath(p))
                    .anyMatch(p -> {
                        // Presence, not type: a detection path may be a file or a directory.
                        if (!Files.exists(ctx.project.resolve(p))) return false;
                        // When the project lists its tools and leaves this one out, a skills folder of its own counts
                        // only with an OpenSpec skill in it.
                        if (p.endsWith("/skills") && !ctx.listed.isEmpty() && !ctx.listed.contains(tool.id())) {
                            return hasOpenSpecSkillIn(ctx.project.resolve(p), AiToolRegistry.SKILL_NAMES);
                        }
                        return true;
                    });
        } else {
            hasOwnPath = !shared && Files.isDirectory(ctx.project.resolve(skillsDir));
        }
        // usesSharedSkills: every project tool on 2.x.
        return hasOwnPath || (ctx.listed.contains(tool.id()) && isListedToolPresent(ctx, tool, skillsDir));
    }

    private static boolean isSharedPath(String p) {
        return p.equals(SHARED_ROOT) || p.startsWith(SHARED_ROOT + "/skills");
    }

    /** Upstream's {@code isListedToolPresent}, for a tool already known to be listed. */
    private static boolean isListedToolPresent(Context ctx, AiTool tool, String skillsDir) {
        if (!ctx.ownFiles.contains(tool.id())) return true;
        if (hasAnyCommand(ctx.project, tool)) return true;
        Path linkDir = linkDir(ctx, tool);
        Path probe = linkDir;
        // 2.1 falls back to the tool's former own folder when it gets no links.
        if (probe == null && ctx.rules.linkLine() == SkillLinkDirs.Line.V2_1) probe = formerOwnDir(ctx.project, skillsDir);
        if (probe != null && hasOpenSpecSkillIn(probe, AiToolRegistry.SKILL_NAMES)) return true;
        return linkDir != null && hasSkillLinks(ctx.project, linkDir);
    }

    private static @Nullable Path linkDir(Context ctx, AiTool tool) {
        String dir = ctx.rules.linkLine() == null ? null : SkillLinkDirs.linkDir(ctx.rules.linkLine(), tool.id());
        return dir == null ? null : ctx.project.resolve(dir);
    }

    /** Upstream's {@code resolveFormerOwnSkillsDir}: {@code <skillsDir>/skills}, except for tools already in {@code .agents}. */
    private static @Nullable Path formerOwnDir(Path project, String skillsDir) {
        return SHARED_ROOT.equals(skillsDir) ? null : project.resolve(skillsDir).resolve("skills");
    }

    /**
     * Upstream's {@code hasSkillLinks}: the tool's link folder still holds OpenSpec's link to a shared skill, even one
     * that points nowhere now, or the text git writes for one.
     */
    private static boolean hasSkillLinks(Path project, Path linkDir) {
        Path shared = project.resolve(SHARED_ROOT).resolve("skills");
        for (String name : AiToolRegistry.SKILL_NAMES) {
            Path spot = linkDir.resolve(name);
            SkillSpot.Shape shape = SkillSpot.classify(spot, shared.resolve(name));
            if (Files.isSymbolicLink(spot) || isJunction(spot)) {
                if (shape == SkillSpot.Shape.LINK || shape == SkillSpot.Shape.BROKEN) return true;
            } else if (shape == SkillSpot.Shape.RELEASED) {
                return true;
            }
        }
        return false;
    }

    private static boolean isJunction(Path p) {
        try {
            return Files.readAttributes(p, java.nio.file.attribute.BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS).isOther();
        } catch (IOException e) {
            return false;
        }
    }

    /** Upstream's {@code toolHasAnyConfiguredCommand}: any of the tool's command files exists. */
    private static boolean hasAnyCommand(Path project, AiTool tool) {
        if (AiToolRegistry.exploreCommandPath(tool) == null) return false;
        for (String id : COMMAND_IDS) {
            String path = AiToolRegistry.commandPath(tool, id);
            if (path != null && Files.exists(project.resolve(path))) return true;
        }
        return false;
    }

    private static boolean hasOpenSpecSkillIn(Path skillsDir, Collection<String> names) {
        return names.stream().anyMatch(n -> Files.exists(skillsDir.resolve(n).resolve("SKILL.md")));
    }

    /**
     * Upstream's {@code inferPreListTool}: the tool a shared folder written before any list was for, from the invocation
     * syntax of a skill that is exactly a release's own output ({@link ReleasedSkillFingerprints}).
     */
    static @Nullable String inferPreListTool(Path project) {
        Path shared = project.resolve(SHARED_ROOT).resolve("skills");
        if (!Files.exists(shared)) return null;
        boolean generic = false;
        for (String skill : AiToolRegistry.SKILL_NAMES) {
            Path file = shared.resolve(skill).resolve("SKILL.md");
            if (!Files.exists(file)) continue;
            try {
                String content = Files.readString(file, StandardCharsets.UTF_8);
                if (!ReleasedSkillFingerprints.isReleased(content)) continue;
                if (content.contains("$openspec-")) return "codex";
                if (content.contains("/openspec-")) generic = true;
            } catch (IOException | RuntimeException e) {
                // Missing or unreadable files give no signal.
            }
        }
        return generic ? AiToolRegistry.UNIVERSAL_ID : null;
    }

    /**
     * Upstream's {@code inferSkillsLockTool} (2.1.1): {@code skills-lock.json} records a skill of OpenSpec's, from
     * {@code Fission-AI/OpenSpec}, that is in the shared folder, so the folder is set up for the {@code agents} tool.
     */
    static @Nullable String inferSkillsLockTool(Path project) {
        try {
            Path lock = project.resolve("skills-lock.json");
            if (!Files.exists(lock)) return null;
            JsonElement root = JsonParser.parseString(Files.readString(lock, StandardCharsets.UTF_8));
            JsonObject skills = root.isJsonObject() && root.getAsJsonObject().get("skills") instanceof JsonObject o ? o : new JsonObject();
            List<String> fromOpenSpec = new ArrayList<>();
            for (String name : AiToolRegistry.SKILL_NAMES) {
                if (skills.get(name) instanceof JsonObject entry && isOpenSpecSource(entry.get("source"))) fromOpenSpec.add(name);
            }
            Path shared = project.resolve(SHARED_ROOT).resolve("skills");
            return !fromOpenSpec.isEmpty() && hasOpenSpecSkillIn(shared, fromOpenSpec) ? AiToolRegistry.UNIVERSAL_ID : null;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** Upstream's {@code isOpenSpecSource}: {@code Fission-AI/OpenSpec} as a shorthand or GitHub URL, optionally {@code /skills}. */
    static boolean isOpenSpecSource(@Nullable JsonElement source) {
        if (source == null || !source.isJsonPrimitive() || !source.getAsJsonPrimitive().isString()) return false;
        String repo = source.getAsString().strip().toLowerCase(Locale.ROOT)
                .replaceFirst("^(?:https?://|git@)github\\.com[/:]", "")
                .replaceFirst("/+$", "")
                .replaceFirst("/skills$", "")
                .replaceFirst("\\.git$", "");
        return repo.equals("fission-ai/openspec");
    }
}
