package com.johnnyblabs.openspec.services;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.openapi.project.Project;
import com.johnnyblabs.openspec.services.AiToolRegistry.AiTool;
import com.johnnyblabs.openspec.services.ExplorePromptService.Instructions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The short Explore handoff names exactly what upstream tells the user to type, for every tool and delivery mode.
 * {@code invocations.json} is captured from upstream's own functions by {@code scripts/capture-explore-invocations.py}.
 * Each row's explore files are seeded into a fresh project, the tool is made the candidate, and the plugin's lookup
 * plus {@link ExploreInvocation} must give the row's invocation.
 */
class ExploreInvocationContractTest {

    private static final Path MATRIX = AiToolRegistryContractTest.FIXTURES.resolve(AiToolRegistryContractTest.TARGET)
            .resolve("explore/invocations.json");

    @TempDir Path root;

    private static List<JsonObject> rows() throws IOException {
        List<JsonObject> rows = new ArrayList<>();
        for (JsonElement row : JsonParser.parseString(Files.readString(MATRIX)).getAsJsonObject().getAsJsonArray("rows")) {
            rows.add(row.getAsJsonObject());
        }
        return rows;
    }

    private static String stringOrNull(JsonObject row, String key) {
        return row.get(key) == null || row.get(key).isJsonNull() ? null : row.get(key).getAsString();
    }

    /** The registry tool for upstream's tool value: the registry's id (devin), not its CLI alias (windsurf). */
    private static AiTool byUpstreamId(String id) {
        AiTool tool = AiToolRegistry.byId(id);
        assertNotNull(tool, "no registry tool with id " + id);
        return tool;
    }

    @Test
    void everyToolAndDeliveryGetsUpstreamsInvocation() throws IOException {
        List<String> wrong = new ArrayList<>();
        int checked = 0;
        for (JsonObject row : rows()) {
            String tool = row.get("tool").getAsString();
            String delivery = row.get("delivery").getAsString();
            String commandPath = stringOrNull(row, "commandPath");
            String skillPath = stringOrNull(row, "skillPath");
            String expected = stringOrNull(row, "invocation");

            Path project = Files.createDirectory(root.resolve(tool + "-" + delivery));
            for (String path : new String[] {commandPath, skillPath}) {
                if (path == null) continue;
                Path file = project.resolve(path);
                Files.createDirectories(file.getParent());
                Files.writeString(file, path.endsWith(".toml") ? "prompt = \"\"\"Explore.\"\"\"\n" : "Explore.\n");
            }
            Project p = mock(Project.class);
            when(p.getBasePath()).thenReturn(project.toString());
            ExplorePromptService service = new ExplorePromptService(p);
            AiTool aiTool = byUpstreamId(tool);
            service.setCandidateToolsForTest(() -> List.of(aiTool));

            Instructions instructions = service.loadInstructions();
            String actual = instructions.tool() == null ? null : ExploreInvocation.of(aiTool.id(),
                    AiToolRegistry.exploreCommandPath(aiTool), instructions.hasCommand(), instructions.hasSkill());

            if (!java.util.Objects.equals(expected, actual)) {
                wrong.add(tool + "/" + delivery + ": upstream " + expected + ", plugin " + actual);
            }
            checked++;
        }
        assertEquals(147, checked, "the 1.15.0 matrix covers 49 tools x 3 deliveries");
        assertTrue(wrong.isEmpty(), "invocations that differ from upstream's:\n" + String.join("\n", wrong));
    }

    @Test
    void theMatrixPathsAreTheRegistrysExplorePaths() throws IOException {
        List<String> drift = new ArrayList<>();
        for (JsonObject row : rows()) {
            AiTool tool = byUpstreamId(row.get("tool").getAsString());
            String commandPath = stringOrNull(row, "commandPath");
            String skillPath = stringOrNull(row, "skillPath");
            if (commandPath != null && !commandPath.equals(AiToolRegistry.exploreCommandPath(tool))) {
                drift.add(tool.id() + " command: upstream " + commandPath + ", registry " + AiToolRegistry.exploreCommandPath(tool));
            }
            if (skillPath != null && !skillPath.equals(AiToolRegistry.exploreSkillPath(tool))) {
                drift.add(tool.id() + " skill: upstream " + skillPath + ", registry " + AiToolRegistry.exploreSkillPath(tool));
            }
        }
        assertTrue(drift.isEmpty(), String.join("\n", drift));
    }

    @Test
    void theHandoffAddsTheTopicOrNamesTheSkillInWords() {
        assertEquals("/opsx:explore why caching", ExploreInvocation.handoff("/opsx:explore", "why caching"));
        assertEquals("/opsx:explore", ExploreInvocation.handoff("/opsx:explore", "  "));
        assertEquals("Use the openspec-explore skill: why caching",
                ExploreInvocation.handoff(ExploreInvocation.PLAIN_WORDS, "why caching"));
        assertEquals("Use the openspec-explore skill.", ExploreInvocation.handoff(ExploreInvocation.PLAIN_WORDS, null));
    }
}
