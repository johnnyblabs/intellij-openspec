package com.johnnyblabs.openspec.services;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Builds the full explore prompt by combining skill instructions, project context, and topic.
 * Reads the explore skill file from the project when available, falling back to a built-in default.
 */
@Service(Service.Level.PROJECT)
public final class ExplorePromptService {
    private static final Logger LOG = Logger.getInstance(ExplorePromptService.class);

    /**
     * Skill file paths to search, in priority order.
     */
    private static final String[] SKILL_FILE_PATHS = {
            // Skills-era location first — the CLI's tracked skill surface since its 1.5.0
            // skills-only migration (1.6 stamps allowed-tools/generatedBy frontmatter,
            // which the frontmatter stripping below removes).
            ".claude/skills/openspec-explore/SKILL.md",
            // Legacy pre-1.5 command paths, kept for still-supported 1.3/1.4 projects.
            ".claude/commands/opsx/explore.md",
            ".augment/commands/opsx-explore.md",
            ".github/prompts/opsx-explore.prompt.md"
    };

    static final String DEFAULT_EXPLORE_PROMPT = """
            Enter explore mode. Think deeply. Visualize freely. Follow the conversation wherever it goes.

            **IMPORTANT: Explore mode is for thinking, not implementing.** You may read files, search code, \
            and investigate the codebase, but you must NEVER write code or implement features.

            **This is a stance, not a workflow.** There are no fixed steps, no required sequence, no mandatory outputs. \
            You're a thinking partner helping the user explore.

            ## The Stance

            - **Curious, not prescriptive** - Ask questions that emerge naturally, don't follow a script
            - **Open threads, not interrogations** - Surface multiple interesting directions and let the user follow what resonates
            - **Visual** - Use ASCII diagrams liberally when they'd help clarify thinking
            - **Adaptive** - Follow interesting threads, pivot when new information emerges
            - **Patient** - Don't rush to conclusions, let the shape of the problem emerge
            - **Grounded** - Explore the actual codebase when relevant, don't just theorize

            ## What You Might Do

            - Explore the problem space: ask clarifying questions, challenge assumptions, reframe the problem
            - Investigate the codebase: map architecture, find integration points, surface hidden complexity
            - Compare options: brainstorm approaches, build comparison tables, sketch tradeoffs
            - Visualize: use ASCII diagrams for system diagrams, state machines, data flows
            - Surface risks and unknowns: identify what could go wrong, find gaps in understanding

            ## Guardrails

            - Don't implement - never write code or implement features
            - Don't fake understanding - if something is unclear, dig deeper
            - Don't rush - discovery is thinking time, not task time
            - Don't force structure - let patterns emerge naturally
            - Do visualize - a good diagram is worth many paragraphs
            - Do explore the codebase - ground discussions in reality
            - Do question assumptions - including the user's and your own
            """;

    private final Project project;

    public ExplorePromptService(Project project) {
        this.project = project;
    }

    /**
     * Builds the full explore prompt: skill instructions + project context + topic.
     *
     * @param topic the user's explore topic, or empty/null for open exploration
     * @return the assembled prompt string
     */
    public String buildPrompt(String topic) {
        String instructions = loadSkillInstructions();
        String context = assembleContext();
        String topicSection = buildTopicSection(topic);

        return instructions + "\n\n---\n\n" + context + "\n\n---\n\n" + topicSection;
    }

    /**
     * Reads the explore skill file from the project, falling back to the built-in default.
     */
    String loadSkillInstructions() {
        String basePath = project.getBasePath();
        if (basePath != null) {
            for (String skillPath : SKILL_FILE_PATHS) {
                Path path = Path.of(basePath, skillPath);
                if (Files.isRegularFile(path)) {
                    try {
                        String content = Files.readString(path);
                        // Strip YAML frontmatter if present
                        if (content.startsWith("---")) {
                            int endOfFrontmatter = content.indexOf("---", 3);
                            if (endOfFrontmatter != -1) {
                                content = content.substring(endOfFrontmatter + 3).strip();
                            }
                        }
                        LOG.info("Loaded explore skill from: " + path);
                        return content;
                    } catch (IOException e) {
                        LOG.warn("Failed to read explore skill file: " + path, e);
                    }
                }
            }
        }
        LOG.info("No explore skill file found, using built-in default");
        return DEFAULT_EXPLORE_PROMPT;
    }

    private String assembleContext() {
        ExploreContextService contextService = project.getService(ExploreContextService.class);
        if (contextService != null) {
            return contextService.assembleContext();
        }
        return "# Project Context\n\nNo project context available.";
    }

    private static String buildTopicSection(String topic) {
        if (topic == null || topic.isBlank()) {
            return "**Topic:** Open exploration — what would you like to think about?";
        }
        return "**Topic:** " + topic.strip();
    }
}
