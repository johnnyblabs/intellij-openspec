package com.johnnyblabs.openspec.services;

import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * What a user types to start Explore in their AI tool, by upstream's own rules: the ones {@code openspec init}'s
 * "getting started" hint applies ({@code core/init.js} {@code startHintLines}, built from
 * {@code utils/command-references.js} and {@code core/command-generation/invocation.js}). The delivery mode is never
 * read; the explore files the CLI generated imply it. Contract-tested against a matrix captured from upstream for every
 * tool and delivery ({@code fixtures/cli/<version>/explore/invocations.json}).
 */
public final class ExploreInvocation {

    /** Tools upstream gives no slash invocation for skills; their skills are named in plain words. */
    private static final Set<String> PLAIN_WORDS_SKILL_TOOLS = Set.of("gsd", "rovodev", "codeassistant");

    /** The plain-words form, as upstream writes it. */
    static final String PLAIN_WORDS = "the openspec-explore skill";

    private ExploreInvocation() {
    }

    /**
     * The invocation for the tool upstream calls {@code toolId} (the registry's {@code id}, e.g. {@code devin}, not
     * the {@code windsurf} alias older CLIs accept), given which of its explore files exist, or {@code null} when it
     * has none.
     *
     * @param commandPath the tool's explore command path; its file name decides {@code /opsx-explore} vs
     *                    {@code /opsx:explore}
     */
    public static @Nullable String of(String toolId, @Nullable String commandPath, boolean hasCommand, boolean hasSkill) {
        if (!hasCommand && !hasSkill) return null;
        // Codex invokes skills directly (skills-invocable), in every delivery.
        if ("codex".equals(toolId) && hasSkill) return "$openspec-explore";
        // Devin with both: only Devin Desktop reads its workflows, while the skill works on both agents.
        boolean commandWins = hasCommand && !("devin".equals(toolId) && hasSkill);
        if (commandWins && commandPath != null) {
            String prefix = "amazon-q".equals(toolId) ? "@" : "/";
            String name = commandPath.substring(commandPath.lastIndexOf('/') + 1);
            return prefix + "opsx" + (name.startsWith("opsx-") ? "-" : ":") + "explore";
        }
        if (PLAIN_WORDS_SKILL_TOOLS.contains(toolId)) return PLAIN_WORDS;
        if ("kimi".equals(toolId)) return "/skill:openspec-explore";
        return "/openspec-explore";
    }

    /** What to hand the agent: the invocation and the topic, or for the plain-words form, a sentence naming the skill. */
    public static String handoff(String invocation, @Nullable String topic) {
        String t = topic == null ? "" : topic.strip();
        if (PLAIN_WORDS.equals(invocation)) {
            return t.isEmpty() ? "Use the openspec-explore skill." : "Use the openspec-explore skill: " + t;
        }
        return t.isEmpty() ? invocation : invocation + " " + t;
    }
}
