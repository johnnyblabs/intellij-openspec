import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    id("org.jetbrains.intellij.platform")
}

group = "com.johnnyblabs.openspec"
version = "0.2.8"

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
        changeNotes = """
            <h3>v0.2.6 — Stability &amp; Context</h3>
            <ul>
                <li><b>Explore context enrichment</b>: full artifact content (proposal, design, tasks, delta specs) and requirement descriptions now included in assembled context</li>
                <li><b>Delta spec quick-fix</b>: MODIFIED requirements missing scenarios offer a one-click fix that copies the full requirement block from the main spec</li>
                <li><b>Lenient config parsing</b>: unknown fields and type mismatches in <code>config.yaml</code> are silently ignored instead of raising parse errors</li>
                <li><b>YAML field ordering</b>: <code>ConfigService</code> preserves field order via <code>LinkedHashMap</code> for readable YAML output</li>
                <li><b>PSI stability</b>: all inspections guard against zero-length PSI elements and invalid offsets, preventing editor crashes</li>
            </ul>
            <h3>v0.2.5 — Validation &amp; Onboarding</h3>
            <ul>
                <li><b>Config version validation</b>: version field presence, recognition, and required-fields-per-version checks</li>
                <li><b>Schema-version cross-check</b>: warn when a change uses a schema incompatible with the project version</li>
                <li><b>Smart onboarding</b>: skip setup wizard for already-initialized projects, show tree view directly</li>
                <li><b>Icon bar redesign</b>: Apply and Compliance promoted to first-class icon bar actions</li>
                <li><b>Overflow menu cleanup</b>: trimmed to change-scoped actions only (Sync Specs, Cancel Generation)</li>
            </ul>
            <h3>v0.2.4 — Open Source &amp; Workflow Engine</h3>
            <ul>
                <li><b>Fast-Forward</b>: one-click change creation + full artifact generation</li>
                <li><b>Continue</b>: incremental one-artifact-at-a-time generation</li>
                <li><b>Verify</b>: check artifact completeness and requirement coverage</li>
                <li><b>Pipeline redesign</b>: interactive chips with click-to-generate, context menus, icon bar, and status strip</li>
                <li><b>Explore panel</b>: assembled project context for AI conversations with auto-refresh</li>
                <li><b>Custom schemas</b>: list, fork, and create workflow schemas via CLI</li>
                <li><b>Config viewer</b>: browse openspec/config.yaml as tree nodes</li>
                <li><b>Compliance pre-flight</b>: three-category check gate before archiving</li>
                <li><b>Delta spec sync</b>: merge ADDED/MODIFIED/REMOVED sections with diff preview</li>
                <li><b>Bulk archive</b>: multi-change archive with conflict detection</li>
                <li><b>Built-in validation</b>: 14 rule codes covering config, specs, changes, and delta specs</li>
                <li><b>CLI update action</b>: refresh agent instruction files from the IDE</li>
                <li><b>Open source</b>: Apache 2.0 license, GitHub CI, community files</li>
            </ul>
            <h3>v0.2.2 — Review Ready</h3>
            <ul>
                <li>Updated Anthropic API to version 2024-06-01</li>
                <li>Fixed OpenAI o1-series model compatibility</li>
                <li>Monochrome tool window icon matching JetBrains platform conventions</li>
                <li>Corrected CLI install instructions and standardized UI terminology</li>
                <li>Updated vendor URL and contact information</li>
            </ul>
            <h3>v0.2.1 — Patch Fixes</h3>
            <ul>
                <li>CLI-aligned init: delegates to <code>openspec init</code> when CLI detected, generating skills and commands for all 24 supported AI tools</li>
                <li>Branded onboarding: 32x32 OpenSpec icon and "Spec-Driven Development" tagline in getting started panel and setup wizard</li>
                <li>Fix: first-run state detection — projects with archived changes skip onboarding correctly</li>
                <li>Fix: wizard "Create Your First Change" now actually persists the change to disk</li>
                <li>Fix: VFS refresh timing — no more false config-missing errors after init</li>
                <li>Fix: deprecated API cleanup (ActionUtil.performActionDumbAwareWithCallbacks)</li>
                <li>HiDPI-safe text widths and improved dark theme tree colors</li>
                <li>CI: custom java-21 runner with baked JDK, Gradle, and Node — zero-download builds</li>
            </ul>
            <h3>v0.2.0 — Spec Intelligence</h3>
            <ul>
                <li>Gutter markers: <code>@spec</code> references in Java source are annotated with clickable icons linking back to the spec</li>
                <li>Coverage panel: new Coverage tab in the OpenSpec tool window shows which requirements are referenced in code</li>
                <li>Removed in-plugin tracker integration (Forgejo/Plane) — external AI workflows handle this better</li>
            </ul>
            <h3>v0.1.0 — Ship It Clean</h3>
            <ul>
                <li>Spec browsing with tree view (domains, capabilities, requirements)</li>
                <li>Workflow automation: Init, Propose, Apply, Archive actions</li>
                <li>AI-assisted generation via Claude, OpenAI, and Gemini APIs</li>
                <li>Spec validation and format inspections</li>
                <li>Setup wizard and onboarding</li>
            </ul>
        """.trimIndent()
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
