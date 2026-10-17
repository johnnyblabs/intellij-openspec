import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask.FailureLevel

plugins {
    id("java")
    id("jacoco")
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.changelog") version "2.2.1"
    id("org.sonarqube") version "7.3.1.8318"
    id("org.cyclonedx.bom") version "3.3.0"
    // Kotlin exists ONLY for the uiSmoke integration-test source set — the Starter/Driver
    // UI-test DSL is Kotlin-idiomatic (extension receivers, kotlin.time) and impractical
    // from Java. Production code and unit tests remain pure Java; kotlin-stdlib is
    // confined to integrationTest configurations and never ships in the plugin.
    kotlin("jvm") version "2.3.0"
}

group = "com.johnnyblabs.openspec"
version = "0.12.0"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

kotlin {
    jvmToolchain(21)
}

// UI smoke journeys live in their own source set so the Starter/Driver stack never
// pollutes the unit-test classpath (or the JaCoCo floor — `check` is untouched).
sourceSets {
    create("integrationTest") {
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().output
    }
}

val integrationTestImplementation: Configuration by configurations.getting {
    extendsFrom(configurations.testImplementation.get())
}
val integrationTestRuntimeOnly: Configuration by configurations.getting {
    extendsFrom(configurations.testRuntimeOnly.get())
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2024.2")
        bundledPlugin("org.jetbrains.plugins.yaml")
        bundledPlugin("org.jetbrains.plugins.terminal")
        testFramework(TestFrameworkType.Platform)
        // Starter/Driver UI-test stack, pinned to the 242.* line matching the 2024.2
        // target (Starter and the driven IDE must stay on the same branch).
        testFramework(TestFrameworkType.Starter, "242.26775.15",
            configurationName = "integrationTestImplementation")
    }

    implementation("com.google.code.gson:gson:2.13.2")
    implementation("org.commonmark:commonmark:0.24.0")

    integrationTestImplementation(platform("org.junit:junit-bom:6.0.3"))
    integrationTestImplementation("org.junit.jupiter:junit-jupiter")
    integrationTestRuntimeOnly("org.junit.platform:junit-platform-launcher")
    integrationTestImplementation("org.kodein.di:kodein-di-jvm:7.20.2")
    integrationTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.10.1")

    testImplementation(platform("org.junit:junit-bom:6.0.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:5.23.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.23.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine")
}

tasks.test {
    useJUnitPlatform()
    javaLauncher = javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(21)
    }
    // JaCoCo agent must be explicitly wired — the IntelliJ Platform custom
    // classloader prevents the default runtime attachment from collecting data.
    configure<JacocoTaskExtension> {
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*")
    }
    // Full exception detail in console output — CI legs without report artifacts
    // (notably the mirror's Windows/macOS matrix) must be diagnosable from the log.
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    // Use instrumented classes — IntelliJ Platform's instrumentCode task
    // modifies bytecode (null-checks, assertions), so JaCoCo must report
    // against the same classes the test JVM actually loaded.
    classDirectories.setFrom(
        fileTree(layout.buildDirectory.dir("instrumented/instrumentCode")) {
            exclude("**/META-INF/**")
        }
    )
    reports {
        html.required = true
        xml.required = true
        csv.required = false
    }
}

