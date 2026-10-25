package com.johnnyblabs.openspec.services;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.services.AiToolRegistry.AiTool;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Builds the full explore prompt by combining the explore instructions, project context, and topic.
 * <p>
 * The instructions are the explore file OpenSpec generated for the user's tool: the preferred tool from Settings,
 * then every tool in registry order, trying its skill and then its explore command. OpenSpec tailors that file's handoffs to the
 * tool, the delivery mode and the installed workflows, so only the generated file is faithful; the built-in default
 * is the last resort, and the result says which was used.
 */
@Service(Service.Level.PROJECT)
public final class ExplorePromptService {
    private static final Logger LOG = Logger.getInstance(ExplorePromptService.class);

    /** Where the prompt goes: an agent that has tools, or a Direct API model that has none. */
    public enum Delivery { AGENT, DIRECT_API }

    /**
     * The explore instructions and the project-relative file they came from, or {@code null} for the default. For a
     * generated file, also the tool it belongs to and which of that tool's explore files exist: upstream's invocation
     * depends on both (with {@code delivery: both} the skill is found first, but the command form is what's advertised).
     */
    public record Instructions(String text, @Nullable String sourcePath, @Nullable AiTool tool,
                               boolean hasCommand, boolean hasSkill) {
        public Instructions(String text, @Nullable String sourcePath) {
            this(text, sourcePath, null, false, false);
        }

        public boolean isBuiltInDefault() {
            return sourcePath == null;
        }
    }

    /**
     * The assembled prompt and the source of its instructions. {@code handoff} is set when the prompt is the short form,
     * what the user types to run their tool's own explore (e.g. {@code /opsx:explore why caching}), so notices can
     * quote it.
     */
    public record Prompt(String text, Instructions instructions, @Nullable String handoff) {
        public Prompt(String text, Instructions instructions) {
            this(text, instructions, null);
        }
    }

    /**
     * Put first in a Direct API prompt. The generated instructions are written for an agent that can run commands and
     * edit files; a Direct API model can do neither, so without this it may claim to have done both. The managed
     * file itself is never edited.
     */
    public static final String DIRECT_API_PREAMBLE = """
            **Note from the OpenSpec IDE plugin:** you are answering through an API, inside the IDE plugin, with no \
            tools. You cannot run commands (including `openspec`), read or search files, or write anything. The project \
            context below stands in for `openspec list`, `openspec status` and `openspec show`. Where the instructions \
            below tell you to run a command, read a file or capture something, work from that context instead, and \
            never claim to have run or changed anything. If the user wants to capture this as a change, tell them to \
            use the plugin's **Propose** action.""";

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
    private @Nullable Supplier<List<AiTool>> candidateToolsOverride;

    public ExplorePromptService(Project project) {
        this.project = project;
    }

    /** Replaces the preferred-then-detected tool list; {@code null} restores it. */
    @TestOnly
    void setCandidateToolsForTest(@Nullable Supplier<List<AiTool>> tools) {
        candidateToolsOverride = tools;
    }

    /**
     * Builds the full explore prompt for an agent: instructions + project context + topic.
     *
     * @param topic the user's explore topic, or empty/null for open exploration
     * @return the assembled prompt string
     */
    public String buildPrompt(String topic) {
        return build(topic, Delivery.AGENT).text();
    }

    /**
     * Builds the prompt for {@code delivery}; a Direct API prompt starts with {@link #DIRECT_API_PREAMBLE}. For an
     * agent whose tool has OpenSpec's explore command or skill installed, it's the short handoff instead: what the user
     * types to run that tool's own explore, by upstream's rules ({@link WorkflowInvocation}). The agent gathers the
     * project context itself, so none is assembled.
     */
    public Prompt build(String topic, Delivery delivery) {
        Instructions instructions = loadInstructions();
        if (delivery == Delivery.AGENT && instructions.tool() != null) {
            AiTool tool = instructions.tool();
            String invocation = WorkflowInvocation.of(tool.id(), WorkflowInvocation.EXPLORE,
                    WorkflowInvocation.EXPLORE_SKILL, AiToolRegistry.exploreCommandPath(tool),
                    instructions.hasCommand(), instructions.hasSkill());
            if (invocation != null) {
                String handoff = WorkflowInvocation.handoff(invocation, WorkflowInvocation.EXPLORE_SKILL, topic);
                return new Prompt(handoff, instructions, handoff);
            }
        }
        String body = instructions.text() + "\n\n---\n\n" + assembleContext() + "\n\n---\n\n" + buildTopicSection(topic);
        String text = delivery == Delivery.DIRECT_API ? DIRECT_API_PREAMBLE + "\n\n---\n\n" + body : body;
        return new Prompt(text, instructions);
    }

    /** The explore instructions' text, from the generated file or the built-in default. */
    String loadSkillInstructions() {
        return loadInstructions().text();
    }

    /** The explore instructions: for each candidate tool its skill, then its explore command; then the default. */
    Instructions loadInstructions() {
        String basePath = project.getBasePath();
        if (basePath != null) {
            for (AiTool tool : candidateTools()) {
                String skill = AiToolRegistry.exploreSkillPath(tool);
                String command = AiToolRegistry.exploreCommandPath(tool);
                boolean hasSkill = skill != null && Files.isRegularFile(Path.of(basePath, skill));
                boolean hasCommand = command != null && Files.isRegularFile(Path.of(basePath, command));
                for (String relative : new String[] {hasSkill ? skill : null, hasCommand ? command : null}) {
                    if (relative == null) continue;
                    Path path = Path.of(basePath, relative);
                    try {
                        String body = extractBody(relative, Files.readString(path));
                        if (body == null) continue;
                        LOG.info("Loaded explore instructions from: " + path);
                        return new Instructions(body, relative, tool, hasCommand, hasSkill);
                    } catch (IOException e) {
                        LOG.warn("Failed to read explore instructions: " + path, e);
                    }
                }
            }
        }
        LOG.info("No generated explore instructions found, using the built-in default");
        return new Instructions(DEFAULT_EXPLORE_PROMPT, null);
    }

    /** Project-relative paths to try, in order, without duplicates. */
    List<String> candidatePaths() {
        Set<String> paths = new LinkedHashSet<>();
        for (AiTool tool : candidateTools()) {
            String skill = AiToolRegistry.exploreSkillPath(tool);
            if (skill != null) paths.add(skill);
            String command = AiToolRegistry.exploreCommandPath(tool);
            if (command != null) paths.add(command);
        }
        return new ArrayList<>(paths);
    }

    /** The tools to look for explore files in: {@link WorkflowInvocation#candidateTools}. */
    private List<AiTool> candidateTools() {
        if (candidateToolsOverride != null) return candidateToolsOverride.get();
        return WorkflowInvocation.candidateTools(project);
    }

    /**
     * The instructions in a generated file: a TOML command's {@code prompt = """…"""} body, or Markdown with its YAML
     * frontmatter stripped. {@code null} when a TOML file has no prompt.
     */
    static @Nullable String extractBody(String relativePath, String content) {
        if (relativePath.endsWith(".toml")) {
            int start = content.indexOf("prompt = \"\"\"");
            if (start < 0) return null;
            start += "prompt = \"\"\"".length();
            int end = content.indexOf("\"\"\"", start);
            return end < 0 ? null : content.substring(start, end).strip();
        }
        if (content.startsWith("---")) {
            int endOfFrontmatter = content.indexOf("---", 3);
            if (endOfFrontmatter != -1) {
                return content.substring(endOfFrontmatter + 3).strip();
            }
        }
        return content;
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
