package com.johnnyblabs.openspec.toolwindow;

import com.johnnyblabs.openspec.services.AiToolRegistry;
import com.johnnyblabs.openspec.services.AiToolRegistry.AiTool;
import com.johnnyblabs.openspec.services.WorkflowInvocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Clipboard and editor Apply send the short apply handoff when the selected tool has the apply skill or command
 * installed, and the full prompt otherwise. The handoff must name the selected tool's own invocation.
 */
class ApplyHandoffTest {

    @TempDir
    Path base;

    @Test
    void noHandoffWhenTheSelectedToolHasNoApplySkillOrCommand() {
        assertNull(WorkflowActionPanel.applyHandoff(base.toString(), "Claude Code", "my-change"));
    }

    @Test
    void theHandoffNamesTheSelectedToolsApplySkill() throws IOException {
        AiTool claude = AiToolRegistry.byName("Claude Code");
        assertNotNull(claude);
        String skillPath = AiToolRegistry.skillPath(claude, WorkflowInvocation.APPLY_SKILL);
        assertNotNull(skillPath, "Claude Code has an apply skill path");
        Path skill = base.resolve(skillPath);
        Files.createDirectories(skill.getParent());
        Files.writeString(skill, "---\nname: openspec-apply-change\n---\n");

        String handoff = WorkflowActionPanel.applyHandoff(base.toString(), "Claude Code", "my-change");

        assertNotNull(handoff, "the selected tool has the apply skill");
        assertTrue(handoff.contains(WorkflowInvocation.APPLY_SKILL), handoff);
        assertTrue(handoff.contains("my-change"), handoff);
    }

    @Test
    void noHandoffWithoutAToolSelected() {
        assertNull(WorkflowActionPanel.applyHandoff(base.toString(), "", "my-change"));
    }
}
