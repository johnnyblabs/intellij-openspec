import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    id("org.jetbrains.intellij.platform")
}

group = "com.johnnyblabs.openspec"
version = "0.2.1"

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
        }
        changeNotes = """
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

    publishing {
        token = providers.environmentVariable("JETBRAINS_MARKETPLACE_TOKEN")
    }

    pluginVerification {
        ides {
            recommended()
        }
    }
}
