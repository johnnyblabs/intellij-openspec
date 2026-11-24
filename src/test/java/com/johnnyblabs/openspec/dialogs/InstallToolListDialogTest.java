package com.johnnyblabs.openspec.dialogs;

import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.johnnyblabs.openspec.services.AiToolRegistry;
import com.johnnyblabs.openspec.services.AiToolRegistry.AiTool;
import com.johnnyblabs.openspec.services.InstallToolTicks;

import javax.swing.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** The install list: Install needs a tick, its command names the ticked tools into the project, Cancel runs nothing. */
public class InstallToolListDialogTest extends BasePlatformTestCase {

    private InstallToolListDialog open(List<InstallToolTicks.Row> rows) {
        InstallToolListDialog dialog = new InstallToolListDialog(new JPanel(), rows);
        Disposer.register(getTestRootDisposable(), dialog.getDisposable());
        return dialog;
    }

    public void testInstallNeedsATickAndNamesTheTickedToolsIntoTheProject() throws Exception {
        AiTool claude = AiToolRegistry.byId("claude");
        AiTool cursor = AiToolRegistry.byId("cursor");
        AiTool codex = AiToolRegistry.byId("codex");
        InstallToolListDialog dialog = open(List.of(new InstallToolTicks.Row(cursor, true),
                new InstallToolTicks.Row(claude, true), new InstallToolTicks.Row(codex, false)));

        assertTrue(dialog.okEnabledForTest());
        assertEquals(List.of(cursor, claude), dialog.ticked());
        List<String> argv = ManageAiToolsDialog.installArgs(dialog.ticked()).resolve("2.1.1").argv();
        assertEquals(List.of("init", "--tools", InstallToolTicks.toolsArg(List.of(claude, cursor)), "--scope", "project"), argv);

        dialog.setTickedForTest(cursor, false);
        dialog.setTickedForTest(claude, false);
        assertFalse("at least one tool, as upstream's picker requires", dialog.okEnabledForTest());
        dialog.setTickedForTest(codex, true);
        assertTrue(dialog.okEnabledForTest());
    }

    /** The install always names its tools: without --tools, a non-interactive 2.1 init can't choose (captured). */
    public void testTheInstallAlwaysPassesToolsBecauseTheCliCantChooseWithoutThem() throws Exception {
        for (String v : new String[] {"2.1.0", "2.1.1"}) {
            Path errors = com.johnnyblabs.openspec.util.FixtureVersions.CLI.resolve(v).resolve("errors");
            assertEquals(v, "1", Files.readString(errors.resolve("init-scope-project-no-tools.exit")).strip());
            assertTrue(v, Files.readString(errors.resolve("init-scope-project-no-tools.stderr"))
                    .contains("No tools detected and no --tools flag provided"));
        }
        List<String> argv = ManageAiToolsDialog.installArgs(List.of(AiToolRegistry.byId("claude"))).requested();
        assertEquals("--tools", argv.get(1));
        assertEquals("claude", argv.get(2));
    }

    public void testNothingTickedOpensWithInstallDisabled() {
        assertFalse(open(List.of(new InstallToolTicks.Row(AiToolRegistry.byId("claude"), false))).okEnabledForTest());
    }

    /** Cancel returns before the command is built; the install runs only after the list is confirmed. */
    public void testCancelRunsNothing() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/johnnyblabs/openspec/dialogs/ManageAiToolsDialog.java"));
        int start = source.indexOf("private void installIntoProject()");
        String body = source.substring(start, source.indexOf("static com.johnnyblabs.openspec.util.CliArgs installArgs(", start));
        int cancel = body.indexOf("if (!list.showAndGet()) return;");
        assertTrue(cancel > 0);
        assertTrue(body.indexOf("runCliAction(installArgs(list.ticked())") > cancel);
        assertEquals("one run, after confirming", 1, body.split("runCliAction\\(", -1).length - 1);
        assertTrue("the list opens over Manage AI Tools", body.contains("new InstallToolListDialog(parent, rows)"));
    }
}