// Coverage REGRESSION floor — wired into `check` (so CI's `./gradlew build` runs it).
// This is a backstop against backsliding, NOT a per-PR new-code mandate: thresholds sit
// just below current coverage. New-code test quality is governed by the OpenSpec `tasks`
// rule ("tests SHALL verify real behavior") and the CLAUDE.md contract-test convention.
//
// BASELINE (measured 2026-08-02, fix-direct-api-provider-compliance): INSTRUCTION 39.30%,
// LINE 36.96%, BRANCH 36.32%. Raised from the 2026-07-23 add-validate-project-view-menu baseline
// (INSTRUCTION 37.96%, LINE 35.68%, BRANCH 34.94%) by extracting the three Direct-API response
// parsers + the Claude/OpenAI request builders into static seams and contract-testing them against
// captured provider example responses — a block previously reachable only via a live HTTP call,
// now deterministically covered. Floors raised to ~0.006 below measured (LINE kept a touch lower
// for the known CLI-integration line-coverage wobble). Prior baseline detail retained below.
// BASELINE (measured 2026-07-23, add-validate-project-view-menu): INSTRUCTION 37.96%,
// LINE 35.68%, BRANCH 34.94%. (Raised from the add-tree-status-badges baseline — INSTRUCTION
// 37.53%, LINE 35.28%, BRANCH 34.52% — by the Project-View scoped Validate work: the pure
// ValidateTarget/resolveTarget resolver, the scoped builtInValidate routing, the single-item
// CLI contract tests, and the resolveTarget/update()/routing integration tests.)
// (Previous 2026-07-22 add-tree-status-badges baseline: INSTRUCTION 37.53%, LINE 35.28%,
// BRANCH 34.52%. Raised from the add-change-deltas-view baseline — INSTRUCTION 37.03%,
// LINE 34.89%, BRANCH 34.22%, METHOD 39.89% — by the tree status-badge work: the pure
// SpecTreeCellRenderer.iconForType map, the SpecTreeModel label/type helpers
// (buildChangeLabel/changeNodeType/buildArtifactLabel), and their headless renderer/label
// tests plus the status-complete CLI contract test.) (Earlier add-searchable-spec-viewer baseline —
// INSTRUCTION 36.18%, LINE 34.18%, BRANCH 33.62%, METHOD 39.10% — by the consolidated
// change-deltas view's pure classes (ChangeDeltaModel, DeltaDiffAnchor, renderChangeDeltas)
// and their contract/render/anchor tests; the new Swing/CLI wiring in OpenSpecToolWindowPanel
// dilutes but net coverage still rose on all counters. Before the spec-viewer baseline, the
// earlier 2026-07-22 baseline was INSTRUCTION 35.6%, LINE 33.6%, BRANCH 32.8%; the 2026-07-03
// baseline was INSTRUCTION 32.6%, LINE 30.7%, BRANCH 29.1%.)
// Floors are set below the baseline WITH MARGIN so any regression fails `check`. Ratchet the
// minimums upward as coverage improves; never lower a floor without recorded justification.
//
// MARGIN (recorded justification, 2026-07-23): the add-change-deltas-view ratchet set the
// floors to the measured max (~0.369/0.349/0.342) with near-zero headroom. The self-hosted
// runner's line coverage wobbles run-to-run — some runs don't cover the CLI-integration
// branches (they only cover when CliDetectionService finds openspec via a *login shell*,
// which is non-deterministic on that runner) — so LINE dipped to 0.347 and reddened main on a
// markdown-only archive commit whose code was byte-identical to a passing run. Floors are now
// held ~0.005–0.006 below the measured baseline to absorb that wobble while staying well above
// the pre-deltas viewer floors (0.36/0.34/0.33). Do not re-ratchet to the measured max.
//
// LOWERED 2026-08-03 (restructure-tool-window-panel, task 1.2): 0.394/0.371/0.365 →
// 0.385/0.360/0.360. Justification (recorded per the "never lower without justification" rule):
// the tool-window tree's file-navigation half was retired — buildSpecsNode/buildArchiveNode/
// buildConfigNode + filterNode/cloneSubtree + the always-on tree filter, all well-covered pure
// code, deleted ALONGSIDE their dedicated tests (SpecContentFilterTest, SpecTreeModelConfigTest,
// SpecPreviewFileReadTest). Removing high-coverage code + its tests lowers the AGGREGATE ratio
// even though no retained code lost coverage. Measured after the trim: 0.387/0.364/0.362; floors
// set just below with extra LINE headroom for the documented CLI-integration wobble.
//
// RAISED 2026-08-05 (honor-change-metadata-strip-and-skip-specs): 0.385/0.360/0.360 →
// 0.386/0.362/0.360. The tolerant change-metadata parse added a pure ChangeMetadataParser +
// ChangeMetadata.fromMap seam, contract-tested (ok + malformed branches) against captured 1.7.0
// fixtures. Measured 0.3918/0.3696/0.3661; INSTRUCTION/LINE nudged to ~0.006 below measured, BRANCH
// left at 0.360 (already ~0.006 below — the volatile counter keeps its wobble headroom).
// RAISED 2026-08-06 (verify-status-requires-and-id-keying): 0.386/0.362/0.360 → 0.388/0.366/0.364.
// Adopting the CLI's status `requires` edges for downstream reasoning brought the previously-0%-
// covered getCompletedDownstream under test, and ScaffoldingOverrideTest now exercises the real
// applyScaffoldingOverrides (was a hand-copy). All new coverage is pure/deterministic (no added
// CLI-integration lines), so the wobble profile is unchanged. Measured 0.3943/0.3734/0.3707; floors
// set ~0.006 below (LINE kept a touch lower for the documented CLI-integration line wobble).
// RAISED 2026-08-14 (builtin-validator-duplicate-requirement-parity): 0.388/0.366/0.364 →
// 0.390/0.368/0.366. The 1.8 main-spec duplicate-requirement rule added a branch-heavy cluster to
// validateSpecContent (## Requirements section-bounds detection + a name→first-line dedup map +
// N−1 emission), all deterministically covered by BuiltInValidatorRulesTest / BuiltInValidatorTest.
// Measured 0.3959/0.3751/0.3732; INSTRUCTION nudged to ~0.006 below measured, LINE/BRANCH kept a
// touch lower (BRANCH the volatile counter keeps the most wobble headroom).
// RAISED 2026-08-14 (builtin-validator-delta-discovery-parity): 0.390/0.368/0.366 → 0.391/0.369/0.368.
// The recursive change-delta walk + misplaced-delta ERROR + the skip_specs-aware no-deltas gate added a
// branch cluster to validateDeltaSpecs/validateSingleChange, all deterministically covered by the new
// BuiltInValidatorTest cases. Measured 0.3977/0.3764/0.3762; INSTRUCTION ~0.006 below, LINE/BRANCH keep
// extra headroom. (Note: the `required.contains("specs")` arm of the no-deltas gate is constant-true —
// the single V1_2 baseline always requires specs — so its false branch is intentionally uncovered.)
// RAISED 2026-09-09 (fetch-provider-models-dynamically): 0.391/0.369/0.368 → 0.398/0.378/0.373. The three
// list-models parsers (parseOpenAi/Anthropic/GeminiModels), buildListModelsRequest, and the pure
// AiModelOptions.resolve helper are all deterministically covered by DirectApiModelsContractTest /
// DirectApiModelSelectionTest / the BuildListModelsRequest cases — blocks previously reachable only via a
// live HTTP call. Measured 0.4044/0.3849/0.3808; INSTRUCTION ~0.006 below, LINE/BRANCH keep extra headroom
// (BRANCH the volatile counter keeps the most).
tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    // Match the report: instrumented classes + the test JVM's execution data.
    executionData.setFrom(layout.buildDirectory.file("jacoco/test.exec"))
    classDirectories.setFrom(
        fileTree(layout.buildDirectory.dir("instrumented/instrumentCode")) {
            exclude("**/META-INF/**")
        }
    )
    violationRules {
        rule {
            limit {
                counter = "INSTRUCTION"
                value = "COVEREDRATIO"
                minimum = "0.398".toBigDecimal()
            }
        }
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.378".toBigDecimal()
            }
        }
        rule {
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "0.373".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}

