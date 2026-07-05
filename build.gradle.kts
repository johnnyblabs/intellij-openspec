import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    id("jacoco")
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.changelog") version "2.2.1"
    id("org.sonarqube") version "7.3.1.8318"
    id("org.cyclonedx.bom") version "2.4.1"
    // Kotlin exists ONLY for the uiSmoke integration-test source set — the Starter/Driver
    // UI-test DSL is Kotlin-idiomatic (extension receivers, kotlin.time) and impractical
    // from Java. Production code and unit tests remain pure Java; kotlin-stdlib is
    // confined to integrationTest configurations and never ships in the plugin.
    kotlin("jvm") version "2.3.0"
}

group = "com.johnnyblabs.openspec"
version = "0.3.1"

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
// BASELINE (measured 2026-07-03): INSTRUCTION 32.6%, LINE 30.7%, BRANCH 29.1%, METHOD 37.0%.
// Floors are set just below the baseline so any regression fails `check`. Ratchet the
// minimums upward as coverage improves; never lower a floor without recorded justification.
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
                minimum = "0.33".toBigDecimal()
            }
        }
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.31".toBigDecimal()
            }
        }
        rule {
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "0.29".toBigDecimal()
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
tasks.cyclonedxBom {
    includeConfigs.set(listOf("runtimeClasspath"))
    // The 2.4.x plugin deprecated outputFormat/outputName in favor of the json/xmlOutput
    // file properties. Pin jsonOutput to build/reports/bom.json — the exact path CI
    // uploads to the dependency server. (The plugin also emits bom.xml alongside it;
    // harmless — build/reports is ephemeral and only bom.json is uploaded.)
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
        description = "IntelliJ IDEA plugin for OpenSpec spec-driven development framework"
        vendor {
            name = "johnnyblabs"
            url = "https://github.com/johnnyblabs/intellij-openspec"
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
    }
}
