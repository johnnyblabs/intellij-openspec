package com.johnnyb.openspec.integration;

import com.johnnyb.openspec.services.AiToolDetectionService;
import com.johnnyb.openspec.settings.OpenSpecSettings;

import java.io.File;
import java.util.List;

/**
 * Integration tests for AiToolDetectionService.
 * Uses the IntelliJ test framework to verify detection with real project directories.
 */
public class AiToolDetectionIntegrationTest extends OpenSpecIntegrationTestBase {

    private static final String[] ALL_TOOL_DIRS = {
            ".claude", ".github", ".cursor", ".windsurf", ".cline", ".gemini"
    };

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // Clean any tool dirs so each test starts from a known state
        removeAllToolDirs();
    }

    @Override
    protected void tearDown() throws Exception {
        removeAllToolDirs();
        super.tearDown();
    }

    // --- Detection with real directories ---

    public void testDetect_findsClaudeCodeDir() throws Exception {
        createToolDir(".claude");

        AiToolDetectionService service = getProject().getService(AiToolDetectionService.class);
        service.detect();

        assertTrue("Should detect Claude Code", service.getDetectedTools().contains("Claude Code"));
        assertTrue("Should have detected tools", service.hasDetectedTools());
    }

    public void testDetect_findsCopilotDir() throws Exception {
        createToolDir(".github");

        AiToolDetectionService service = getProject().getService(AiToolDetectionService.class);
        service.detect();

        assertTrue("Should detect GitHub Copilot", service.getDetectedTools().contains("GitHub Copilot"));
    }

    public void testDetect_findsMultipleTools() throws Exception {
        createToolDir(".claude");
        createToolDir(".github");
        createToolDir(".cursor");

        AiToolDetectionService service = getProject().getService(AiToolDetectionService.class);
        service.detect();

        List<String> tools = service.getDetectedTools();
        assertTrue("Should detect Claude Code", tools.contains("Claude Code"));
        assertTrue("Should detect GitHub Copilot", tools.contains("GitHub Copilot"));
        assertTrue("Should detect Cursor", tools.contains("Cursor"));
        assertEquals("Should find exactly 3 tools", 3, tools.size());
    }

    public void testDetect_returnsEmptyWhenNoDirsPresent() {
        AiToolDetectionService service = getProject().getService(AiToolDetectionService.class);
        service.detect();

        assertTrue("Should have no detected tools", service.getDetectedTools().isEmpty());
        assertFalse("hasDetectedTools should be false", service.hasDetectedTools());
    }

    public void testSummary_withDetectedTools() throws Exception {
        createToolDir(".claude");
        createToolDir(".gemini");

        AiToolDetectionService service = getProject().getService(AiToolDetectionService.class);
        service.detect();

        String summary = service.getSummary();
        assertTrue("Summary should contain AI prefix", summary.startsWith("AI: "));
        assertTrue("Summary should mention Claude Code", summary.contains("Claude Code"));
        assertTrue("Summary should mention Gemini", summary.contains("Gemini"));
    }

    public void testSummary_withNoTools() {
        AiToolDetectionService service = getProject().getService(AiToolDetectionService.class);
        service.detect();

        assertEquals("No AI tools detected", service.getSummary());
    }

    public void testPrimaryToolLabel_returnsFirstDetected() throws Exception {
        createToolDir(".claude");
        createToolDir(".github");

        AiToolDetectionService service = getProject().getService(AiToolDetectionService.class);
        service.detect();

        assertEquals("Claude Code", service.getPrimaryToolLabel());
    }

    public void testPrimaryToolLabel_fallbackWhenNoTools() {
        AiToolDetectionService service = getProject().getService(AiToolDetectionService.class);
        service.detect();

        assertEquals("AI Tool", service.getPrimaryToolLabel());
    }

    // --- Preferred tool resolution ---

    public void testPreferredToolLabel_defaultsToFirstDetected() throws Exception {
        createToolDir(".claude");
        createToolDir(".github");

        OpenSpecSettings settings = OpenSpecSettings.getInstance(getProject());
        settings.setPreferredTool("");

        AiToolDetectionService service = getProject().getService(AiToolDetectionService.class);
        service.detect();

        assertEquals("Should default to first detected tool",
                "Claude Code", service.getPreferredToolLabel());
    }

    public void testPreferredToolLabel_returnsSavedPreference() throws Exception {
        createToolDir(".claude");
        createToolDir(".github");

        OpenSpecSettings settings = OpenSpecSettings.getInstance(getProject());
        settings.setPreferredTool("GitHub Copilot");

        AiToolDetectionService service = getProject().getService(AiToolDetectionService.class);
        service.detect();

        assertEquals("Should return saved preference",
                "GitHub Copilot", service.getPreferredToolLabel());
    }

    public void testPreferredToolLabel_fallsBackWhenSavedMissing() throws Exception {
        createToolDir(".claude");
        // Don't create .github — so "GitHub Copilot" won't be detected

        OpenSpecSettings settings = OpenSpecSettings.getInstance(getProject());
        settings.setPreferredTool("GitHub Copilot"); // saved but not detected

        AiToolDetectionService service = getProject().getService(AiToolDetectionService.class);
        service.detect();

        assertEquals("Should fall back to first detected",
                "Claude Code", service.getPreferredToolLabel());
    }

    public void testPreferredToolLabel_fallsBackToAiToolWhenNothingDetected() {
        OpenSpecSettings settings = OpenSpecSettings.getInstance(getProject());
        settings.setPreferredTool("GitHub Copilot"); // saved but nothing detected

        AiToolDetectionService service = getProject().getService(AiToolDetectionService.class);
        service.detect();

        assertEquals("Should fall back to AI Tool",
                "AI Tool", service.getPreferredToolLabel());
    }

    // --- Tool type indicators via settings dropdown ---

    public void testDetectedToolsHaveCorrectTypes() throws Exception {
        createToolDir(".claude");
        createToolDir(".github");
        createToolDir(".gemini");

        AiToolDetectionService service = getProject().getService(AiToolDetectionService.class);
        service.detect();

        for (String tool : service.getDetectedTools()) {
            AiToolDetectionService.ToolType type = AiToolDetectionService.getToolType(tool);
            assertNotNull("Tool type should not be null for " + tool, type);
        }

        assertEquals(AiToolDetectionService.ToolType.CLI,
                AiToolDetectionService.getToolType("Claude Code"));
        assertEquals(AiToolDetectionService.ToolType.IDE_PANEL,
                AiToolDetectionService.getToolType("GitHub Copilot"));
        assertEquals(AiToolDetectionService.ToolType.CLI,
                AiToolDetectionService.getToolType("Gemini"));
    }

    // --- Helpers ---

    private void createToolDir(String dirName) throws Exception {
        String basePath = getProject().getBasePath();
        assertNotNull("Project base path should not be null", basePath);
        File dir = new File(basePath, dirName);
        assertTrue("Should create directory: " + dirName, dir.mkdirs() || dir.isDirectory());
    }

    private void removeAllToolDirs() {
        String basePath = getProject().getBasePath();
        if (basePath == null) return;
        for (String dirName : ALL_TOOL_DIRS) {
            File dir = new File(basePath, dirName);
            if (dir.isDirectory()) {
                dir.delete();
            }
        }
    }
}