// UI smoke journeys (see openspec/specs/ui-smoke-journeys): a real IDE booted by the
// Starter framework with the built plugin installed, driven over the Driver SDK.
// Deliberately NOT wired into `check`/`test` — heavy (full IDE boot per journey) and
// policy-bound to manual dispatch + release gating, never a per-PR blocker.
// Run with: ./gradlew uiSmoke   (first run downloads an IDE installer to out/perf-startup)
val uiSmoke by intellijPlatformTesting.testIdeUi.registering {
    task {
        val integrationTestSourceSet = sourceSets.getByName("integrationTest")
        testClassesDirs = integrationTestSourceSet.output.classesDirs
        classpath = integrationTestSourceSet.runtimeClasspath
        useJUnitPlatform()
        // Starter installs the plugin under test from this exploded sandbox directory.
        systemProperty("path.to.build.plugin",
            tasks.prepareSandbox.get().pluginDirectory.get().asFile)
        // Seeded demo project (single source: scripts/seed-lifecycle-demo.sh; the
        // committed fixture is its captured output so CI needs no network/CLI to seed).
        systemProperty("demo.project.path",
            layout.projectDirectory.dir("src/integrationTest/testData/lifecycle-demo").asFile)
        // The screenshot tour shares this source set but is a docs tool, not a smoke
        // gate — keep it out of release-gating runs.
        filter { excludeTestsMatching("*MarketplaceScreenshotTour*") }
        dependsOn(tasks.prepareSandbox)
    }
}

