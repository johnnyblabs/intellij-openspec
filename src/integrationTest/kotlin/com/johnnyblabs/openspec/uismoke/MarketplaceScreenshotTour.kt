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
 * shot names). v1 deviations from the full manual set:
 *  - 03: captures the inspection highlighting + Console results WITHOUT the Alt+Enter
 *    intention popup (driving the popup remotely is deferred).
 *  - 05 (Settings/Schemas): NOT automated — the Settings dialog is modal and its tree
 *    navigation is not remotely drivable yet; use the manual script for that shot.
 *  - 06: captures the sticky cleanup notification over the IDE, not the opened review
 *    dialog (notification-action invocation is deferred).
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

    /** Runs the host `openspec` CLI with the isolated data dir; returns combined output. */
    private fun hostCli(xdg: Path, workDir: Path, vararg args: String): String {
        val proc = ProcessBuilder("openspec", *args)
            .directory(workDir.toFile())
            .redirectErrorStream(true)
            .apply { environment()["XDG_DATA_HOME"] = xdg.toString() }
            .start()
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
                ?.let { (maj, min) -> maj.toInt() > 1 || (maj.toInt() == 1 && min.toInt() >= 6) } == true
        ) { "screenshot tour needs OpenSpec CLI 1.6+ on the host (found: $cliVersion)" }

        // ---- Pre-boot seeding: everything the shots need, no in-IDE prep -------------
        val projectPath = freshDemoProject()
        val base = Files.createTempDirectory("openspec-shot-state-")
        val xdg = Files.createDirectories(base.resolve("xdg-data"))

        // 1.6 register needs: a config.yaml, a git history, and --yes on a never-a-store root.
        Files.writeString(projectPath.resolve("openspec/config.yaml"), "schema: spec-driven\n")
        git(projectPath, "init", "-q")
        git(projectPath, "-c", "user.email=demo@example.com", "-c", "user.name=Demo", "add", "-A")
        git(projectPath, "-c", "user.email=demo@example.com", "-c", "user.name=Demo",
            "commit", "-qm", "seed")
        val platformStore = base.resolve("demo-platform-store")
        hostCli(xdg, base, "store", "setup", "demo-platform", "--path", platformStore.toString())
        hostCli(xdg, projectPath, "store", "register", projectPath.toString(), "--yes")
        hostCli(xdg, base, "workset", "create", "demo-workset",
            "--member", projectPath.toString(), "--member", platformStore.toString())
        // Project schema fork so Settings' Schemas section shows a [project] provenance tag
        // (rendered in the manual shot 05; harmless state for the automated shots).
        hostCli(xdg, projectPath, "schema", "fork", "spec-driven", "custom-flow")

        Files.createDirectories(outputDir)

        val context = newContext(projectPath).apply {
            applyVMOptionsPatch { withEnv("XDG_DATA_HOME", xdg.toString()) }
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
            fun snap(name: String) {
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

            withContext(OnDispatcher.EDT) { getToolWindow("OpenSpec").show() }
            ideFrame { waitUntil("Browse tree renders") { hasText("Specs") } }

            // 01 — hero: tree + workflow chips + a spec in the editor.
            openFile("openspec/specs/greeting/spec.md", project)
            waitUntil("editor settles") { !isCodeAnalysisRunning(project) }
            ideFrame { waitUntil("workflow panel shows the seeded change") { hasText("demo-add-farewell") } }
            snap("01-spec-browser")

            // 02 — change lifecycle: proposal open, chips prominent.
            openFile("openspec/changes/demo-add-farewell/proposal.md", project)
            waitUntil("editor settles") { !isCodeAnalysisRunning(project) }
            snap("02-change-workflow")

            fun expireNotifications() {
                withContext(OnDispatcher.EDT) {
                    utility(ActionCenterRef::class).getNotifications(project).forEach { it.expire() }
                }
            }

            // 04 — coordination: stores + workset from the isolated registry.
            selectToolWindowContent("Coordination")
            ideFrame {
                waitUntil("store rows render", timeout = 3.minutes) { hasText("demo-platform") }
            }
            snap("04-coordination-stores")

            // 03 — validation: Console filled by Validate, inspection-highlighted spec open.
            invokeAction("OpenSpec.Validate", now = false)
            waitUntil("validation summary notification", timeout = 3.minutes) {
                utility(ActionCenterRef::class).getNotifications(project)
                    .any { it.getContent().contains("Validation") }
            }
            openFile("openspec/specs/keyword-in-header/spec.md", project)
            waitUntil("editor settles") { !isCodeAnalysisRunning(project) }
            snap("03-validation-quickfix")

            // 06 — update cleanup: sticky notification over the IDE (captured LAST; the
            // flow stops re-raising once resolved). Shot 05 (Settings) is manual-only.
            selectToolWindowContent("Browse")
            expireNotifications()
            invokeAction("OpenSpec.Update", now = false)
            waitUntil("cleanup review notification", timeout = 3.minutes) {
                utility(ActionCenterRef::class).getNotifications(project)
                    .any { it.getContent().contains("legacy file(s)") }
            }
            snap("06-update-legacy-cleanup")
        }

        // Sanity: every emitted PNG must be non-trivial AND distinct. Without macOS
        // Screen Recording permission, AWT Robot returns the bare desktop wallpaper —
        // large enough to pass a size check but byte-identical across all shots.
        val emitted = listOf("01-spec-browser", "02-change-workflow", "03-validation-quickfix",
            "04-coordination-stores", "06-update-legacy-cleanup")
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
