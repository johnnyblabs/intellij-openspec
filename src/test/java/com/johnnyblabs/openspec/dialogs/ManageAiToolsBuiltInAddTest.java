package com.johnnyblabs.openspec.dialogs;

import com.intellij.notification.Notification;
import com.intellij.notification.Notifications;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.johnnyblabs.openspec.services.AiToolDetectionService.ToolInfo;
import com.johnnyblabs.openspec.services.AiToolDetectionService.ToolStatus;
import com.johnnyblabs.openspec.services.AiToolDetectionService.ToolType;
import com.johnnyblabs.openspec.services.AiToolRegistry;
import com.johnnyblabs.openspec.services.AiToolRegistry.BuiltInAdd;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Manage AI Tools' add without the CLI, on a real project on disk: on a 2.x project it never writes the tool list, and
 * a tool only that list can record is refused with a notice saying the CLI is needed; elsewhere it adds as before.
 */
public class ManageAiToolsBuiltInAddTest extends HeavyPlatformTestCase {

    private final List<Notification> raised = new ArrayList<>();
    private Path base;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        base = Path.of(getProject().getBasePath());
        raised.clear();
        getProject().getMessageBus().connect(getTestRootDisposable()).subscribe(Notifications.TOPIC, new Notifications() {
            @Override
            public void notify(@NotNull Notification notification) {
                raised.add(notification);
            }
        });
    }

    /** A 2.x project: Claude Code set up, its list written as the CLI writes it, its links as git checks them out without link support. */
    private void seed2x() throws Exception {
        Files.createDirectories(base.resolve(".agents/skills/openspec-explore"));
        Files.writeString(base.resolve(".agents/skills/openspec-explore/SKILL.md"), "shared\n");
        Files.writeString(base.resolve(".agents/skills/.openspec-target"), "claude own-files\n");
        Files.createDirectories(base.resolve(".claude/skills"));
        Files.writeString(base.resolve(".claude/skills/openspec-explore"), "../../.agents/skills/openspec-explore");
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(base);
    }

    private static ToolInfo row(String id) {
        return new ToolInfo(AiToolRegistry.byId(id).name(), ToolStatus.AVAILABLE, ToolType.CLI, id);
    }

    public void testCodexOnA2xProjectWritesNothingAndSaysTheCliIsNeeded() throws Exception {
        seed2x();
        String listBefore = Files.readString(base.resolve(".agents/skills/.openspec-target"));

        BuiltInAdd done = ManageAiToolsDialog.applyBuiltInAdd(getProject(), AiToolRegistry.byId("codex"), this);

        assertEquals(BuiltInAdd.NEEDS_CLI, done);
        assertEquals("the CLI's tool list is untouched", listBefore, Files.readString(base.resolve(".agents/skills/.openspec-target")));
        assertFalse(Files.exists(base.resolve(".codex")));
        assertFalse(raised.isEmpty());
        assertEquals(ManageAiToolsDialog.builtInNeedsCliNotice("Codex"), raised.get(raised.size() - 1).getContent());
        assertTrue(ManageAiToolsDialog.addTooltip(getProject(), row("codex")).startsWith("Install the OpenSpec CLI"));
    }

    /** The list deleted, the links left: still a 2.x project, and no list is created. */
    public void testA2xProjectWithoutItsListGetsNoListWritten() throws Exception {
        seed2x();
        Files.delete(base.resolve(".agents/skills/.openspec-target"));

        assertEquals(BuiltInAdd.NEEDS_CLI, ManageAiToolsDialog.applyBuiltInAdd(getProject(), AiToolRegistry.byId("codex"), this));
        assertFalse("no list is created on a 2.x project", Files.exists(base.resolve(".agents/skills/.openspec-target")));
    }

    public void testCursorOnA2xProjectGetsItsFolderAndNoList() throws Exception {
        seed2x();
        String listBefore = Files.readString(base.resolve(".agents/skills/.openspec-target"));

        assertEquals(BuiltInAdd.CREATE_DIR, ManageAiToolsDialog.applyBuiltInAdd(getProject(), AiToolRegistry.byId("cursor"), this));

        assertTrue(Files.isDirectory(base.resolve(".cursor")));
        assertEquals(listBefore, Files.readString(base.resolve(".agents/skills/.openspec-target")));
        assertTrue(ManageAiToolsDialog.addTooltip(getProject(), row("cursor")).startsWith("Create tool directory"));
    }

    public void testCodexOnAnEmptyProjectGetsItsListAsBefore() throws Exception {
        Files.createDirectories(base); // a heavy project's base directory exists only once something is written to it
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(base);

        assertEquals(BuiltInAdd.CREATE_DIR_AND_LIST,
                ManageAiToolsDialog.applyBuiltInAdd(getProject(), AiToolRegistry.byId("codex"), this));

        assertEquals("codex\n", Files.readString(base.resolve(".agents/skills/.openspec-target")));
    }

    /** The dialog's button goes through the decision, never straight to the folder and list. */
    public void testTheAddButtonUsesTheDecision() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/johnnyblabs/openspec/dialogs/ManageAiToolsDialog.java"));
        String add = source.substring(source.indexOf("private void addToolBuiltIn("), source.indexOf("static com.johnnyblabs.openspec.services.AiToolRegistry.@Nullable BuiltInAdd applyBuiltInAdd("));
        assertTrue(add.contains("applyBuiltInAdd(project, entry, this)"));
        assertFalse("the list is written in one place", add.contains(".openspec-target"));
        FileUtil.delete(base.resolve(".agents").toFile());
    }
}