// Marketplace screenshot tour: boots one sandbox IDE against the seeded demo and emits
// docs/screenshots/*.png at 1280x800 for the Marketplace listing. A docs tool, NOT a
// smoke journey (no assertions gate a release; uiSmoke excludes it, this runs only it).
// Requires a GUI session and an OpenSpec CLI 1.6+ on PATH; output needs a human
// flip-through before upload. Run with: ./gradlew screenshotTour
val screenshotTour by intellijPlatformTesting.testIdeUi.registering {
    task {
        val integrationTestSourceSet = sourceSets.getByName("integrationTest")
        testClassesDirs = integrationTestSourceSet.output.classesDirs
        classpath = integrationTestSourceSet.runtimeClasspath
        useJUnitPlatform()
        systemProperty("path.to.build.plugin",
            tasks.prepareSandbox.get().pluginDirectory.get().asFile)
        systemProperty("demo.project.path",
            layout.projectDirectory.dir("src/integrationTest/testData/lifecycle-demo").asFile)
        systemProperty("screenshot.output.dir",
            layout.projectDirectory.dir("docs/screenshots").asFile)
        filter { includeTestsMatching("*MarketplaceScreenshotTour*") }
        dependsOn(tasks.prepareSandbox)
    }
}

// Reports coverage + test results to the analysis server. Host URL and token are
// read by the scanner from the SONAR_HOST_URL / SONAR_TOKEN env vars (injected as
// CI secrets) — never hardcoded here, so this stays safe for the public mirror.
// Paths match the JaCoCo XML and JUnit output the `test` task already produces.
sonar {
    properties {
        property("sonar.projectKey", "intellij-openspec")
        property("sonar.projectName", "intellij-openspec")
        property("sonar.coverage.jacoco.xmlReportPaths", "build/reports/jacoco/test/jacocoTestReport.xml")
        property("sonar.junit.reportPaths", "build/test-results/test")
    }
}

// CycloneDX SBOM for dependency/CVE/license visibility. Scoped to runtimeClasspath — the
// IntelliJ Platform SDK graph is huge and mostly provided/test noise, so an unscoped SBOM would
// drown the real deps. Emits build/reports/bom.json, uploaded to the analysis server in CI.
//
// We configure `cyclonedxDirectBom` (the per-project SBOM), not the `cyclonedxBom` aggregate:
// this is a single-module build, and in the 3.x plugin `includeConfigs`/scoping lives on the
// direct task while `cyclonedxBom` only merges per-project direct BOMs. CI invokes
// `cyclonedxDirectBom` to match. (The 3.x plugin — 3.2.4+'s withPluginClassLoader ClassLoader
// isolation — is also what lets the SBOM survive the IntelliJ Platform Gradle Plugin's bundled
// Jackson; the older 2.4.1 plugin failed cyclonedxBom under the platform plugin's Jackson 2.18.)
tasks.cyclonedxDirectBom {
    // 3.x takes includeConfigs as a plain assignment (no .set()).
    includeConfigs = listOf("runtimeClasspath")
    // Pin jsonOutput to build/reports/bom.json — the exact path CI uploads to the dependency
    // server (3.x defaults it to build/reports/cyclonedx/bom.json). The plugin also emits
    // bom.xml alongside it; harmless — build/reports is ephemeral and only bom.json is uploaded.
    jsonOutput.set(layout.buildDirectory.file("reports/bom.json"))
}

changelog {
    version.set(project.version.toString())
    path.set(file("CHANGELOG.md").canonicalPath)
    headerParserRegex.set("""v(\d+\.\d+\.\d+).*""".toRegex())
    // patchChangelog rolls ## Unreleased into a released section at cut time.
    // Emit headers in this file's established "## vX.Y.Z" style (no date), and
    // don't seed the fresh Unreleased section with empty group skeletons —
    // entries here are written per-change, with only the groups they need.
    header.set(provider { "v${version.get()}" })
    groups.empty()
}

