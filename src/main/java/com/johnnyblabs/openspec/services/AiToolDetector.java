package com.johnnyblabs.openspec.services;

import com.johnnyblabs.openspec.services.AiToolRegistry.AiTool;
import com.johnnyblabs.openspec.util.CliVersion;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Which AI tools a project has, decided the way the OpenSpec CLI decides it: a port of {@code getAvailableTools}
 * ({@code dist/core/available-tools.js}) and the shared-root reconciliation it calls
 * ({@code dist/core/shared-skill-target.js}). Contract-tested against upstream's verdicts on captured project
 * layouts ({@code fixtures/cli/<version>/detect/}).
 */
public final class AiToolDetector {

    private static final String TARGET_MARKER = ".openspec-target";

    private AiToolDetector() {
    }

    /** The tools detected in {@code project} by 1.x rules, in registry order; global tools are looked for under {@code home}. */
    public static List<AiTool> detect(Path project, @Nullable Path home) {
        return detect(project, home, DetectionRules.V1);
    }

    /** The tools detected in {@code project} by {@code rules}' release line, in registry order. */
    public static List<AiTool> detect(Path project, @Nullable Path home, DetectionRules rules) {
        return detect(project, home, rules, null);
    }

    /**
     * {@link #detect(Path, Path, DetectionRules)}, under 1.x rules as the 1.x {@code release} applies them. A
     * {@code null} release keeps 1.17's shapes and rules with no release filter.
     */
    public static List<AiTool> detect(Path project, @Nullable Path home, DetectionRules rules, @Nullable String release) {
        return rules.is2x() ? Detector2x.detect(project, home, rules) : detect1x(project, home, release);
    }

    /**
     * Upstream's {@code getAvailableTools} as the release had it ({@code dist/core/available-tools.js}): unchanged
     * 1.3.0-1.7.x; home-directory tools and shared-root reconciliation from 1.8.0; a tool's own detection path beside
     * a shared root it doesn't own from 1.11.0, unchanged since (1.15.0's file-view seam behaves the same).
     * <p>
     * The 1.8.0 rules need no gate of their own. Before 1.8.0 no two tools share a skills folder and none has a home
     * folder (every captured registry from 1.3.0 to 1.7.0), so with that release's tools and shapes, reconciliation
     * leaves each candidate its own folder's owner, which is exactly upstream's "every candidate is detected".
     */
    private static List<AiTool> detect1x(Path project, @Nullable Path home, @Nullable String release) {
        boolean known = release != null && !release.isBlank();
        List<AiTool> tools = new ArrayList<>();
        for (AiTool tool : AiToolRegistry.all()) {
            if (known && !AiToolRegistry.acceptedBy(tool, release)) continue;
            tools.add(AiToolRegistry.asOf1x(tool, release));
        }
        List<AiTool> candidates = new ArrayList<>();
        for (AiTool tool : tools) {
            if (isCandidate(project, home, tool)) candidates.add(tool);
        }
        Set<String> owners = new HashSet<>();
        for (AiTool tool : reconcile(project, candidates)) owners.add(tool.id());
        boolean independentPaths = !known || !CliVersion.below(release, "1.11.0");
        List<AiTool> detected = new ArrayList<>();
        for (AiTool tool : candidates) {
            if (tool.globalSkillsDir() != null || (independentPaths && hasIndependentDetectionPath(project, tool))
                    || owners.contains(tool.id())) {
                detected.add(AiToolRegistry.byId(tool.id()));
            }
        }
        return detected;
    }

    private static boolean isCandidate(Path project, @Nullable Path home, AiTool tool) {
        if (tool.globalSkillsDir() != null) {
            if (home == null) return false;
            Path skills = home.resolve(tool.globalSkillsDir()).resolve("skills");
            return AiToolRegistry.SKILL_NAMES.stream().anyMatch(n -> Files.exists(skills.resolve(n).resolve("SKILL.md")));
        }
        if (tool.skillsDir() == null) return false;
        if (!tool.detectionPaths().isEmpty()) {
            // A detection path may be a file or a directory.
            return tool.detectionPaths().stream().anyMatch(p -> Files.exists(project.resolve(p)));
        }
        return Files.isDirectory(project.resolve(tool.skillsDir()));
    }

    /** An existing detection path that isn't a skills root: those only count through reconciliation. */
    private static boolean hasIndependentDetectionPath(Path project, AiTool tool) {
        return tool.detectionPaths().stream()
                .anyMatch(p -> !p.endsWith("/skills") && Files.exists(project.resolve(p)));
    }

    /**
     * The tool that owns {@code tool}'s skills folder when registry tools share it, or {@code null} when none does:
     * upstream 1.x's {@code isSharedSkillTargetActive}, which reconciles every tool on that folder.
     */
    static @Nullable AiTool sharedRootOwner(Path project, AiTool tool) {
        return sharedRootOwner(project, tool, null);
    }

