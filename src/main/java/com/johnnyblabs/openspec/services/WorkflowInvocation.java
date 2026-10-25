package com.johnnyblabs.openspec.services;

import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.services.AiToolRegistry.AiTool;
import com.johnnyblabs.openspec.settings.OpenSpecSettings;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * What a user types to run an OpenSpec workflow (e.g. {@code explore}, {@code update}) in their AI tool, by upstream's own
 * rules: the ones {@code openspec init}'s "getting started" hint applies ({@code core/init.js} {@code startHintLines},
 * built from {@code utils/command-references.js} and {@code core/command-generation/invocation.js}). The delivery mode
 * is never read; the workflow files the CLI generated imply it. Contract-tested against a matrix captured from upstream
 * for every workflow, tool and delivery ({@code fixtures/cli/<version>/workflow-invocations.json}).
 */
public final class WorkflowInvocation {

    /** Explore: the workflow id and its skill. */
    public static final String EXPLORE = "explore";
    public static final String EXPLORE_SKILL = "openspec-explore";
    /** Update, which the plugin calls Revise Change: the workflow id and its skill. */
    public static final String UPDATE = "update";
    public static final String UPDATE_SKILL = "openspec-update-change";

    /** Tools upstream gives no slash invocation for skills; their skills are named in plain words. */
    private static final Set<String> PLAIN_WORDS_SKILL_TOOLS = Set.of("gsd", "rovodev", "codeassistant");

    private WorkflowInvocation() {
    }

    /**
     * The tools to look for a workflow's files in: the preferred tool from Settings, then every tool in registry order.
     * Only files that exist are used, so this finds whichever tools the project has. Detection isn't consulted: a
     * generated workflow file creates the directory detection looks for, except a commands-only tool detection can't
     * see (Cline's {@code .clinerules}, Kilo Code's {@code .kilo}), which this still finds.
     */
    public static List<AiTool> candidateTools(Project project) {
        Set<AiTool> tools = new LinkedHashSet<>();
        OpenSpecSettings settings = OpenSpecSettings.getInstance(project);
        if (settings != null) {
            AiTool preferred = AiToolRegistry.byName(settings.getPreferredTool());
            if (preferred != null) tools.add(preferred);
        }
        tools.addAll(AiToolRegistry.all());
        return new ArrayList<>(tools);
    }

    /** A tool that has the workflow installed in the project, and what to type to run it there. */
    public record Found(AiTool tool, String invocation) {
    }

    /**
     * The first of {@code candidates} with the workflow's command or skill in the project at {@code base}, and its
     * invocation, or {@code null} when none has either.
     */
    public static @Nullable Found find(Path base, List<AiTool> candidates, String workflowId, String skillName) {
        for (AiTool tool : candidates) {
            String commandPath = AiToolRegistry.commandPath(tool, workflowId);
            String skillPath = AiToolRegistry.skillPath(tool, skillName);
            boolean hasCommand = commandPath != null && Files.isRegularFile(base.resolve(commandPath));
            boolean hasSkill = skillPath != null && Files.isRegularFile(base.resolve(skillPath));
            String invocation = of(tool.id(), workflowId, skillName, commandPath, hasCommand, hasSkill);
            if (invocation != null) return new Found(tool, invocation);
        }
        return null;
    }

    /** The plain-words form upstream writes for {@code skillName}. */
    static String plainWords(String skillName) {
        return "the " + skillName + " skill";
    }

    /**
     * The invocation of {@code workflowId} for the tool upstream calls {@code toolId} (the registry's {@code id}, e.g.
     * {@code devin}, not the {@code windsurf} alias older CLIs accept), given which of its files for that workflow exist,
     * or {@code null} when it has neither.
     *
     * @param commandPath the tool's command path for the workflow; its file name decides {@code /opsx-<id>} vs
     *                    {@code /opsx:<id>}
     */
    public static @Nullable String of(String toolId, String workflowId, String skillName, @Nullable String commandPath,
                                      boolean hasCommand, boolean hasSkill) {
        if (!hasCommand && !hasSkill) return null;
        // Codex invokes skills directly (skills-invocable), in every delivery.
        if ("codex".equals(toolId) && hasSkill) return "$" + skillName;
        // Devin with both: only Devin Desktop reads its workflows, while the skill works on both agents.
        boolean commandWins = hasCommand && !("devin".equals(toolId) && hasSkill);
        if (commandWins && commandPath != null) {
            String prefix = "amazon-q".equals(toolId) ? "@" : "/";
            String name = commandPath.substring(commandPath.lastIndexOf('/') + 1);
            return prefix + "opsx" + (name.startsWith("opsx-") ? "-" : ":") + workflowId;
        }
        if (PLAIN_WORDS_SKILL_TOOLS.contains(toolId)) return plainWords(skillName);
        if ("kimi".equals(toolId)) return "/skill:" + skillName;
        return "/" + skillName;
    }

    /**
     * What to hand the agent: the invocation and its arguments, or for the plain-words form, a sentence naming the
     * skill.
     */
    public static String handoff(String invocation, String skillName, @Nullable String args) {
        String a = args == null ? "" : args.strip();
        if (plainWords(skillName).equals(invocation)) {
            return a.isEmpty() ? "Use the " + skillName + " skill." : "Use the " + skillName + " skill: " + a;
        }
        return a.isEmpty() ? invocation : invocation + " " + a;
    }
}
