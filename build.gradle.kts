import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    id("jacoco")
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.changelog") version "2.2.1"
    id("org.sonarqube") version "7.3.1.8318"
    id("org.cyclonedx.bom") version "2.4.1"
}

group = "com.johnnyblabs.openspec"
version = "0.2.10"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2024.2")
        bundledPlugin("org.jetbrains.plugins.yaml")
        bundledPlugin("org.jetbrains.plugins.terminal")
        testFramework(TestFrameworkType.Platform)
    }

    implementation("com.google.code.gson:gson:2.13.2")
    implementation("org.commonmark:commonmark:0.24.0")

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
// Ratchet the minimums upward as coverage improves.
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
                minimum = "0.31".toBigDecimal()
            }
        }
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.29".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
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
    setIncludeConfigs(listOf("runtimeClasspath"))
    setOutputFormat("json")
    setOutputName("bom")
}

changelog {
    version.set(project.version.toString())
    path.set(file("CHANGELOG.md").canonicalPath)
    headerParserRegex.set("""v(\d+\.\d+\.\d+).*""".toRegex())
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