    /** {@link #sharedRootOwner(Path, AiTool)} among the tools a 1.x {@code release} has, as it shaped them. */
    static @Nullable AiTool sharedRootOwner(Path project, AiTool tool, @Nullable String release) {
        AiTool shaped = AiToolRegistry.asOf1x(tool, release);
        if (shaped.skillsDir() == null) return null;
        boolean known = release != null && !release.isBlank();
        List<AiTool> sharing = AiToolRegistry.all().stream()
                .filter(t -> !known || AiToolRegistry.acceptedBy(t, release))
                .map(t -> AiToolRegistry.asOf1x(t, release))
                .filter(t -> shaped.skillsDir().equals(t.skillsDir())).toList();
        return sharing.size() < 2 ? null : AiToolRegistry.byId(reconcile(project, sharing).getFirst().id());
    }

    /** One owner per skills root that several detected tools share; a tool alone on its root owns it. */
    private static List<AiTool> reconcile(Path project, List<AiTool> tools) {
        Map<String, List<AiTool>> byRoot = new LinkedHashMap<>();
        for (AiTool tool : tools) {
            if (tool.skillsDir() == null) continue;
            byRoot.computeIfAbsent(tool.skillsDir(), k -> new ArrayList<>()).add(tool);
        }
        List<AiTool> reconciled = new ArrayList<>();
        for (Map.Entry<String, List<AiTool>> entry : byRoot.entrySet()) {
            List<AiTool> group = entry.getValue();
            if (group.size() == 1) {
                reconciled.add(group.getFirst());
                continue;
            }
            String root = entry.getKey();
            AiTool marked = find(group, readMarker(project, root));
            if (marked != null) {
                reconciled.add(marked);
                continue;
            }
            String inferred = inferOwner(project, root);
            AiTool legacyCodex = group.stream()
                    .filter(t -> t.id().equals("codex") && hasLegacySkills(project, t)).findFirst().orElse(null);
            if (AiToolRegistry.UNIVERSAL_ID.equals(inferred) && legacyCodex != null) {
                // Before markers, selecting both produced a generic tree plus a Codex-only legacy tree.
                reconciled.add(legacyCodex);
                continue;
            }
            AiTool inferredTool = find(group, inferred);
            if (inferredTool != null) {
                reconciled.add(inferredTool);
                continue;
            }
            if (hasCurrentSkills(project, root)) {
                reconciled.add(universalOrFirst(group));
                continue;
            }
            AiTool legacy = group.stream().filter(t -> hasLegacySkills(project, t)).findFirst().orElse(null);
            reconciled.add(legacy != null ? legacy : universalOrFirst(group));
        }
        return reconciled;
    }

    private static @Nullable AiTool find(List<AiTool> group, @Nullable String id) {
        if (id == null) return null;
        return group.stream().filter(t -> t.id().equals(id)).findFirst().orElse(null);
    }

    private static AiTool universalOrFirst(List<AiTool> group) {
        AiTool universal = find(group, AiToolRegistry.UNIVERSAL_ID);
        return universal != null ? universal : group.getFirst();
    }

    /** The tool id {@code <root>/skills/.openspec-target} names (1.x's one-line marker), or {@code null}. */
    static @Nullable String readMarker(Path project, String root) {
        try {
            String value = Files.readString(project.resolve(root).resolve("skills").resolve(TARGET_MARKER),
                    StandardCharsets.UTF_8).trim();
            return value.isEmpty() ? null : value;
        } catch (IOException e) {
            return null;
        }
    }

    /** The owner implied by generated skills: Codex syntax names Codex, generic syntax the universal target. */
    private static @Nullable String inferOwner(Path project, String root) {
        boolean generic = false;
        for (String skill : AiToolRegistry.SKILL_NAMES) {
            try {
                String content = Files.readString(skillFile(project, root, skill), StandardCharsets.UTF_8);
                if (content.contains("$openspec-")) return "codex";
                if (content.contains("/openspec-")) generic = true;
            } catch (IOException e) {
                // A missing or unreadable skill says nothing about ownership.
            }
        }
        return generic ? AiToolRegistry.UNIVERSAL_ID : null;
    }

    private static boolean hasCurrentSkills(Path project, String root) {
        return AiToolRegistry.SKILL_NAMES.stream().anyMatch(s -> Files.exists(skillFile(project, root, s)));
    }

    private static boolean hasLegacySkills(Path project, AiTool tool) {
        return tool.legacySkillsDirs().stream().anyMatch(root ->
                AiToolRegistry.SKILL_NAMES.stream().anyMatch(s -> Files.exists(skillFile(project, root, s))));
    }

    private static Path skillFile(Path project, String root, String skill) {
        return project.resolve(root).resolve("skills").resolve(skill).resolve("SKILL.md");
    }
}
