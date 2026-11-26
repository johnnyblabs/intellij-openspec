package com.johnnyblabs.openspec.uismoke

import com.intellij.driver.client.Remote
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.Project
import com.intellij.driver.sdk.getToolWindow
import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.isCodeAnalysisRunning
import com.intellij.driver.sdk.openFile
import com.intellij.driver.sdk.singleProject
import com.intellij.ide.starter.driver.execute
import com.intellij.tools.ide.performanceTesting.commands.CommandChain
import com.intellij.tools.ide.performanceTesting.commands.takeScreenshot
import com.intellij.driver.sdk.ui.components.ideFrame
import com.intellij.driver.sdk.ui.components.tree
import com.intellij.driver.sdk.ui.ui
import com.intellij.driver.sdk.waitForIndicators
import com.intellij.ide.starter.driver.engine.runIdeWithDriver
import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.junit5.hyphenateWithClass
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.plugins.PluginConfigurator
import com.intellij.ide.starter.project.LocalProjectInfo
import com.intellij.ide.starter.runner.CurrentTestMethod
import com.intellij.ide.starter.runner.Starter
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import javax.imageio.ImageIO
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Marketplace screenshot tour — a DOCS TOOL, not a smoke journey. Boots one sandbox IDE
 * against the seeded demo project (plus CLI-seeded store/workset/schema state under an
 * isolated data dir) and emits `docs/screenshots/<shot>.png` at 1280x800 for the
 * Marketplace listing. No assertions gate a release; the `uiSmoke` task excludes this
 * class and the `screenshotTour` task runs only it.
 *
 * The shot list mirrors scripts/capture-screenshots.sh (the single source of truth for
 * shot names); docs/marketplace-page.md gives the upload order. Where the tour differs from a hand capture:
 *  - 08: captures the inspection highlighting + Console results WITHOUT the Alt+Enter
 *    intention popup (driving the popup remotely is deferred).
 *  - 11: captures the update preview with its OpenSpec 2 migration banner, then cancels it,
 *    so nothing is migrated.
 *  - 06, 07, 11: the plugin's own dialogs are built and filled remotely and shown non-modally,
 *    and their capture is drawn over the frame where the dialog sits.
 *
 * Requirements: a GUI session (AWT Robot screen capture — grant Screen Recording to the
 * launching terminal on macOS if captures come out black) and an OpenSpec CLI 1.6+ on
 * PATH. Output needs a human flip-through before Marketplace upload.
 */
class MarketplaceScreenshotTour {

    @Remote("com.intellij.openapi.wm.ToolWindowManager")
    interface ToolWindowManagerRef {
        fun getInstance(project: Project): ToolWindowManagerRef
        fun getToolWindow(id: String): RichToolWindowRef?
    }

    @Remote("com.intellij.openapi.wm.ToolWindow")
    interface RichToolWindowRef {
        fun getContentManager(): ContentManagerRef
        fun hide()
    }

    @Remote("com.intellij.openapi.wm.ex.ToolWindowEx")
    interface ToolWindowExRef {
        fun stretchWidth(value: Int)
    }

    @Remote("com.intellij.openapi.ui.Splitter")
    interface SplitterRef {
        fun setProportion(proportion: Float)
    }

    @Remote("com.intellij.openapi.wm.ex.ToolWindowManagerEx")
    interface ToolWindowManagerExRef {
        fun getInstanceEx(project: Project): ToolWindowManagerExRef
        fun setMaximized(window: RichToolWindowRef, maximized: Boolean)
    }

    @Remote("com.intellij.ui.content.ContentManager")
    interface ContentManagerRef {
        fun findContent(displayName: String): ContentRef?
        fun setSelectedContent(content: ContentRef)
    }

    @Remote("com.intellij.ui.content.Content")
    interface ContentRef

    @Remote("java.awt.Frame")
    interface FrameRef {
        fun setBounds(x: Int, y: Int, width: Int, height: Int)
        fun toFront()
    }

    @Remote("com.intellij.notification.ActionCenter")
    interface ActionCenterRef {
        fun getNotifications(project: Project?): List<NotificationRef>
    }

    @Remote("com.intellij.notification.Notification")
    interface NotificationRef {
        fun getContent(): String
        fun expire()
    }

