package com.johnnyblabs.openspec.uismoke

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.Project
import com.intellij.driver.sdk.getToolWindow
import com.intellij.driver.sdk.isCodeAnalysisRunning
import com.intellij.driver.sdk.openFile
import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.singleProject
import com.intellij.driver.sdk.ui.components.ideFrame
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * UI smoke journeys (spec: ui-smoke-journeys): a real sandbox IDE booted by the Starter
 * framework with the locally built plugin installed, driven via the Driver SDK (242 line,
 * matching the 2024.2 target). Journeys assert PRESENCE AND WIRING of rendered surfaces —
 * never textual prose or pixels — and are policy-bound to manual dispatch / release
 * gating, never a per-PR blocker.
 *
 * The demo project fixture is the captured output of scripts/seed-lifecycle-demo.sh (the
 * single seeding source shared with the manual lifecycle-testdrive skill). Each journey
 * copies it to a fresh temp dir: per-project IDE state (e.g. the cleanup-dismissal
 * memory) is keyed to the project path, and journeys must not share state.
 *
 * Failure artifacts (IDE logs, screenshots) are collected by Starter under out/perf-startup/.
 */
class OpenSpecUiSmokeTest {

    /** JMX stub for the platform's notification center (no SDK helper on the 242 line). */
    @Remote("com.intellij.notification.ActionCenter")
    interface ActionCenterRef {
        fun getNotifications(project: Project?): List<NotificationRef>
    }

    /** JMX stub to flip the register-store UI-smoke seam property between journey stops. */
    @Remote("java.lang.System")
    interface SystemRef {
        fun setProperty(key: String, value: String): String?
    }

    // Programmatic tool-window content selection: robot clicks on ContentTabLabel are
    // unreliable in the Starter run (SmoothRobot "click unsuccessful"), so the store-health
    // journey selects the Coordination tab through the platform API instead. The SDK's own
    // ToolWindow ref exposes only show/hide — these richer stubs follow its getInstance pattern.
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

    /** Programmatic action fire — physical robot clicks don't land in this environment. */
    @Remote("com.intellij.openapi.actionSystem.impl.ActionButton")
    interface ActionButtonRef {
        fun click()
    }

    /** Programmatic modal-dialog dismissal (ends the dialog's modality). */
    @Remote("java.awt.Window")
    interface WindowRef {
        fun dispose()
    }

    @Remote("com.intellij.notification.Notification")
    interface NotificationRef {
        fun getContent(): String
    }

