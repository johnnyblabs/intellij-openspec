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
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Workflow handoffs name exactly what upstream tells the user to type, for every workflow, tool and delivery mode.
 * {@code workflow-invocations.json} is captured from upstream's own functions by
 * {@code scripts/capture-workflow-invocations.py}. Each row's files are seeded into a fresh project with the row's tool
 * as the only candidate; explore rows go through Explore's own lookup, the others through {@link WorkflowInvocation#find}.
 */
class WorkflowInvocationContractTest {

    private static final Path MATRIX = AiToolRegistryContractTest.FIXTURES.resolve(AiToolRegistryContractTest.TARGET)
            .resolve("workflow-invocations.json");

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

    private String pluginInvocation(JsonObject row, Path project, AiTool tool) {
        String workflow = row.get("workflow").getAsString();
        String skill = row.get("skill").getAsString();
        if (!WorkflowInvocation.EXPLORE.equals(workflow)) {
            WorkflowInvocation.Found found = WorkflowInvocation.find(project, List.of(tool), workflow, skill);
            return found == null ? null : found.invocation();
        }
        Project p = mock(Project.class);
        when(p.getBasePath()).thenReturn(project.toString());
        ExplorePromptService service = new ExplorePromptService(p);
        service.setCandidateToolsForTest(() -> List.of(tool));
        Instructions instructions = service.loadInstructions();
        return instructions.tool() == null ? null : WorkflowInvocation.of(tool.id(), workflow, skill,
                AiToolRegistry.exploreCommandPath(tool), instructions.hasCommand(), instructions.hasSkill());
    }

    @Test
    void everyWorkflowToolAndDeliveryGetsUpstreamsInvocation() throws IOException {
        List<String> wrong = new ArrayList<>();
        List<String> workflows = new ArrayList<>();
        int checked = 0;
        for (JsonObject row : rows()) {
            String workflow = row.get("workflow").getAsString();
            String tool = row.get("tool").getAsString();
            String delivery = row.get("delivery").getAsString();
            String expected = stringOrNull(row, "invocation");

            Path project = Files.createDirectory(root.resolve(workflow + "-" + tool + "-" + delivery));
            for (String path : new String[] {stringOrNull(row, "commandPath"), stringOrNull(row, "skillPath")}) {
                if (path == null) continue;
                Path file = project.resolve(path);
                Files.createDirectories(file.getParent());
                Files.writeString(file, path.endsWith(".toml") ? "prompt = \"\"\"Run.\"\"\"\n" : "Run.\n");
            }
            String actual = pluginInvocation(row, project, byUpstreamId(tool));
            if (!Objects.equals(expected, actual)) {
                wrong.add(workflow + " " + tool + "/" + delivery + ": upstream " + expected + ", plugin " + actual);
            }
            if (!workflows.contains(workflow)) workflows.add(workflow);
            checked++;
        }
        assertEquals(List.of("explore", "update"), workflows);
        assertEquals(294, checked, "the 1.15.0 matrix covers 2 workflows x 49 tools x 3 deliveries");
        assertTrue(wrong.isEmpty(), "invocations that differ from upstream's:\n" + String.join("\n", wrong));
    }

    @Test
    void theMatrixPathsAreTheRegistrysPaths() throws IOException {
        List<String> drift = new ArrayList<>();
        for (JsonObject row : rows()) {
            AiTool tool = byUpstreamId(row.get("tool").getAsString());
            String workflow = row.get("workflow").getAsString();
            String skill = row.get("skill").getAsString();
            String commandPath = stringOrNull(row, "commandPath");
            String skillPath = stringOrNull(row, "skillPath");
            if (commandPath != null && !commandPath.equals(AiToolRegistry.commandPath(tool, workflow))) {
                drift.add(workflow + " " + tool.id() + " command: upstream " + commandPath + ", registry "
                        + AiToolRegistry.commandPath(tool, workflow));
            }
            if (skillPath != null && !skillPath.equals(AiToolRegistry.skillPath(tool, skill))) {
                drift.add(workflow + " " + tool.id() + " skill: upstream " + skillPath + ", registry "
                        + AiToolRegistry.skillPath(tool, skill));
            }
        }
        assertTrue(drift.isEmpty(), String.join("\n", drift));
    }

    /** The update rows' paths are exactly the update files a real {@code openspec init} wrote, per tool and delivery. */
    @Test
    void theUpdatePathsAreWhatInitWrites() throws IOException {
        Path listings = AiToolRegistryContractTest.FIXTURES.resolve(AiToolRegistryContractTest.TARGET).resolve("update");
        List<String> drift = new ArrayList<>();
        int compared = 0;
        for (String delivery : List.of("both", "skills", "commands")) {
            java.util.Map<String, java.util.Set<String>> written = new java.util.HashMap<>();
            for (String line : Files.readAllLines(listings.resolve("listing-" + delivery + ".txt"))) {
                if (line.isBlank()) continue;
                String[] parts = line.split(" ", 3);
                assertEquals("P", parts[1], "update files are written in the project: " + line);
                written.computeIfAbsent(parts[0], k -> new java.util.TreeSet<>()).add(parts[2]);
            }
            for (JsonObject row : rows()) {
                if (!"update".equals(row.get("workflow").getAsString()) || !delivery.equals(row.get("delivery").getAsString())) {
                    continue;
                }
                java.util.Set<String> matrix = new java.util.TreeSet<>();
                for (String key : new String[] {"commandPath", "skillPath"}) {
                    if (stringOrNull(row, key) != null) matrix.add(stringOrNull(row, key));
                }
                java.util.Set<String> init = written.getOrDefault(row.get("tool").getAsString(), java.util.Set.of());
                if (!matrix.equals(init)) drift.add(row.get("tool").getAsString() + "/" + delivery + ": matrix " + matrix + ", init " + init);
                compared++;
            }
        }
        assertEquals(147, compared);
        assertTrue(drift.isEmpty(), "update paths that differ from what init wrote:\n" + String.join("\n", drift));
    }

    @Test
    void theHandoffAddsTheArgumentsOrNamesTheSkillInWords() {
        String explore = WorkflowInvocation.EXPLORE_SKILL;
        String update = WorkflowInvocation.UPDATE_SKILL;
        assertEquals("/opsx:explore why caching", WorkflowInvocation.handoff("/opsx:explore", explore, "why caching"));
        assertEquals("/opsx:explore", WorkflowInvocation.handoff("/opsx:explore", explore, "  "));
        assertEquals("Use the openspec-explore skill: why caching",
                WorkflowInvocation.handoff(WorkflowInvocation.plainWords(explore), explore, "why caching"));
        assertEquals("Use the openspec-explore skill.",
                WorkflowInvocation.handoff(WorkflowInvocation.plainWords(explore), explore, null));
        assertEquals("Use the openspec-update-change skill: add-auth drop export",
                WorkflowInvocation.handoff(WorkflowInvocation.plainWords(update), update, "add-auth drop export"));
    }
}