intellijPlatform {
    pluginConfiguration {
        id = "com.johnnyblabs.openspec"
        name = "OpenSpec"
        version = project.version.toString()
        // The Marketplace listing description is extracted from README.md between the
        // plugin-description markers — one source of truth for GitHub and the listing.
        // MarketplaceListingHygieneTest enforces the markers, a content floor, and that
        // links are absolute (relative links break on the Marketplace page).
        description = providers.fileContents(layout.projectDirectory.file("README.md")).asText.map {
            val start = "<!-- Plugin description -->"
            val end = "<!-- Plugin description end -->"
            with(it.lines()) {
                if (!containsAll(listOf(start, end))) {
                    throw GradleException("Plugin description markers not found in README.md")
                }
                subList(indexOf(start) + 1, indexOf(end)).joinToString("\n")
            }.let(::markdownToHTML)
        }
        vendor {
            name = "johnnyblabs"
            url = "https://openspec.johnnyblabs.com"
        }
        changeNotes = provider {
            with(changelog) {
                renderItem(
                    (getOrNull(version.get()) ?: getUnreleased())
                        .withHeader(false)
                        .withEmptySections(false),
                    org.jetbrains.changelog.Changelog.OutputType.HTML,
                )
            }
        }
        ideaVersion {
            sinceBuild = "242"
            untilBuild = provider { null }
        }
    }

    signing {
        privateKey = providers.environmentVariable("PLUGIN_SIGNING_KEY")
        certificateChain = providers.environmentVariable("PLUGIN_SIGNING_CERTIFICATE")
        password = providers.environmentVariable("PLUGIN_SIGNING_KEY_PASSWORD")
    }

    publishing {
        token = providers.environmentVariable("JETBRAINS_MARKETPLACE_TOKEN")
    }

    pluginVerification {
        ides {
            recommended()
        }
        // Verifier severity gate — pinned replica of the IPGP 2.18.1 DEFAULT failure set (do NOT
        // shorten: dropping INTERNAL_API_USAGES / OVERRIDE_ONLY_API_USAGES would silently regress
        // coverage; pinning also protects us if a future IPGP weakens its default). DEPRECATED_API_USAGES
        // is deliberately EXCLUDED — we knowingly retain a few plain-@Deprecated usages whose replacement
        // doesn't exist at the 242 floor (ActionUtil.invokeAction→performAction needs 252;
        // ReadAction.compute→computeBlocking; the Reworked Terminal API), tracked for the floor bump.
        // NOTE: scheduled-for-removal usage is NOT (and cannot be) gated here — IPGP 2.18.1 folds
        // forRemoval usages into the verifier's "Deprecated API usages" report section and names them
        // "scheduled for removal" only on the verdict line its problem-collector skips, so
        // FailureLevel.SCHEDULED_FOR_REMOVAL_API_USAGES is structurally inert (verified: it did not fail
        // a build with a real forRemoval usage present). That canary is enforced instead by the
        // verifyPlugin doLast below.
        failureLevel = listOf(
            FailureLevel.COMPATIBILITY_PROBLEMS,
            FailureLevel.INTERNAL_API_USAGES,
            FailureLevel.OVERRIDE_ONLY_API_USAGES,
        )
    }
}

// Scheduled-for-removal API canary. IPGP 2.18.1's failureLevel cannot gate scheduled-for-removal usage
// without also failing plain-@Deprecated usage (the verifier folds forRemoval usages into its
// "Deprecated API usages" section and only names them "scheduled for removal" on the verdict line the
// IPGP problem-collector skips). So we enforce it directly off the verifier's own PLAIN report files,
// keyed on wording the verifier emits ONLY for forRemoval usages: "... will be removed in ..." per usage
// (in deprecated-usages.txt) and "N usages of scheduled for removal API" on the verdict
// (verification-verdict.txt). Plain-@Deprecated usages (our deferred ActionUtil.invokeAction calls) emit
// neither, so they stay green. The .html reports are skipped so a static legend can never false-trip it.
// A call to a platform API the vendor will delete becomes a NoSuchMethodError-class break on a future
// IDE (untilBuild is open-ended), so this fails the verify gate now rather than surfacing as a
// Marketplace warning or a user crash.
tasks.named<VerifyPluginTask>("verifyPlugin") {
    val reportsDir = verificationReportsDirectory
    doFirst { reportsDir.get().asFile.deleteRecursively() } // clean slate — never grep stale reports
    doLast {
        val dir = reportsDir.get().asFile
        val needle = Regex("scheduled for removal|will be removed in", RegexOption.IGNORE_CASE)
        val offenders = dir.walkTopDown()
            .filter { it.isFile && !it.name.endsWith(".html", ignoreCase = true) && needle.containsMatchIn(it.readText()) }
            .map { it.relativeTo(dir).path }
            .toList()
        if (offenders.isNotEmpty()) {
            throw GradleException(
                "verifyPlugin canary: scheduled-for-removal IntelliJ Platform API usage detected — " +
                    "migrate it before it hard-breaks a future IDE. See report files:\n  " +
                    offenders.joinToString("\n  ")
            )
        }
    }
}