    private fun freshDemoProject(): Path {
        val fixture = Path.of(System.getProperty("demo.project.path"))
        check(Files.isDirectory(fixture)) {
            "demo fixture missing at $fixture — regenerate with scripts/seed-lifecycle-demo.sh"
        }
        val target = Files.createTempDirectory("openspec-ui-smoke-")
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

    /** Poll a presence predicate with a deadline — the smoke tests' one wait primitive. */
    private fun waitUntil(what: String, timeout: Duration = 90.seconds, probe: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeout.inWholeMilliseconds
        while (System.currentTimeMillis() < deadline) {
            runCatching { if (probe()) return }
            Thread.sleep(2_000)
        }
        throw AssertionError("Timed out waiting for: $what")
    }

    /** Journey 1 — open & render: tool window opens and shows the seeded tree. */
    @Test
    fun toolWindowRendersSeededProject() {
        newContext(freshDemoProject()).runIdeWithDriver().useDriverAndCloseIde {
            waitForIndicators(5.minutes)

            // ToolWindow.show() asserts EDT — route the JMX call through the dispatcher.
            withContext(OnDispatcher.EDT) { getToolWindow("OpenSpec").show() }

            ideFrame {
                // Presence-level assertions via RENDERED TEXT (hasText), not component
                // queries — tree rows are cell-renderer paint, not Swing components, so
                // byVisibleText can't see them. Tree groups render collapsed by default;
                // the group labels and the workflow panel's active-change label are the
                // reliably visible seeded content.
                waitUntil("Browse tree renders its Specs group") { hasText("Specs") }
                waitUntil("Browse tree renders its Changes group") { hasText("Changes") }
                waitUntil("workflow panel shows the seeded change 'demo-add-farewell'") {
                    hasText("demo-add-farewell")
                }
            }
        }
    }

    /** Journey 2 — Update cleanup: the legacy-seeded project raises the review notice. */
    @Test
    fun updateActionRaisesCleanupNotice() {
        newContext(freshDemoProject()).runIdeWithDriver().useDriverAndCloseIde {
            waitForIndicators(5.minutes)

            invokeAction("OpenSpec.Update", now = false)

            // The review notice arrives after the background `openspec update` finishes.
            waitUntil("cleanup review notice raised", timeout = 3.minutes) {
                notificationContents(this).any { it.contains("legacy file(s)") }
            }
        }
    }

    /** Journey 3 — Settings: the plugin's Schemas section renders. */
    @Test
    fun settingsSchemasSectionRenders() {
        newContext(freshDemoProject()).runIdeWithDriver().useDriverAndCloseIde {
            waitForIndicators(5.minutes)

            invokeAction("ShowSettings", now = false)

            ideFrame {
                // The Settings dialog focuses its search field on open; typing filters
                // the tree and Enter opens the best match — the plugin's page.
                waitUntil("settings dialog open") {
                    x { byType("com.intellij.openapi.ui.impl.DialogPanelWrapper") }.present() ||
                            x { byAccessibleName("Settings") }.present()
                }
                keyboard {
                    enterText("OpenSpec", 50)
                    enter()
                }
                waitUntil("Schemas section renders (Open Templates action present)") {
                    x { byAccessibleName("Open Templates") }.present()
                }
            }
        }
    }

    private fun notificationContents(driver: Driver): List<String> {
        val project = driver.singleProject()
        return driver.utility(ActionCenterRef::class)
            .getNotifications(project)
            .map { it.getContent() }
    }

    /**
     * Journey 4 — editor validator parity (of 5): the lowercase-header spec draws no
     * requirement-recognition complaint; the keyword-in-header spec draws the
     * targeted diagnostic. Asserted through the Problems view's rendered text.
     */
    @Test
    fun editorShowsValidatorParityDiagnostics() {
        newContext(freshDemoProject()).runIdeWithDriver().useDriverAndCloseIde {
            waitForIndicators(5.minutes)
            val project = singleProject()

            openFile("openspec/specs/keyword-in-header/spec.md", project)
            withContext(OnDispatcher.EDT) { getToolWindow("Problems View").show() }
            ideFrame {
                waitUntil("targeted keyword-placement diagnostic in Problems view", timeout = 2.minutes) {
                    hasText("Requirement 'The system SHALL demonstrate the header hint' has its RFC 2119 keyword only in the header — move the keyword onto the requirement body line")
                }
            }

            openFile("openspec/specs/greeting/spec.md", project)
            waitUntil("code analysis settles on the greeting spec", timeout = 2.minutes) {
                !isCodeAnalysisRunning(project)
            }
            ideFrame {
                // Parity: the lowercase '### requirement:' header counts as a requirement
                // heading, so the missing-requirement inspection must NOT fire.
                check(!hasText("Spec file should contain at least one '### Requirement:' heading")) {
                    "lowercase header was flagged — CLI 1.4 parity regression"
                }
            }
        }
    }

    /**
     * Journey 5 — archive guard: Archive on the 1/4-complete change surfaces the
     * compliance pre-flight, cancel leaves the change directory unmoved.
     */
    @Test
    fun archiveGuardsIncompleteChange() {
        val projectPath = freshDemoProject()
        newContext(projectPath).runIdeWithDriver().useDriverAndCloseIde {
            waitForIndicators(5.minutes)

            invokeAction("OpenSpec.Archive", now = false)

            // Dialogs are separate windows, NOT descendants of the IDE frame — search
            // from the driver-level UI root (learned from the hierarchy dump).
            waitUntil("compliance pre-flight dialog for the incomplete change", timeout = 3.minutes) {
                ui.x { byTitle("Compliance Check — demo-add-farewell") }.present()
            }
            // Robot Escape depends on focus — click the dialog's Cancel button instead.
            ui.x { byTitle("Compliance Check — demo-add-farewell") }
                .x { byAccessibleName("Cancel") }
                .click(null)
            waitUntil("pre-flight dialog closed on cancel") {
                ui.x { byTitle("Compliance Check — demo-add-farewell") }.notPresent()
            }
        }
        check(Files.isDirectory(projectPath.resolve("openspec/changes/demo-add-farewell"))) {
            "change directory was moved despite cancelling the archive"
        }
    }

    // ---- Journey 6 — store health follows CLI 1.6 semantics ----------------------

    private fun hostCliVersion(): String? = runCatching {
        ProcessBuilder("openspec", "--version").redirectErrorStream(true).start()
            .inputStream.bufferedReader().readText().trim().lines().lastOrNull()
    }.getOrNull()

    private fun seedStoreRoot(base: Path, name: String, configYaml: String, withIdentity: Boolean): Path {
        val root = base.resolve(name)
        Files.createDirectories(root.resolve("openspec"))
        Files.writeString(root.resolve("openspec/config.yaml"), configYaml)
        if (withIdentity) {
            Files.createDirectories(root.resolve(".openspec-store"))
            Files.writeString(root.resolve(".openspec-store/store.yaml"), "version: 1\nid: $name\n")
        }
        return root
    }

    /**
     * Journey 6 — store health follows CLI 1.6 semantics (change adapt-store-health-to-1-6).
     * The IDE (and every CLI child process it spawns) runs against an ISOLATED registry via
     * XDG_DATA_HOME, so the journey never touches the user's real OpenSpec data dir — that's
     * what keeps this register-exercising journey inside the no-durable-state-mutation rule.
     * The register action's file chooser is bypassed through the
     * `openspec.uismoke.register.store.root` seam, flipped per stop via remote System.setProperty.
     * Requires a 1.6+ host CLI (skipped otherwise).
     */
    @Test
    fun storeHealthFollowsCli16Semantics() {
        val cliVersion = hostCliVersion()
        org.junit.jupiter.api.Assumptions.assumeTrue(
            cliVersion != null && Regex("""^(\d+)\.(\d+)""").find(cliVersion)?.destructured
                ?.let { (maj, min) -> maj.toInt() > 1 || (maj.toInt() == 1 && min.toInt() >= 6) } == true
        ) { "store-health journey needs OpenSpec CLI 1.6+ on the host (found: $cliVersion)" }

        val base = Files.createTempDirectory("openspec-ui-smoke-stores-")
        val xdg = Files.createDirectories(base.resolve("xdg-data"))
        // Pre-seed ONE registered fresh/config-only store via the real host CLI so the
        // Coordination tab has state to show (an empty registry hides the tab) — and its row
        // is itself the healthy-empty rendering under test.
        val seedRoot = seedStoreRoot(base, "seed-empty-store", "schema: spec-driven\n", withIdentity = true)
        val register = ProcessBuilder("openspec", "store", "register", seedRoot.toString(), "--yes", "--json")
            .apply { environment()["XDG_DATA_HOME"] = xdg.toString() }
            .redirectErrorStream(true).start()
        check(register.waitFor() == 0) { "seeding register failed: ${register.inputStream.bufferedReader().readText()}" }

        val healthyRoot = seedStoreRoot(base, "healthy-empty-store", "schema: spec-driven\n", withIdentity = true)
        val pointerRoot = seedStoreRoot(base, "pointer-store", "store: some-external-store\n", withIdentity = false)
        val brandNewRoot = seedStoreRoot(base, "brand-new-store", "schema: spec-driven\n", withIdentity = false)

        val context = newContext(freshDemoProject()).apply {
            applyVMOptionsPatch { withEnv("XDG_DATA_HOME", xdg.toString()) }
        }
        context.runIdeWithDriver().useDriverAndCloseIde {
            waitForIndicators(5.minutes)
            val project = singleProject()
            withContext(OnDispatcher.EDT) { getToolWindow("OpenSpec").show() }

            // The Coordination content is added asynchronously once the (isolated) registry
            // shows state — poll for it, then select it programmatically.
            waitUntil("Coordination tab appears and is selected", timeout = 3.minutes) {
                withContext(OnDispatcher.EDT) {
                    val manager = utility(ToolWindowManagerRef::class).getInstance(project)
                    val contentManager = manager.getToolWindow("OpenSpec")?.getContentManager()
                    val coordination = contentManager?.findContent("Coordination")
                    if (coordination != null) {
                        contentManager.setSelectedContent(coordination)
                        true
                    } else false
                }
            }

            ideFrame {
                // Stop A (rendering): the seeded fresh store — planning dirs absent, doctor
                // healthy:true with present:false — must list with NO error marker.
                waitUntil("seeded healthy-empty store row renders", timeout = 2.minutes) {
                    hasText("seed-empty-store")
                }
                check(!hasSubtext("unhealthy openspec-root")) {
                    "healthy-empty store rendered the unhealthy marker — 1.6 semantics regression"
                }
                check(!hasSubtext("metadata issue")) {
                    "healthy-empty store rendered a metadata error marker"
                }
            }

            val sys = utility(SystemRef::class)

            // Physical robot clicks don't land in this environment (SmoothRobot "click
            // unsuccessful") — fire the register action programmatically via ActionButton.click()
            // and end each failure dialog's modality via Window.dispose(), both on the EDT.
            val fireRegisterAction = {
                val button = ui.x { byAccessibleName("Register Existing Store") }
                waitUntil("register toolbar button present") { button.present() }
                withContext(OnDispatcher.EDT) {
                    cast(button.component, ActionButtonRef::class).click()
                }
            }
            val expectFailureDialog = { what: String, expectedText: String, complaint: String ->
                val dialog = ui.x { byTitle("Coordination Action Failed") }
                waitUntil(what, timeout = 2.minutes) { dialog.present() }
                check(dialog.hasSubtext(expectedText)) { complaint }
                withContext(OnDispatcher.EDT) {
                    cast(dialog.component, WindowRef::class).dispose()
                }
                waitUntil("$what dismissed") { dialog.notPresent() }
            }

            // Stop B (refusal wiring): a store:-pointer root is refused with the CLI's
            // message + fix — surfaced in the write-failure dialog, never raw stderr.
            sys.setProperty("openspec.uismoke.register.store.root", pointerRoot.toString())
            fireRegisterAction()
            expectFailureDialog(
                "pointer-declared refusal dialog", "externalized",
                "refusal dialog does not carry the CLI's pointer-declared message"
            )

            // Stop C (confirmation wiring): a never-a-store root gets 1.6's identity
            // confirmation envelope, surfaced with its --yes fix text; the plugin never
            // auto-confirms.
            sys.setProperty("openspec.uismoke.register.store.root", brandNewRoot.toString())
            fireRegisterAction()
            expectFailureDialog(
                "identity-confirmation dialog", "--yes",
                "confirmation envelope's fix text not surfaced"
            )

            // Stop A (register wiring): registering a fresh root WITH identity succeeds on
            // 1.6 (1.5 refused it) and the new row lists healthy — no error marker.
            sys.setProperty("openspec.uismoke.register.store.root", healthyRoot.toString())
            fireRegisterAction()
            ideFrame {
                waitUntil("freshly registered healthy-empty store row renders", timeout = 2.minutes) {
                    hasText("healthy-empty-store")
                }
                check(!hasSubtext("unhealthy openspec-root")) {
                    "freshly registered healthy-empty store rendered the unhealthy marker"
                }
            }
        }
    }
}
