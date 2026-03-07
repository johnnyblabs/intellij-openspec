plugins {
    id("java")
    id("org.jetbrains.intellij.platform")
}

group = "com.johnnyb.openspec"
version = "0.1.0"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2024.1")
        bundledPlugin("org.jetbrains.plugins.yaml")
    }

    implementation("com.google.code.gson:gson:2.10.1")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

intellijPlatform {
    pluginConfiguration {
        id = "com.johnnyb.openspec"
        name = "OpenSpec"
        version = project.version.toString()
        description = "IntelliJ IDEA plugin for OpenSpec spec-driven development framework"
        vendor {
            name = "johnnyb"
        }
        ideaVersion {
            sinceBuild = "241"
            untilBuild = provider { null }
        }
    }
}
