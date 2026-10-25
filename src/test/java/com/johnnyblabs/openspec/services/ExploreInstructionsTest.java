package com.johnnyblabs.openspec.services;

import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.services.AiToolRegistry.AiTool;
import com.johnnyblabs.openspec.services.ExplorePromptService.Delivery;
import com.johnnyblabs.openspec.services.ExplorePromptService.Instructions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Which generated explore file the prompt uses, how its body is read, and the Direct API preamble. The files are the
 * real ones {@code openspec init} generated ({@code fixtures/cli/<version>/explore/}).
 */
class ExploreInstructionsTest {

    private static final Path FIXTURES = AiToolRegistryContractTest.FIXTURES.resolve(AiToolRegistryContractTest.TARGET)
            .resolve("explore");

    @TempDir Path project;
    private ExplorePromptService service;

    @BeforeEach
    void setUp() {
        Project p = mock(Project.class);
        when(p.getBasePath()).thenReturn(project.toString());
        service = new ExplorePromptService(p);
    }

    private static AiTool tool(String id) {
        return requireNonNull(AiToolRegistry.byId(id), id);
    }

    private static <T> T requireNonNull(T value, String what) {
        assertNotNull(value, what);
        return value;
    }

    private void place(String relative, String fixture) throws IOException {
        Path target = project.resolve(relative);
        Files.createDirectories(target.getParent());
        Files.copy(FIXTURES.resolve(fixture), target);
    }

    private static String fixture(String name) throws IOException {
        return Files.readString(FIXTURES.resolve(name));
    }

    @Test
    void aToolsSkillWinsOverItsOwnCommand() throws IOException {
        place(".claude/skills/openspec-explore/SKILL.md", "claude-skill-both.md");
        place(".claude/commands/opsx/explore.md", "claude-command.md");
        service.setCandidateToolsForTest(() -> List.of(tool("claude")));

        Instructions instructions = service.loadInstructions();

        assertEquals(".claude/skills/openspec-explore/SKILL.md", instructions.sourcePath());
    }

    @Test
    void theCommandIsUsedWhenTheToolHasNoSkill() throws IOException {
        place(".cursor/commands/opsx-explore.md", "cursor-command.md");
        service.setCandidateToolsForTest(() -> List.of(tool("cursor")));

        Instructions instructions = service.loadInstructions();

        assertEquals(".cursor/commands/opsx-explore.md", instructions.sourcePath());
        assertEquals(ExplorePromptService.extractBody(".md", fixture("cursor-command.md")), instructions.text());
    }

    @Test
    void theFirstCandidateToolWins() throws IOException {
        place(".claude/skills/openspec-explore/SKILL.md", "claude-skill-both.md");
        place(".cursor/commands/opsx-explore.md", "cursor-command.md");
        service.setCandidateToolsForTest(() -> List.of(tool("cursor"), tool("claude")));

        assertEquals(".cursor/commands/opsx-explore.md", service.loadInstructions().sourcePath());
    }

    @Test
    void candidatePathsListEachToolsSkillThenItsCommand() {
        service.setCandidateToolsForTest(() -> List.of(tool("cursor"), tool("codex")));

        assertEquals(List.of(
                ".cursor/skills/openspec-explore/SKILL.md",
                ".cursor/commands/opsx-explore.md",
                ".agents/skills/openspec-explore/SKILL.md"), service.candidatePaths());
    }

    @Test
    void theSharedAgentsSkillIsReadForAnyToolThatSharesIt() throws IOException {
        place(".agents/skills/openspec-explore/SKILL.md", "agents-skill.md");
        service.setCandidateToolsForTest(() -> List.of(tool("zed")));

        assertEquals(".agents/skills/openspec-explore/SKILL.md", service.loadInstructions().sourcePath());
    }

    @Test
    void withNoGeneratedFileTheBuiltInDefaultIsUsedAndSaysSo() {
        service.setCandidateToolsForTest(() -> List.of(tool("cursor")));

        Instructions instructions = service.loadInstructions();

        assertTrue(instructions.isBuiltInDefault());
        assertNull(instructions.sourcePath());
        assertEquals(ExplorePromptService.DEFAULT_EXPLORE_PROMPT, instructions.text());
    }

    @Test
    void aTomlCommandsPromptBodyIsTaken() throws IOException {
        String toml = fixture("gemini-command.toml");
        String body = ExplorePromptService.extractBody(".gemini/commands/opsx/explore.toml", toml);

        assertNotNull(body);
        assertTrue(body.startsWith("Enter explore mode. Think deeply."), body.substring(0, 60));
        assertTrue(body.endsWith("Including the user's and your own"), body.substring(body.length() - 60));
        assertFalse(body.contains("prompt = \"\"\""));
        assertFalse(body.contains("description ="));
    }

