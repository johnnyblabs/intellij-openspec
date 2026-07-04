package com.johnnyblabs.openspec.uismoke

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.Project
import com.intellij.driver.sdk.getToolWindow
import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.singleProject
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
}
