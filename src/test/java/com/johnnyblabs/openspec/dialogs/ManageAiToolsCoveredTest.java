package com.johnnyblabs.openspec.dialogs;

import com.johnnyblabs.openspec.services.AiToolDetectionService.ToolInfo;
import com.johnnyblabs.openspec.services.AiToolDetectionService.ToolStatus;
import com.johnnyblabs.openspec.services.AiToolDetectionService.ToolType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** A tool that openspec init installs for this machine says so, and Configure says what it will do. */
class ManageAiToolsCoveredTest {

    private static ToolInfo detected(boolean forMachine) {
        return new ToolInfo("Claude Code", ToolStatus.DETECTED, ToolType.CLI, "claude", false, forMachine);
    }

    @Test
    void aMachineToolSaysWhatConfigureDoes() {
        assertEquals("skills for this machine", ManageAiToolsDialog.rowNote(detected(true)));
        assertTrue(ManageAiToolsDialog.configureTooltip(detected(true)).contains("for every project on this machine"));
        assertTrue(ManageAiToolsDialog.COVERED_CAPTION.startsWith("Not set up for this project."));
        assertTrue(ManageAiToolsDialog.COVERED_CAPTION.contains("Install into this project"), "the way to project skills");
    }

    /** The button runs what the row says: plain init, so the CLI installs for the machine, whatever the scope line shows. */
    @Test
    void aMachineToolsConfigureRunsPlainInit() {
        var args = ManageAiToolsDialog.configureArgs(null, detected(true));
        assertEquals(com.johnnyblabs.openspec.util.CliCommands.initKeepingScope("claude").toString(), args.toString());
        assertFalse(args.toString().contains("--scope"), args.toString());
    }

    @Test
    void aProjectToolReadsAsBefore() {
        assertNull(ManageAiToolsDialog.rowNote(detected(false)));
        assertEquals("Generate OpenSpec skills and commands for Claude Code", ManageAiToolsDialog.configureTooltip(detected(false)));
    }
}