    @Test
    void aTomlFileWithoutAPromptFallsThroughToTheNextCandidate() throws IOException {
        Files.createDirectories(project.resolve(".gemini/commands/opsx"));
        Files.writeString(project.resolve(".gemini/commands/opsx/explore.toml"), "description = \"no prompt here\"\n");
        place(".claude/skills/openspec-explore/SKILL.md", "claude-skill-both.md");
        service.setCandidateToolsForTest(() -> List.of(tool("gemini"), tool("claude")));

        assertEquals(".claude/skills/openspec-explore/SKILL.md", service.loadInstructions().sourcePath());
    }

    @Test
    void aSkillsFrontmatterIsStripped() throws IOException {
        String skill = fixture("claude-skill-both.md");
        assertTrue(skill.startsWith("---"), "the captured skill has frontmatter");

        String body = ExplorePromptService.extractBody(".claude/skills/openspec-explore/SKILL.md", skill);

        assertTrue(body.startsWith("Enter explore mode."), body.substring(0, 40));
        assertFalse(body.contains("generatedBy"));
    }

    @Test
    void theGeneratedHandoffsReachThePromptUnchanged() throws IOException {
        place(".claude/skills/openspec-explore/SKILL.md", "claude-skill-explore-only.md");
        service.setCandidateToolsForTest(() -> List.of(tool("claude")));

        String text = service.loadInstructions().text();

        assertTrue(text.contains("I can capture this as a change"), "the explore-only profile's own handoff");
        assertFalse(text.contains("/opsx:propose"), "no handoff to a workflow the profile doesn't install");
    }

    @Test
    void theFrontmatterStripKeepsTheWholeBodyDespiteItsHorizontalRules() throws IOException {
        List<String> lines = Files.readAllLines(FIXTURES.resolve("claude-skill-both.md"));
        assertEquals("---", lines.get(10), "the captured skill's frontmatter ends on line 11");
        assertTrue(lines.subList(11, lines.size()).contains("---"), "the body has its own horizontal rules");
        String expected = String.join("\n", lines.subList(11, lines.size())).strip();

        assertEquals(expected, ExplorePromptService.extractBody("SKILL.md", fixture("claude-skill-both.md")));
    }

    @Test
    void aTomlCommandsBodyEqualsTheSameCommandInMarkdown() throws IOException {
        // Two captured formats of the same generated command check each other; no expected text is written here.
        String fromToml = ExplorePromptService.extractBody("explore.toml", fixture("gemini-command.toml"));
        String fromMarkdown = ExplorePromptService.extractBody("explore.md", fixture("claude-command.md"));

        assertEquals(fromMarkdown, fromToml);
    }

    @Test
    void theCapturedSkillsDifferByDeliveryMode() throws IOException {
        assertNotEquals(fixture("claude-skill-both.md"), fixture("claude-skill-skills.md"),
                "delivery: skills rewrites the handoffs, so the two captures can't be the same file");
    }

    @Test
    void aCommandsOnlyToolThatDetectionMissesIsStillFound() throws IOException {
        // Under delivery: commands, Cline writes only .clinerules/, which isn't one of its detection paths.
        Path target = project.resolve(".clinerules/workflows/opsx-explore.md");
        Files.createDirectories(target.getParent());
        Files.copy(FIXTURES.resolve("cursor-command.md"), target);
        // The real candidate list: no Settings and no detection on this project, so it is the registry tail.
        service.setCandidateToolsForTest(null);

        assertEquals(".clinerules/workflows/opsx-explore.md", service.loadInstructions().sourcePath());
    }

    @Test
    void aDirectApiPromptStartsWithThePreambleThenInstructionsContextAndTopic() throws IOException {
        place(".claude/skills/openspec-explore/SKILL.md", "claude-skill-both.md");
        service.setCandidateToolsForTest(() -> List.of(tool("claude")));

        String text = service.build("caching", Delivery.DIRECT_API).text();

        assertTrue(text.startsWith(ExplorePromptService.DIRECT_API_PREAMBLE), text.substring(0, 80));
        int instructions = text.indexOf("Enter explore mode.");
        int topic = text.indexOf("**Topic:** caching");
        assertTrue(instructions > 0 && topic > instructions, "preamble, then instructions, then the topic");
    }

    @Test
    void anAgentPromptHasNoPreamble() throws IOException {
        place(".claude/skills/openspec-explore/SKILL.md", "claude-skill-both.md");
        service.setCandidateToolsForTest(() -> List.of(tool("claude")));

        assertFalse(service.buildPrompt("caching").contains("Note from the OpenSpec IDE plugin"));
        assertFalse(service.build("caching", Delivery.AGENT).text().contains("Note from the OpenSpec IDE plugin"));
    }

    @Test
    void thePreambleSaysWhatTheModelCantDoAndWhereCaptureLives() {
        String preamble = ExplorePromptService.DIRECT_API_PREAMBLE;

        assertTrue(preamble.contains("cannot run commands"));
        assertTrue(preamble.contains("write anything"));
        assertTrue(preamble.contains("never claim to have run or changed anything"));
        assertTrue(preamble.contains("**Propose** action"));
    }
}
