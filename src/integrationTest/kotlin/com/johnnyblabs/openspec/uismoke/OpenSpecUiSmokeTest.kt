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
import com.intellij.driver.sdk.ui.components.searchEverywherePopup
import com.intellij.driver.sdk.ui.components.tree
import com.intellij.driver.sdk.ui.ui
import com.intellij.driver.sdk.waitForIndicators
import com.intellij.ide.starter.driver.engine.runIdeWithDriver
import com.intellij.ide.starter.driver.execute
import com.intellij.tools.ide.performanceTesting.commands.CommandChain
import com.intellij.tools.ide.performanceTesting.commands.takeScreenshot
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

    /** JMX stub to flip the register-store UI-smoke seam properties between journey stops. */
    @Remote("java.lang.System")
    interface SystemRef {
        fun setProperty(key: String, value: String): String?
        fun clearProperty(key: String): String?
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
        fun getTitle(): String
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
                // byVisibleText can't see them.
                waitUntil("Browse tree renders its Changes group") { hasText("Changes") }
                waitUntil("workflow panel shows the seeded change 'demo-add-farewell'") {
                    hasText("demo-add-farewell")
                }
                // The workflow label already shows the change name, so read the tree's rows: the change's
                // row is visible only if the first load expanded Changes without anyone clicking.
                val browseTree = tree("//div[@class='Tree']")
                waitUntil("the first load expands Changes, showing the change's row") {
                    browseTree.collectExpandedPaths().any { row ->
                        row.path.size == 3 && row.path[1] == "Changes" && row.path[2].contains("demo-add-farewell")
                    }
                }
            }
        }
    }

    /** Journey 2 — Update cleanup: the legacy-seeded project raises the review notice. */
    @Test
    fun updateActionRaisesCleanupNotice() {
        // The plugin's legacy-file cleanup notice mirrors `openspec update`'s "Files to remove" block. The
        // seeded demo carries legacy `.cursor/commands/openspec-*.md` files, which current CLIs (verified on
        // 1.13.2 and 1.14.3) still list there; the demo's Junie command files are regenerated in place instead,
        // which is why an earlier version of this journey wrongly concluded the block was gone from 1.8 on.
        // CLI 1.14.0 reworded the block's guidance line; the plugin recognizes both wordings.
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

    private fun notificationTitles(driver: Driver): List<String> {
        val project = driver.singleProject()
        return driver.utility(ActionCenterRef::class)
            .getNotifications(project)
            .map { it.getTitle() }
    }

    /**
     * Journey 4 — editor validator parity: the lowercase-header spec draws no
     * requirement-recognition complaint; the keyword-in-header spec draws the
     * targeted diagnostic; a keyword hidden inside a fenced code block draws the
     * missing-keyword diagnostic (CLI 1.6 fence masking). Asserted through the
     * Problems view's rendered text.
     */
    @Test
    fun editorShowsValidatorParityDiagnostics() {
        val projectPath = freshDemoProject()
        // 1.6 fence-masking seed: the only SHALL sits inside a fence, so the
        // inspection must flag the requirement as missing its keyword.
        Files.createDirectories(projectPath.resolve("openspec/specs/fenced-keyword"))
        Files.writeString(
            projectPath.resolve("openspec/specs/fenced-keyword/spec.md"),
            "# Fenced Keyword\n\n## Purpose\nShows that fenced code cannot satisfy the keyword rule.\n\n" +
                "## Requirements\n\n### Requirement: Fenced\nBody without the word.\n\n" +
                "```\nThe system SHALL work.\n```\n\n#### Scenario: T\n- **WHEN** x\n- **THEN** y\n",
        )
        newContext(projectPath).runIdeWithDriver().useDriverAndCloseIde {
            waitForIndicators(5.minutes)
            val project = singleProject()

            openFile("openspec/specs/fenced-keyword/spec.md", project)
            withContext(OnDispatcher.EDT) { getToolWindow("Problems View").show() }
            ideFrame {
                waitUntil("fence-masked missing-keyword diagnostic in Problems view", timeout = 2.minutes) {
                    hasText("Requirement must contain SHALL or MUST")
                }
            }

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
     * Journey 5 — archive guard: Archive on the incomplete change surfaces the Verify pre-flight
     * (upstream's word — the plugin no longer says "Compliance"), cancel leaves the change directory
     * unmoved. The seeded demo is proposal-only (no deltas) so it renders the hard BLOCKED tier; the
     * neutral IN_PROGRESS-tier logic is proven by the pure ArchiveReadinessResultTest/VerifyDialogTest.
     */
    @Test
    fun archiveGuardsIncompleteChange() {
        val projectPath = freshDemoProject()
        newContext(projectPath).runIdeWithDriver().useDriverAndCloseIde {
            waitForIndicators(5.minutes)

            invokeAction("OpenSpec.Archive", now = false)

            // Dialogs are separate windows, NOT descendants of the IDE frame — search
            // from the driver-level UI root (learned from the hierarchy dump).
            waitUntil("verify pre-flight dialog for the incomplete change", timeout = 3.minutes) {
                ui.x { byTitle("Verify — demo-add-farewell") }.present()
            }
            // Robot Escape depends on focus — click the dialog's Cancel button instead.
            ui.x { byTitle("Verify — demo-add-farewell") }
                .x { byAccessibleName("Cancel") }
                .click(null)
            waitUntil("pre-flight dialog closed on cancel") {
                ui.x { byTitle("Verify — demo-add-farewell") }.notPresent()
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
     * Journey 6 — store health follows CLI 1.6 semantics, and Register Existing Store is actionable
     * on the CLI's identity-confirmation gate (change fix-store-register-identity-confirmation).
     * The IDE (and every CLI child process it spawns) runs against an ISOLATED registry via
     * XDG_DATA_HOME, so the journey never touches the user's real OpenSpec data dir — that's
     * what keeps this register-exercising journey inside the no-durable-state-mutation rule.
     *
     * Two UI-smoke seams are flipped per stop via remote System.setProperty/clearProperty (the remote
     * Driver can drive neither the platform file chooser nor a modal Yes/No dialog by clicking):
     *  - `openspec.uismoke.register.store.root` bypasses the file chooser (preselects the folder);
     *  - `openspec.uismoke.register.confirm` (`yes`/`no`) auto-answers the identity confirmation in
     *    place of the modal dialog — the Driver can only *dispose* a dialog, which cancels = NO.
     *
     * Stop C is the intentional break vs. the prior journey: registering a healthy not-yet-a-store
     * root used to dead-end at the confirmation *failure dialog*; now it confirms (seam = `yes`) and
     * the `--yes` retry creates the store identity, so the row registers healthy.
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
        // A second never-a-store root for the silent-abort (confirm=NO) micro-stop.
        val declinedRoot = seedStoreRoot(base, "declined-store", "schema: spec-driven\n", withIdentity = false)

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
            // message + fix — surfaced in the write-failure dialog, never raw stderr. The confirm
            // seam is cleared so this refusal is proven NOT to route through the identity confirmation
            // (a pointer-declared refusal is a different code, not the confirmation gate).
            sys.clearProperty("openspec.uismoke.register.confirm")
            sys.setProperty("openspec.uismoke.register.store.root", pointerRoot.toString())
            fireRegisterAction()
            expectFailureDialog(
                "pointer-declared refusal dialog", "externalized",
                "refusal dialog does not carry the CLI's pointer-declared message"
            )

            // Stop C (INTENTIONAL BREAK — confirm then succeed): a healthy never-a-store root raises
            // the identity-confirmation gate on the no-`--yes` probe; with the confirm seam = `yes` the
            // plugin retries `store register <root> --yes --json`, which creates the store identity, so
            // the new row registers healthy (no unhealthy marker). This replaces the old dead-end that
            // asserted the confirmation *failure dialog*.
            sys.setProperty("openspec.uismoke.register.confirm", "yes")
            sys.setProperty("openspec.uismoke.register.store.root", brandNewRoot.toString())
            fireRegisterAction()
            ideFrame {
                waitUntil("confirmed brand-new store row renders", timeout = 2.minutes) {
                    hasText("brand-new-store")
                }
                check(!hasSubtext("unhealthy openspec-root")) {
                    "confirmed register did not create the store identity via --yes"
                }
            }

            // Stop A (no-confirmation branch): a root that ALREADY carries store identity metadata
            // registers straight through the probe — no gate, no dialog — and lists healthy.
            sys.clearProperty("openspec.uismoke.register.confirm")
            sys.setProperty("openspec.uismoke.register.store.root", healthyRoot.toString())
            fireRegisterAction()
            ideFrame {
                waitUntil("already-identity healthy-empty store row renders", timeout = 2.minutes) {
                    hasText("healthy-empty-store")
                }
                check(!hasSubtext("unhealthy openspec-root")) {
                    "already-identity store rendered the unhealthy marker"
                }
            }
            // No confirmation dialog nor failure dialog should have appeared on the no-gate branch.
            check(ui.x { byTitle("Register Existing Store") }.notPresent()) {
                "an already-identity register raised the confirmation dialog — the probe should not gate"
            }
            check(ui.x { byTitle("Coordination Action Failed") }.notPresent()) {
                "an already-identity register surfaced a failure dialog"
            }

            // Stop NO-path (silent abort end-to-end): a second never-a-store root with confirm = `no`
            // hits the gate, the plugin declines, and NOTHING happens — no --yes retry, no new row, no
            // failure dialog.
            sys.setProperty("openspec.uismoke.register.confirm", "no")
            sys.setProperty("openspec.uismoke.register.store.root", declinedRoot.toString())
            fireRegisterAction()
            check(ui.x { byTitle("Coordination Action Failed") }.notPresent()) {
                "declining the identity confirmation surfaced a failure dialog — abort must be silent"
            }
            ideFrame {
                check(!hasText("declined-store")) {
                    "declining the identity confirmation still registered the store"
                }
            }
        }
    }

    // ---- Journey 7 — Validate results render CLI-reported errors -------------------

    /**
     * Journey 7 — Validate-results: a CLI-reported validate error survives the
     * action → CLI run → JSON parse → merge path and reaches the rendered validation
     * notification. The assertion targets the CLI-PARSED line specifically — the
     * `type/id` path form (`spec/missing-shall`) only the CLI parser produces — so the
     * built-in validator's duplicate diagnostic cannot satisfy it. This is the exact
     * surface where the 1.6 bracket-path parsing bug silently dropped errors.
     * Requires a 1.6+ host CLI (skipped otherwise).
     */
    @Test
    fun validateResultsRenderCliReportedErrors() {
        val cliVersion = hostCliVersion()
        org.junit.jupiter.api.Assumptions.assumeTrue(
            cliVersion != null && Regex("""^(\d+)\.(\d+)""").find(cliVersion)?.destructured
                ?.let { (maj, min) -> maj.toInt() > 1 || (maj.toInt() == 1 && min.toInt() >= 6) } == true
        ) { "validate-results journey needs OpenSpec CLI 1.6+ on the host (found: $cliVersion)" }

        val projectPath = freshDemoProject()
        // Seed a spec whose requirement is missing SHALL/MUST *and has no body prose*: OpenSpec 1.8
        // demoted a body-CARRYING missing-keyword requirement to a non-failing WARNING (valid in
        // default), but a body-LESS one still ERRORs ("must contain SHALL or MUST", at the bracketed
        // path requirements[0]) — the error the CLI-present path renders in the console. (A
        // body-carrying seed would validate clean under 1.8 and this journey would find no error line.)
        Files.createDirectories(projectPath.resolve("openspec/specs/missing-shall"))
        Files.writeString(
            projectPath.resolve("openspec/specs/missing-shall/spec.md"),
            "# Missing Shall\n\n## Purpose\nExercises the CLI-reported missing-keyword error end to end.\n\n" +
                "## Requirements\n\n### Requirement: Records are kept\n\n" +
                "#### Scenario: Persist\n- **WHEN** a record is created\n- **THEN** it can be read back later\n",
        )
        newContext(projectPath).runIdeWithDriver().useDriverAndCloseIde {
            waitForIndicators(5.minutes)

            // The full report renders in the OpenSpec Console tab, whose panel registers
            // when the tool window contents are built — show the tool window first so the
            // report has its real surface (otherwise the action falls into a summary-only
            // notification fallback).
            withContext(OnDispatcher.EDT) { getToolWindow("OpenSpec").show() }
            ideFrame { waitUntil("OpenSpec tool window renders") { hasText("Changes") } }

            invokeAction("OpenSpec.Validate", now = true)

            // Stop 1: the summary notification reports the failure (arrives after the
            // background CLI run + merge completes). The balloon body reads
            // "<scope> failed (N errors, M warnings)" (title "Validate", scoped body) —
            // for whole-project validation "whole project failed (…)"; it no longer
            // contains the literal "Validation".
            waitUntil("validation summary notification raised", timeout = 3.minutes) {
                notificationContents(this).any { it.contains("failed (") }
            }

            // Stop 2: the Console tab renders the CLI-PARSED error line — the type/id
            // path form (spec/missing-shall) only the CLI parser produces, so the
            // built-in validator's duplicate cannot satisfy this. getAndActivate has
            // already selected the Console tab.
            ideFrame {
                waitUntil("console renders the CLI-parsed missing-SHALL line", timeout = 2.minutes) {
                    hasSubtext("spec/missing-shall")
                }
            }
        }
    }

    // ---- Journey 8 — Search Everywhere surfaces spec CONTENT (re-homed from the retired tree) ----

    /**
     * Journey 8 — the re-homed spec content-search. The in-panel Specs tree + its always-on content
     * filter were retired (specs are browsed in the Project View and opened in the editor); content
     * search now lives in a Search Everywhere contributor. This journey opens Search Everywhere and
     * types a token that appears ONLY in a requirement's BODY prose ("greet the user by name" — in no
     * tree label, filename, or requirement NAME), then asserts the contributor surfaces the matching
     * requirement by NAME ("Friendly greeting", which its list cell renders). SE is driven by KEYBOARD
     * only (clicks don't land in this env) and its results list renders cell text (unlike the opaque
     * JEditorPane the old journey needed an accessible-name marker for), so this is a robust gate.
     * Editor-open on selection is covered by the RequirementLineFinder unit tests and deliberately NOT
     * gated here (selecting a specific row would need a click).
     */
    @Test
    fun searchEverywhereFindsSpecContent() {
        newContext(freshDemoProject()).runIdeWithDriver().useDriverAndCloseIde {
            // Wait for indicators BEFORE opening SE so no late balloon/indicator steals focus and
            // auto-closes the popup. The contributor is DumbAware + index-free, so no indexing is
            // needed for correctness — the wait only quiets focus theft.
            waitForIndicators(5.minutes)

            invokeAction("SearchEverywhere", now = false)

            // The popup is a HeavyWeightWindow off the driver root (like a dialog), not under ideFrame.
            val se = ui.searchEverywherePopup()
            waitUntil("Search Everywhere popup open") { se.present() }
            se.keyboard { enterText("greet the user by name") }

            // The contributor's cell renders presentableText() = the requirement NAME, so a body-token
            // search surfaces the "Friendly greeting" row (only THIS contributor emits the requirement
            // NAME — the platform Text search emits the matched line, not the name — so matching the
            // name is the discriminating signal). Our contributor's sortWeight ranks it the top hit, so
            // SE auto-selects it — read the SELECTED cell, not the whole list.
            //
            // Why selectedItems and NOT items: the body token also matches the platform Text-in-Files
            // contributor, whose result co-resides in the "All" tab and paints via FileAndLineTextRenderer.
            // That renderer peeks at the neighbouring row (list.getModel().getElementAt(index-1)) for
            // file grouping, which throws IndexOutOfBoundsException("No Data Model") when the driver's
            // collectItems() bulk-renders every cell during results streaming — starving EVERY poll so
            // items never returns our row even though it is present and selected (screenshot-proven).
            // collectSelectedItems() renders ONLY the selected cell — our own SimpleListCellRenderer —
            // so it never touches FileAndLineTextRenderer and the race cannot occur.
            //
            // Generous timeout: this contributor is DumbAware + index-free (a ReadAction VFS walk), so
            // it populates the "All" tab a beat LATER than the indexed contributors.
            waitUntil("SE surfaces the body-matched requirement by name", timeout = 90.seconds) {
                se.resultsList.selectedItems.any { it.contains("Friendly greeting") }
            }
        }
    }

    // ---- Journey 9 — Browse preview renders a change's consolidated deltas ----------

    /**
     * Journey 9 — consolidated change-deltas view: selecting a CHANGE node (whose path is the change
     * directory, not a .md file) renders the CLI-sourced deltas grouped by capability with operation
     * badges. Requires a 1.6+ host CLI (the plugin spawns `openspec show <change> --type change
     * --json`), so it is skipped otherwise — like the store-health journey.
     *
     * The demo change carries no spec deltas out of the box, so this journey seeds one ADDED delta
     * into its fresh temp copy before boot. The pane's accessible name flips to
     * "OpenSpec preview change deltas badged" ONLY after a successful CLI→parse→render whose fragment
     * contains an `openspec-op-badge` span — so waiting on that single marker proves BOTH that the
     * deltas render fired AND that a badge is present, which is the assertion the JEditorPane's
     * unreadable HTML body otherwise can't give us.
     */
    @Test
    fun previewPaneRendersChangeDeltas() {
        val cliVersion = hostCliVersion()
        org.junit.jupiter.api.Assumptions.assumeTrue(
            cliVersion != null && Regex("""^(\d+)\.(\d+)""").find(cliVersion)?.destructured
                ?.let { (maj, min) -> maj.toInt() > 1 || (maj.toInt() == 1 && min.toInt() >= 6) } == true
        ) { "change-deltas journey needs OpenSpec CLI 1.6+ on the host (found: $cliVersion)" }

        val projectPath = freshDemoProject()
        // Seed a spec-level delta so the change has deltas the consolidated view can badge; the
        // `greeting` main spec already exists in the demo project.
        val delta = projectPath.resolve("openspec/changes/demo-add-farewell/specs/greeting/spec.md")
        Files.createDirectories(delta.parent)
        Files.writeString(delta,
            """
            ## ADDED Requirements

            ### Requirement: Farewell message
            The system SHALL bid the user farewell when the session ends.

            #### Scenario: Session ends
            - **WHEN** the user ends the session
            - **THEN** a farewell message is shown
            """.trimIndent() + "\n")

        newContext(projectPath).runIdeWithDriver().useDriverAndCloseIde {
            waitForIndicators(5.minutes)

            withContext(OnDispatcher.EDT) { getToolWindow("OpenSpec").show() }

            ideFrame {
                waitUntil("Browse tree renders its Changes group") { hasText("Changes") }

                val browseTree = tree("//div[@class='Tree']")
                browseTree.expandPath("OpenSpec", "Changes", fullMatch = false)
                browseTree.clickPath("OpenSpec", "Changes", "demo-add-farewell", fullMatch = false)

                waitUntil("preview pane renders the change's badged deltas", timeout = 2.minutes) {
                    x { byAccessibleName("OpenSpec preview change deltas badged") }.present()
                }

                // An artifact previews its change too. Leave the deltas first (Changes itself has no
                // preview), so the badged name can only come back from the artifact's own render.
                browseTree.clickPath("OpenSpec", "Changes", fullMatch = false)
                waitUntil("selecting Changes shows the empty state") {
                    x { byAccessibleName("OpenSpec preview empty") }.present()
                }
                browseTree.clickPath("OpenSpec", "Changes", "demo-add-farewell", "proposal", fullMatch = false)
                waitUntil("selecting the proposal shows its change's badged deltas", timeout = 2.minutes) {
                    x { byAccessibleName("OpenSpec preview change deltas badged") }.present()
                }
            }
        }
    }

    // ---- Journey 10 — Validate results render the grouped, formatted report --------

    /**
     * Journey 10 — grouped validation console: a project with a deliberate spec ERROR is
     * validated, and the OpenSpec Console renders the structured report — the FAILED verdict
     * line naming the target, the error/warning/info count line, and at least one grouped,
     * severity-tagged issue row. The assertion targets rendered CONTENT (verdict, count,
     * issue row), NOT click-navigation: opening the file at the caret line is a headless-
     * untestable editor behavior, so the wiring is covered by unit tests instead.
     *
     * Robust to the CLI-authoritative merge: the missing-SHALL spec fails the verdict in BOTH modes
     * (the host CLI reports it when present, the built-in validator when absent), and a schemaless
     * config.yaml yields a non-failing built-in config-schema-required WARNING at config.yaml:1 — a
     * resolvable, clickable row that renders whether or not the CLI is present (the built-in always
     * owns config). So the assertions key off group / severity / hyperlink features that survive
     * either mode, not off the built-in's spec-error path form, which the CLI supersedes when present.
     */
    @Test
    fun validateResultsRenderGroupedFormattedReport() {
        val projectPath = freshDemoProject()
        // A spec whose requirement is missing SHALL/MUST *and has no body prose* — a body-LESS
        // requirement still ERRORs (by the CLI when present, else the built-in), so the whole-project
        // result fails and the console renders a grouped, per-severity report. (Under 1.8 a
        // body-carrying missing-keyword requirement is only a non-failing WARNING, which would not fail
        // the verdict; body-less keeps this journey's failing-report premise valid.)
        Files.createDirectories(projectPath.resolve("openspec/specs/formatting-demo"))
        Files.writeString(
            projectPath.resolve("openspec/specs/formatting-demo/spec.md"),
            "# Formatting Demo\n\n## Purpose\nExercises the grouped validation console report.\n\n" +
                "## Requirements\n\n### Requirement: Records are kept\n\n" +
                "#### Scenario: Persist\n- **WHEN** a record is created\n- **THEN** it can be read back later\n",
        )
        // A schemaless config.yaml — the built-in validator (which owns config in both CLI modes)
        // emits a non-failing config-schema-required WARNING at line 1: a resolvable, clickable row
        // that exercises the hyperlink (L<line>) format regardless of whether the host CLI is present.
        Files.writeString(
            projectPath.resolve("openspec/config.yaml"),
            "context: \"grouped-report journey — intentionally no schema field\"\n",
        )
        newContext(projectPath).runIdeWithDriver().useDriverAndCloseIde {
            waitForIndicators(5.minutes)

            // Show the tool window so the Console panel is registered — otherwise Validate falls
            // back to a summary-only notification with no console surface for the report.
            withContext(OnDispatcher.EDT) { getToolWindow("OpenSpec").show() }
            ideFrame { waitUntil("OpenSpec tool window renders") { hasText("Changes") } }

            invokeAction("OpenSpec.Validate", now = true)

            // getAndActivate selects the Console tab; assert the rendered report's STRUCTURE
            // (content, never click-navigation): verdict, count line, the file-group header, the
            // severity label on a row, and the clickable L<line> token — so the grouped /
            // per-severity / hyperlinked format is exercised end to end, not just the verdict.
            ideFrame {
                waitUntil("console renders the FAILED verdict line", timeout = 3.minutes) {
                    hasSubtext("Validation FAILED")
                }
                // The count line is always present and carries the (possibly zero) warning tally.
                waitUntil("console renders the error/warning/info count line") {
                    hasSubtext("warning")
                }
                // The seeded spec's error groups under a header naming the capability — the built-in's
                // file path or the CLI's spec/<id> pseudo-path, both containing "formatting-demo".
                waitUntil("console renders the group header for the seeded spec") {
                    hasSubtext("formatting-demo")
                }
                // The spec error renders with its ERROR severity label (per-severity format).
                waitUntil("console renders the ERROR severity label on a grouped row") {
                    hasSubtext("ERROR")
                }
                // The schemaless config.yaml groups under its own resolvable header...
                waitUntil("console renders the resolvable config.yaml group header") {
                    hasSubtext("config.yaml")
                }
                // ...whose non-failing config-schema-required WARNING at line 1 renders as a clickable
                // L<line> token — exercising the hyperlink format in both CLI-present and CLI-absent
                // modes (the built-in always owns config), so the coverage doesn't depend on the host CLI.
                waitUntil("console renders the clickable L<line> token for the config warning") {
                    hasSubtext("L1")
                }
            }
        }
    }

    // ---- Journey 11 — Validate (Strict) discloses the strict run -------------------

    /**
     * Journey 11 — the per-run Validate (Strict) action (change remove-strict-validation-setting).
     * Invoking OpenSpec.ValidateStrict runs a strict validation that DISCLOSES itself in two
     * rendered surfaces a plain Validate never produces: the summary balloon's title is
     * "Validate (strict)" (strict is never a silent mode), and the Console echoes the
     * `openspec validate --strict` command line. Both disclosures are verdict-independent — the
     * seeded demo project fails validation either way — so the assertions target the
     * strict-SPECIFIC wiring, not the verdict: a default Validate run (title "Validate", a command
     * line without `--strict`) cannot satisfy them. The strict SEMANTICS (warnings-count-as-
     * failures verdict flip) are unit-tested in OpenSpecValidateStrictTest / the summaryText and
     * applyStrictFallbackVerdict tests; this journey covers the action→run→disclosure UI wiring.
     */
    @Test
    fun validateStrictActionDisclosesStrictRun() {
        newContext(freshDemoProject()).runIdeWithDriver().useDriverAndCloseIde {
            waitForIndicators(5.minutes)

            // Show the tool window so the Console panel is registered — otherwise Validate falls
            // back to a summary-only notification and the `--strict` command echo has no surface.
            withContext(OnDispatcher.EDT) { getToolWindow("OpenSpec").show() }
            ideFrame { waitUntil("OpenSpec tool window renders") { hasText("Changes") } }

            invokeAction("OpenSpec.ValidateStrict", now = true)

            // Stop 1: the strict run discloses itself in the summary balloon — its title is
            // "Validate (strict)" (a default Validate titles it "Validate"), so strict is never a
            // silent mode. Verdict-independent; read from the notification title, not its body.
            waitUntil("strict validation balloon titled 'Validate (strict)'", timeout = 3.minutes) {
                notificationTitles(this).any { it.contains("Validate (strict)") }
            }

            // Stop 2: the Console echoes the `openspec validate --strict` command line — the
            // strict-specific `--strict` flag a default Validate never emits, proving the strict
            // path (not the default) ran end to end.
            ideFrame {
                waitUntil("console echoes the --strict flag", timeout = 2.minutes) {
                    hasSubtext("--strict")
                }
            }
        }
    }

    // ---- Journeys 8 & 9 — operation-time model selector ----------------------------
    // Stubs to drive the plugin's own settings + credential gate from the test. markHasApiKey
    // flips isConfigured()'s cache without a PasswordSafe write (no macOS Keychain touch); no key
    // is stored, so the model selector's off-EDT listModels fetch short-circuits on the null key
    // (no network) and the static provider list fills the combo. Mirrors the ToolWindowManagerRef
    // static-getInstance pattern above.

    @Remote("com.johnnyblabs.openspec.settings.OpenSpecSettings", plugin = "com.johnnyblabs.openspec")
    interface OpenSpecSettingsRef {
        fun getInstance(project: Project): OpenSpecSettingsRef
        fun setAiProvider(provider: String)
        fun setAiModel(model: String)
        fun setPreferredDeliveryMethod(method: String)
        fun setPreferredTool(tool: String)
    }

    @Remote("com.johnnyblabs.openspec.ai.AiProvider", plugin = "com.johnnyblabs.openspec")
    interface AiProviderRef {
        fun fromString(name: String): AiProviderRef
    }

    @Remote("com.johnnyblabs.openspec.ai.AiCredentialStore", plugin = "com.johnnyblabs.openspec")
    interface AiCredentialStoreRef {
        fun markHasApiKey(provider: AiProviderRef, present: Boolean)
    }

    @Remote("javax.swing.JComboBox")
    interface JComboBoxRef {
        fun getItemCount(): Int
    }

    /**
     * Configure a Direct API provider through the plugin's own settings + has-key gate (no network, no
     * keychain). Order matters: markHasApiKey — the call that flips isConfigured() true — runs LAST, after
     * the delivery method + provider are committed. isConfigured() gates the tool-selector rebuild, so if
     * the has-key flip landed first, a background refresh could fire the one-shot transition guard before
     * preferredDeliveryMethod committed, leaving restoreToolSelection to default to the detected CLI tool
     * and latch. Committing the settings first makes any transition-fire read DIRECT_API deterministically.
     */
    private fun com.intellij.driver.client.Driver.configureDirectApiGemini() {
        val project = singleProject()
        val settings = utility(OpenSpecSettingsRef::class).getInstance(project)
        settings.setPreferredDeliveryMethod("DIRECT_API")
        settings.setPreferredTool("")
        settings.setAiProvider("Gemini")
        settings.setAiModel("gemini-3.5-flash")
        val providerRef = utility(AiProviderRef::class).fromString("Gemini")
        utility(AiCredentialStoreRef::class).markHasApiKey(providerRef, true)
    }

    /**
     * Journey 8 — model selector (happy path): a Direct API provider configured BEFORE the tool
     * window is first shown surfaces the "Direct API" delivery option, restores it as the selection,
     * and shows the model selector populated from the static provider list; the status strip renders
     * the model + mode. Green on current code — gates the shipped happy path headfully (the EDT combo
     * wiring is not unit-coverable).
     */
    @Test
    fun modelSelectorVisibleWhenDirectApiConfiguredAtConstruction() {
        newContext(freshDemoProject()).runIdeWithDriver().useDriverAndCloseIde {
            waitForIndicators(5.minutes)

            // Configure BEFORE first show, so the panel constructs already-configured.
            configureDirectApiGemini()

            withContext(OnDispatcher.EDT) { getToolWindow("OpenSpec").show() }

            ideFrame {
                waitUntil("workflow panel renders the seeded change") { hasText("demo-add-farewell") }
                // Selection wired into the status strip proves getSelectedModel() reads the model combo
                // AND that Direct API is the active delivery (deliveryModeStatusText renders both).
                waitUntil("status strip shows the model + Direct API", timeout = 2.minutes) {
                    hasText("gemini-3.5-flash") && hasSubtext("Direct API")
                }
            }
            // The model selector combo is present + populated (static Gemini list = 3).
            val modelCombo = ui.x { byAccessibleName("OpenSpec model selector") }
            waitUntil("model selector visible for Direct API") { modelCombo.present() }
            val itemCount = withContext(OnDispatcher.EDT) {
                cast(modelCombo.component, JComboBoxRef::class).getItemCount()
            }
            check(itemCount > 0) { "model selector present but empty — populateModelSelector did not fill the static list" }

            runCatching { execute(CommandChain().takeScreenshot("model-selector/A-configured-at-construction")) }
        }
    }

    /**
     * Journey 9 — model selector refreshes on MID-SESSION Direct API config (regression test for the
     * mid-session-refresh fix). With nothing configured at show, the delivery selector offers no Direct
     * API option and the model selector is absent. After configuring Direct API mid-session and firing
     * OpenSpec.Refresh, the fix re-runs populateToolSelector so Direct API is offered + restored and the
     * model selector appears. RED before the fix (refresh never rebuilt the delivery selector, so Direct
     * API could never be selected). In-product this also fires instantly via the AiConfigListener topic
     * on Settings Apply; the driver can't apply the Settings dialog, so the refresh action exercises the
     * same populateToolSelector re-run sink.
     */
    @Test
    fun modelSelectorAppearsAfterMidSessionDirectApiConfig() {
        newContext(freshDemoProject()).runIdeWithDriver().useDriverAndCloseIde {
            waitForIndicators(5.minutes)

            withContext(OnDispatcher.EDT) { getToolWindow("OpenSpec").show() }
            ideFrame {
                waitUntil("workflow panel renders the seeded change") { hasText("demo-add-farewell") }
                // Precondition (renderer-based, reliable): unconfigured → the status strip shows a
                // non-Direct-API delivery, never Direct API.
                check(!hasSubtext("Direct API")) { "Direct API shown before any config — precondition violated" }
            }

            // Configure Direct API AFTER the panel was built — the bug scenario.
            configureDirectApiGemini()

            // Fire a refresh: the exact sink the transition-guard fix hooks.
            invokeAction("OpenSpec.Refresh", now = false)

            ideFrame {
                waitUntil("model + Direct API surface after mid-session config + refresh", timeout = 2.minutes) {
                    hasText("gemini-3.5-flash") && hasSubtext("Direct API")
                }
            }
            check(ui.x { byAccessibleName("OpenSpec model selector") }.present()) {
                "status strip shows Direct API but the model selector combo is not visible"
            }

            runCatching { execute(CommandChain().takeScreenshot("model-selector/B-after-mid-session-refresh")) }
        }
    }
}
