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
}