    @Remote("java.awt.Window")
    interface WindowRef {
        fun getX(): Int
        fun getY(): Int
        fun getWidth(): Int
        fun getHeight(): Int
    }

    // Clicks don't land reliably in this environment, so shots 06 and 07 reach the plugin's dialogs through events and
    // listeners instead: a popup-trigger mouse event on a chip, and the menu item's own listener fired from a Swing timer
    // (a modal dialog opened inside a driver call would block that call until it closed).
    @Remote("java.awt.Component")
    interface ComponentRef {
        fun getParent(): ComponentRef
        fun dispatchEvent(event: MouseEventRef)
    }

    @Remote("java.awt.event.MouseEvent")
    interface MouseEventRef

    @Remote("javax.swing.AbstractButton")
    interface AbstractButtonRef {
        fun getActionListeners(): Array<ActionListenerRef>
        fun doClick()
    }

    @Remote("java.awt.event.ActionListener")
    interface ActionListenerRef

    @Remote("javax.swing.Timer")
    interface TimerRef {
        fun setRepeats(repeats: Boolean)
        fun start()
    }

    @Remote("javax.swing.MenuSelectionManager")
    interface MenuSelectionManagerRef {
        fun defaultManager(): MenuSelectionManagerRef
        fun clearSelectedPath()
    }

    @Remote("javax.swing.text.JTextComponent")
    interface TextComponentRef {
        fun setText(text: String)
    }

    @Remote("com.johnnyblabs.openspec.actions.ExploreContextAction", plugin = "com.johnnyblabs.openspec")
    interface ExploreContextActionRef {
        fun runExplore(project: Project, prefillTopic: String)
    }

    private val outputDir: Path = Path.of(System.getProperty("screenshot.output.dir"))

    private fun waitUntil(what: String, timeout: Duration = 90.seconds, probe: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeout.inWholeMilliseconds
        while (System.currentTimeMillis() < deadline) {
            runCatching { if (probe()) return }
            Thread.sleep(2_000)
        }
        throw AssertionError("Timed out waiting for: $what")
    }

    private fun hostCliVersion(): String? = runCatching {
        ProcessBuilder("openspec", "--version").redirectErrorStream(true).start()
            .inputStream.bufferedReader().readText().trim().lines().lastOrNull()
    }.getOrNull()

    /** The isolated home, config and data folders the host CLI and the tour IDE share, so the machine's own are untouched. */
    private fun isolatedEnv(home: Path, xdg: Path) = mapOf(
        "HOME" to home.toString(),
        "XDG_CONFIG_HOME" to home.resolve(".config").toString(),
        "XDG_DATA_HOME" to xdg.toString(),
        "XDG_STATE_HOME" to home.resolve(".local/state").toString(),
        "OPENSPEC_TELEMETRY" to "0",
    )

    /** Runs the host `openspec` CLI with the isolated folders; returns combined output. */
    private fun hostCli(env: Map<String, String>, workDir: Path, vararg args: String): String {
        val proc = ProcessBuilder("openspec", *args)
            .directory(workDir.toFile())
            .redirectErrorStream(true)
            .apply { environment().putAll(env) }
            .start()
        proc.outputStream.close()
        return proc.inputStream.bufferedReader().readText().also { proc.waitFor() }
    }

    private fun git(workDir: Path, vararg args: String) {
        ProcessBuilder("git", *args)
            .directory(workDir.toFile())
            .redirectErrorStream(true)
            .start()
            .apply { inputStream.bufferedReader().readText(); waitFor() }
    }

