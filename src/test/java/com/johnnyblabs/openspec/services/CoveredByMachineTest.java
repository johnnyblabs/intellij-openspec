package com.johnnyblabs.openspec.services;

import com.johnnyblabs.openspec.services.AiToolDetectionService.ToolStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** openspec init installs a detected tool for the machine only as upstream's resolveSkillScope picks the machine. */
class CoveredByMachineTest {

    private static final AiToolRegistry.AiTool CLAUDE = AiToolRegistry.byId("claude");

    private static boolean forMachine(String version, boolean ownSkills, String saved, ToolStatus status, AiToolRegistry.AiTool tool) {
        return AiToolDetectionService.installsForMachine(version, ownSkills, saved, status, tool);
    }

    @Test
    void theMachineOnlyWhereResolveSkillScopePicksIt() {
        assertTrue(forMachine("2.1.0", false, null, ToolStatus.DETECTED, CLAUDE));
        assertTrue(forMachine("2.2.0", false, "global", ToolStatus.DETECTED, CLAUDE));
        assertFalse(forMachine("2.0.0", false, null, ToolStatus.DETECTED, CLAUDE), "no machine scope before 2.1");
        assertFalse(forMachine(null, false, null, ToolStatus.DETECTED, CLAUDE), "no CLI: nothing is run");
        assertFalse(forMachine("2.2.0", true, null, ToolStatus.DETECTED, CLAUDE), "the project has its own skills");
        assertFalse(forMachine("2.2.0", false, "project", ToolStatus.DETECTED, CLAUDE), "a saved project scope");
        assertFalse(forMachine("2.2.0", false, "elsewhere", ToolStatus.DETECTED, CLAUDE), "any other saved value isn't the machine");
        assertTrue(forMachine("2.2.0", false, " ", ToolStatus.DETECTED, CLAUDE), "a blank one is none");
        assertFalse(forMachine("2.2.0", false, null, ToolStatus.CONFIGURED, CLAUDE));
        assertFalse(forMachine("2.2.0", false, null, ToolStatus.DETECTED, AiToolRegistry.byId("eve")), "no global support");
    }

    @Test
    void theSavedScopeIsTheEnvironmentsThenTheGlobalConfigs(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir)
            throws java.io.IOException {
        java.nio.file.Path config = dir.resolve("config.json");
        java.nio.file.Files.writeString(config, "{\"skillsScope\": \"project\"}");
        assertEquals("project", SavedSkillsScope.read(k -> null, config));
        assertEquals("global", SavedSkillsScope.read(k -> k.equals("OPENSPEC_SKILLS_SCOPE") ? "global" : null, config),
                "the environment comes first");
        assertNull(SavedSkillsScope.read(k -> null, dir.resolve("missing.json")));
    }
}