    private fun freshDemoProject(): Path {
        val fixture = Path.of(System.getProperty("demo.project.path"))
        check(Files.isDirectory(fixture)) { "demo fixture missing at $fixture" }
        val target = Files.createDirectories(Files.createTempDirectory("openspec-shot-tour-").resolve("openspec-demo"))
        Files.walk(fixture).use { paths ->
            paths.forEach { source ->
                val dest = target.resolve(fixture.relativize(source).toString())
                if (Files.isDirectory(source)) Files.createDirectories(dest)
                else Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING)
            }
        }
        return target
    }

    private fun newContext(projectPath: Path) = Starter.newContext(
        CurrentTestMethod.hyphenateWithClass(),
        TestCase(IdeProductProvider.IC, LocalProjectInfo(projectPath)).withVersion("2024.2")
    ).apply {
        PluginConfigurator(this)
            .installPluginFromPath(Path.of(System.getProperty("path.to.build.plugin")))
    }

    @Test
    fun captureMarketplaceShots() {
        val cliVersion = hostCliVersion()
        org.junit.jupiter.api.Assumptions.assumeTrue(
            cliVersion != null && Regex("""^(\d+)\.(\d+)""").find(cliVersion)?.destructured
                ?.let { (maj, min) -> maj.toInt() > 2 || (maj.toInt() == 2 && min.toInt() >= 1) } == true
        ) { "screenshot tour needs OpenSpec CLI 2.1+ on the host (found: $cliVersion)" }

        // ---- Pre-boot seeding: everything the shots need, no in-IDE prep -------------
        val projectPath = freshDemoProject()
        val base = Files.createTempDirectory("openspec-shot-state-")
        val xdg = Files.createDirectories(base.resolve("xdg-data"))
        val env = isolatedEnv(Files.createDirectories(base.resolve("home")), xdg)
        // The profile's workflows: core plus update, so Revise Change (shot 07) has the agent's update skill to hand off.
        hostCli(env, base, "config", "set", "profile", "custom")
        hostCli(env, base, "config", "set", "workflows", """["propose","explore","apply","update","sync","archive"]""")

        // 1.6 register needs: a config.yaml, a git history, and --yes on a never-a-store root.
        Files.writeString(projectPath.resolve("openspec/config.yaml"), "schema: spec-driven\n")
        // Give the demo change a spec-level delta so shot 02 (consolidated deltas view) has an
        // ADDED requirement to badge. The `greeting` main spec already exists in the demo project.
        val demoDelta = projectPath.resolve("openspec/changes/demo-add-farewell/specs/greeting/spec.md")
        Files.createDirectories(demoDelta.parent)
        Files.writeString(demoDelta,
            """
            ## ADDED Requirements

            ### Requirement: Farewell message
            The system SHALL bid the user farewell when the session ends.

            #### Scenario: Session ends
            - **WHEN** the user ends the session
            - **THEN** a farewell message is shown
            """.trimIndent() + "\n")
        // A deliberate failure so shot 09 (grouped validation console) shows a FAILED run with both
        // severities: a main spec whose requirement has no scenario (a CLI ERROR) and lacks SHALL/MUST
        // (a CLI WARNING since OpenSpec 1.8, so on its own it no longer fails the project).
        Files.createDirectories(projectPath.resolve("openspec/specs/formatting-demo"))
        Files.writeString(
            projectPath.resolve("openspec/specs/formatting-demo/spec.md"),
            "# Formatting Demo\n\n## Purpose\nExercises the grouped, colored validation console report end to end.\n\n" +
                "## Requirements\n\n### Requirement: Records are kept\nRecords are kept somewhere safe.\n",
        )
        val removedDelta = projectPath.resolve("openspec/changes/demo-add-farewell/specs/legacy-widget/spec.md")
        Files.createDirectories(removedDelta.parent)
        Files.writeString(removedDelta,
            """
            ## REMOVED Requirements

            ### Requirement: Legacy widget
            The legacy widget is retired.
            """.trimIndent() + "\n")
        // Claude Code set up by the host CLI 2.x, as a user of this plugin gets it: skills in .agents/skills, linked
        // from .claude/skills. Explore (shot 04) and Revise Change (shot 07) hand off /openspec-explore and
        // /openspec-update-change. It is set up in a scratch project and copied in, because init would also move the
        // demo's 1.x Junie files to 2.x, and those are what the update preview (shot 11) shows migrating.
        val claudeSetup = Files.createDirectories(base.resolve("claude-setup"))
        val initOutput = hostCli(env, claudeSetup, "init", "--tools", "claude", "--scope", "project", ".")
        check(Files.isDirectory(claudeSetup.resolve(".agents/skills/openspec-update-change"))) {
            "init didn't set up Claude's skills: $initOutput"
        }
        // cp -R keeps the .claude/skills links as links.
        ProcessBuilder("cp", "-R", claudeSetup.resolve(".agents").toString(), claudeSetup.resolve(".claude").toString(),
            projectPath.toString()).redirectErrorStream(true).start().apply { inputStream.readAllBytes(); check(waitFor() == 0) }
        check(Files.isSymbolicLink(projectPath.resolve(".claude/skills/openspec-explore"))) { "Claude's 2.x skills not set up" }
        // Ignoring .idea keeps the IDE's "project settings can be added to Git" balloon out of the shots.
        Files.writeString(projectPath.resolve(".gitignore"), ".idea/\n")
        git(projectPath, "init", "-q")
        git(projectPath, "-c", "user.email=demo@example.com", "-c", "user.name=Demo", "add", "-A")
        git(projectPath, "-c", "user.email=demo@example.com", "-c", "user.name=Demo",
            "commit", "-qm", "seed")
        val platformStore = base.resolve("demo-platform-store")
        hostCli(env, base, "store", "setup", "demo-platform", "--path", platformStore.toString())
        hostCli(env, projectPath, "store", "register", projectPath.toString(), "--yes")
        hostCli(env, base, "workset", "create", "demo-workset",
            "--member", projectPath.toString(), "--member", platformStore.toString())

        Files.createDirectories(outputDir)

        val context = newContext(projectPath).apply {
            applyVMOptionsPatch { env.forEach { (k, v) -> withEnv(k, v) } }
        }
        context.runIdeWithDriver().useDriverAndCloseIde {
            waitForIndicators(5.minutes)
            val project = singleProject()

            // Fixed frame geometry: every capture reads this exact screen region.
            ideFrame {
                val frame = cast(component, FrameRef::class)
                withContext(OnDispatcher.EDT) {
                    frame.setBounds(50, 50, 1280, 800)
                    frame.toFront()
                }
            }
            // Capture INSIDE the IDE process (performance-plugin TakeScreenshotCommand):
            // the IDE paints its own frame, so no macOS Screen Recording permission is
            // needed (AWT Robot in the test process silently returns bare wallpaper
            // without it). The command treats its argument as a name under the run's
            // log/screenshots dir and writes frame0.png — the IDE frame alone, at
            // native (retina) resolution. Collect it and downscale to exactly 1280x800.
            val screenshotsRoot = outputDir.parent.parent.resolve("out/perf-startup/tests")
            // The command writes each open dialog to its own dialogN.png beside frame0.png. With `dialog` (its window
            // bounds on screen), the capture of that dialog is drawn over the frame where the dialog sits.
            fun snap(name: String, dialog: java.awt.Rectangle? = null) {
                Thread.sleep(3_000) // let paint/animations settle
                execute(CommandChain().takeScreenshot("tour/$name"))
                lateinit var raw: Path
                waitUntil("raw capture for $name", timeout = 30.seconds) {
                    Files.walk(screenshotsRoot).use { s ->
                        s.filter { it.endsWith(Path.of("tour", name, "frame0.png")) }
                            .findFirst().orElse(null)
                    }?.also { raw = it } != null
                }
                val frame = ImageIO.read(raw.toFile())
                if (dialog != null) {
                    val scale = frame.width / 1280.0
                    // The capture closest in width to the dialog; a stray, invisible window can be captured too.
                    val image = Files.list(raw.parent).use { s ->
                        s.filter { it.fileName.toString().matches(Regex("""dialog\d+\.png""")) }.toList()
                    }.map { ImageIO.read(it.toFile()) }
                        .minByOrNull { kotlin.math.abs(it.width - dialog.width * scale) }
                        ?: throw AssertionError("no dialog capture for $name")
                    // The capture may leave out the native title bar; it is the window's content, at its bottom.
                    val x = ((dialog.x - 50) * scale).toInt()
                    val y = ((dialog.y - 50 + dialog.height) * scale).toInt() - image.height
                    frame.createGraphics().apply {
                        // A soft shadow so the dialog reads as floating over the IDE.
                        color = java.awt.Color(0, 0, 0, 60)
                        fillRoundRect(x + 4, y + 8, image.width, image.height, 16, 16)
                        drawImage(image, x, y, null)
                        dispose()
                    }
                }
                val out = java.awt.image.BufferedImage(1280, 800, java.awt.image.BufferedImage.TYPE_INT_RGB)
                out.createGraphics().apply {
                    setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                        java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC)
                    drawImage(frame, 0, 0, 1280, 800, null)
                    dispose()
                }
                ImageIO.write(out, "png", outputDir.resolve("$name.png").toFile())
            }

            fun selectToolWindowContent(displayName: String) {
                waitUntil("'$displayName' content selected", timeout = 3.minutes) {
                    withContext(OnDispatcher.EDT) {
                        val manager = utility(ToolWindowManagerRef::class).getInstance(project)
                        val toolWindow = manager.getToolWindow("OpenSpec")
                        val content = toolWindow?.getContentManager()?.findContent(displayName)
                        if (content != null) {
                            toolWindow.getContentManager().setSelectedContent(content)
                            true
                        } else false
                    }
                }
            }

            fun setOpenSpecMaximized(maximized: Boolean) {
                runCatching {
                    withContext(OnDispatcher.EDT) {
                        val toolWindow = utility(ToolWindowManagerRef::class).getInstance(project)
                            .getToolWindow("OpenSpec")
                        if (toolWindow != null) {
                            utility(ToolWindowManagerExRef::class).getInstanceEx(project)
                                .setMaximized(toolWindow, maximized)
                        }
                    }
                }
            }

            withContext(OnDispatcher.EDT) { getToolWindow("OpenSpec").show() }
            // The Project tree isn't the subject of any shot — hide it so the editor + OpenSpec
            // tool window get the width back (this is what un-cramps the editor's markdown preview).
            withContext(OnDispatcher.EDT) {
                runCatching {
                    utility(ToolWindowManagerRef::class).getInstance(project)
                        .getToolWindow("Project")?.hide()
                }
            }
            ideFrame { waitUntil("Browse tree renders") { hasText("Changes") } }

            fun expireNotifications() {
                withContext(OnDispatcher.EDT) {
                    utility(ActionCenterRef::class).getNotifications(project).forEach { it.expire() }
                }
            }
            // The demo's 1.x Junie files raise the plugin's "OpenSpec 2" migration notice at startup. Shot 11 tells that
            // story, so clear the notice once it has shown, rather than have it cover the chips in every shot.
            runCatching {
                waitUntil("the OpenSpec 2 notice", timeout = 1.minutes) {
                    utility(ActionCenterRef::class).getNotifications(project).any { it.getContent().contains("OpenSpec 2") }
                }
            }
            expireNotifications()

            // Maximize the OpenSpec tool window for the headline shot so the master/detail (tree +
            // rendered preview) fills the frame instead of being crammed into the side dock.
            // Best-effort: if the platform API differs, fall back to the default docked layout.
            setOpenSpecMaximized(true)

            // (The old spec-preview shot is retired: the in-panel Specs tree + plain-spec preview pane were removed in the
            // tool-window restructure — specs are now browsed in the Project View and opened in the
            // editor, shown by the hero shot 01 (spec in the editor). Content search re-homed to the
            // Search Everywhere contributor is exercised headfully by the uiSmoke gate, not the tour.)

            // 02 — consolidated change deltas: select the change NODE (its path is the change dir,
            // not a .md file), which renders the CLI-sourced deltas grouped by capability with the
            // operation badges. The pane's accessible name flips to the badged change-deltas marker
            // only after a successful CLI→parse→render with an op badge present.
            ideFrame {
                val browseTree = tree("//div[@class='Tree']")
                browseTree.expandPath("OpenSpec", "Changes", fullMatch = false)
                browseTree.clickPath("OpenSpec", "Changes", "demo-add-farewell", fullMatch = false)
                waitUntil("preview pane renders the change's consolidated deltas") {
                    x { byAccessibleName("OpenSpec preview change deltas badged") }.present()
                }
            }
            snap("02-change-deltas")

            // 03 — tree status badges: expand the change to reveal its artifact nodes, each carrying
            // a corner status badge on the icon (done / ready / blocked / not-created) instead of a
            // label glyph, plus the change node's apply-ready done badge and X/Y task-count suffix.
            // The visual IS the badged tree, so the shot just needs the artifacts expanded.
            ideFrame {
                val browseTree = tree("//div[@class='Tree']")
                // Shot 02 left the change node selected but COLLAPSED. Force it open (retry the async
                // populate race) and verify via the tree PATH — the old hasText("proposal") guard
                // false-passed on the bottom pipeline panel's "proposal" chip, snapping the collapsed
                // tree (a visual duplicate of 02). Expanding to the "specs" child forces the change
                // node open so its badged artifact nodes are actually visible.
                waitUntil("the change node expands to reveal its badged artifact nodes") {
                    browseTree.expandPath("OpenSpec", "Changes", "demo-add-farewell", "specs", fullMatch = false)
                    true
                }
            }
            snap("03-tree-badges")

            // Restore the normal docked layout so the remaining shots frame the editor + tool window.
            setOpenSpecMaximized(false)

            // Docked, the default split (68% tree) squeezes the Change Deltas pane into a column a few
            // words wide. Widen the tool window a little and give the pane more of the split.
            ideFrame {
                runCatching {
                    val splitter = x("//div[@accessiblename='OpenSpec preview change deltas badged']" +
                        "/ancestor::div[@class='OnePixelSplitter'][1]")
                    withContext(OnDispatcher.EDT) {
                        cast(splitter.component, SplitterRef::class).setProportion(0.5f)
                        utility(ToolWindowManagerRef::class).getInstance(project).getToolWindow("OpenSpec")
                            ?.let { cast(it, ToolWindowExRef::class).stretchWidth(230) }
                    }
                }.onFailure { println("01: couldn't widen the Change Deltas pane: $it") }
            }

            // 01 — hero: tree + workflow chips + a spec in the editor.
            openFile("openspec/specs/greeting/spec.md", project)
            waitUntil("editor settles") { !isCodeAnalysisRunning(project) }
            ideFrame { waitUntil("workflow panel shows the seeded change") { hasText("demo-add-farewell") } }
            snap("01-spec-browser")

            // 05 — change lifecycle: proposal open, chips prominent.
            openFile("openspec/changes/demo-add-farewell/proposal.md", project)
            waitUntil("editor settles") { !isCodeAnalysisRunning(project) }
            snap("05-change-workflow")

            // Captures the dialog titled `title` over the IDE once `ready` holds for it, then cancels it.
            fun snapDialog(name: String, title: String, timeout: Duration = 90.seconds,
                           ready: (com.intellij.driver.sdk.ui.components.UiComponent) -> Unit = {}) {
                val dialog = ui.x { byTitle(title) }
                waitUntil("'$title' shows", timeout) { dialog.present() }
                ready(dialog)
                val window = cast(dialog.component, WindowRef::class)
                snap(name, java.awt.Rectangle(window.getX(), window.getY(), window.getWidth(), window.getHeight()))
                withContext(OnDispatcher.EDT) {
                    cast(dialog.x { byAccessibleName("Cancel") }.component, AbstractButtonRef::class).doClick()
                }
                waitUntil("'$title' closes") { dialog.notPresent() }
            }

            // Fills the note of the dialog titled `title`, captures it over the IDE, then cancels it.
            fun snapNoteDialog(name: String, title: String, note: String) = snapDialog(name, title) { dialog ->
                withContext(OnDispatcher.EDT) {
                    cast(dialog.x { byClass("JBTextArea") }.component, TextComponentRef::class).setText(note)
                }
            }

            expireNotifications()
            // 06 — Regenerate with a note: a done chip's right-click → Regenerate, over the proposal.
            withContext(OnDispatcher.EDT) {
                val chip = cast(ui.x { and(byClass("JBLabel"), byVisibleText("proposal")) }.component,
                    ComponentRef::class).getParent()
                chip.dispatchEvent(new(MouseEventRef::class, chip, java.awt.event.MouseEvent.MOUSE_PRESSED,
                    System.currentTimeMillis(), 0, 10, 10, 1, true, java.awt.event.MouseEvent.BUTTON3))
            }
            val regenerateItem = ui.x { and(byClass("JMenuItem"), byVisibleText("Regenerate")) }
            waitUntil("the chip's menu shows Regenerate") { regenerateItem.present() }
            withContext(OnDispatcher.EDT) {
                val listener = cast(regenerateItem.component, AbstractButtonRef::class).getActionListeners().first()
                utility(MenuSelectionManagerRef::class).defaultManager().clearSelectedPath()
                new(TimerRef::class, 0, listener).apply { setRepeats(false); start() }
            }
            snapNoteDialog("06-regenerate-note", "Regenerate proposal",
                "Say the farewell is shown once per session, and only after the user has signed in.")

            expireNotifications()
            // 07 — Revise Change: the note the agent's own update workflow gets with the change.
            invokeAction("OpenSpec.ReviseChange", now = false)
            snapNoteDialog("07-revise-change", "Revise Change: demo-add-farewell",
                "The farewell should be configurable: add a setting to turn it off.")

            // 04 — Explore: a topic in, and the /openspec-explore handoff copied for the agent, before any change exists.
            expireNotifications()
            withContext(OnDispatcher.EDT) {
                utility(ExploreContextActionRef::class)
                    .runExplore(project, "how should a farewell differ for signed-in and guest users?")
            }
            waitUntil("Explore copied notice", timeout = 2.minutes) {
                utility(ActionCenterRef::class).getNotifications(project).any { it.getContent().contains("explore") }
            }
            snap("04-explore-first-step")
            expireNotifications()

            // 10 — coordination: stores + workset from the isolated registry.
            selectToolWindowContent("Coordination")
            ideFrame {
                waitUntil("store rows render", timeout = 3.minutes) { hasText("demo-platform") }
            }
            snap("10-coordination-stores")

            // 08 — validation: Console filled by Validate, inspection-highlighted spec open.
            // Clear the startup "add settings to Git" notification (it appears after VCS detection,
            // so an early expire misses it) right before firing Validate, mirroring shot 11.
            expireNotifications()
            invokeAction("OpenSpec.Validate", now = false)
            waitUntil("validation summary notification", timeout = 3.minutes) {
                // The Validate balloon body reads "<scope> passed/failed (N errors, M warnings)"
                // (title "Validate", scoped body) — it no longer contains the literal "Validation".
                utility(ActionCenterRef::class).getNotifications(project)
                    .any { it.getContent().contains("failed (") || it.getContent().contains("passed (") }
            }
            openFile("openspec/specs/keyword-in-header/spec.md", project)
            waitUntil("editor settles") { !isCodeAnalysisRunning(project) }
            // openFile re-raises the "add settings to Git" balloon (VCS mapping re-check), so clear
            // just that one here — after the file is open — while leaving the Validate notification.
            withContext(OnDispatcher.EDT) {
                runCatching {
                    utility(ActionCenterRef::class).getNotifications(project)
                        .filter { it.getContent().contains("Git") }
                        .forEach { it.expire() }
                }
            }
            snap("08-validation-quickfix")

            // 09 — grouped validation console: the Validate run above already filled the OpenSpec
            // Console with the structured report — select that tab and capture it showing the
            // FAILED verdict, the error/warning/info count line, per-file group headers, per-severity
            // coloring (the seeded ERROR and WARNING).
            selectToolWindowContent("Console")
            ideFrame {
                waitUntil("validation console renders the grouped report", timeout = 2.minutes) {
                    hasSubtext("Validation FAILED")
                }
            }
            snap("09-validation-console")

            // 11 — the update preview on a project with 1.x files: the OpenSpec 2 migration banner, then Cancel, so
            // nothing is migrated.
            selectToolWindowContent("Browse")
            expireNotifications()
            invokeAction("OpenSpec.Update", now = false)
            snapDialog("11-update-migration-preview", "OpenSpec Update Preview", timeout = 3.minutes) { preview ->
                waitUntil("the OpenSpec 2 migration banner shows") {
                    preview.x { byAccessibleName("OpenSpec 2 migration") }.present()
                }
            }
        }

        // Sanity: every emitted PNG must be non-trivial AND distinct. Without macOS
        // Screen Recording permission, AWT Robot returns the bare desktop wallpaper —
        // large enough to pass a size check but byte-identical across all shots.
        val emitted = listOf("01-spec-browser", "02-change-deltas", "03-tree-badges", "04-explore-first-step",
            "05-change-workflow", "06-regenerate-note", "07-revise-change", "08-validation-quickfix",
            "09-validation-console", "10-coordination-stores", "11-update-migration-preview")
        val small = emitted.filter { Files.size(outputDir.resolve("$it.png")) < 50_000 }
        check(small.isEmpty()) { "suspiciously small captures (blank?): $small" }
        val distinctSizes = emitted.map { Files.size(outputDir.resolve("$it.png")) }.distinct()
        check(distinctSizes.size > 1) {
            "all captures are byte-identical — macOS blocked window capture; grant Screen " +
                "Recording to the launching terminal (System Settings → Privacy & Security) " +
                "and restart it"
        }
    }
}
