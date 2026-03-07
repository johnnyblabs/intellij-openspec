# Build and Dev Setup

## Prerequisites

| Tool | Version | Purpose |
|------|---------|---------|
| **JDK** | 17+ | Compilation and runtime |
| **IntelliJ IDEA** | 2024.1+ | Development IDE and plugin host |
| **Gradle** | Wrapper included | Build system |
| **Node.js** | Latest LTS | For OpenSpec CLI (optional) |

## Project Structure

```
OpenSpecPlugin/
├── build.gradle.kts           # Build configuration
├── settings.gradle.kts        # Project settings
├── gradle/wrapper/            # Gradle wrapper
├── src/
│   ├── main/
│   │   ├── java/com/johnnyb/openspec/
│   │   │   ├── actions/       # 13 action classes
│   │   │   ├── ai/            # 5 AI integration classes
│   │   │   ├── dialogs/       # 1 dialog class
│   │   │   ├── editor/        # 3 editor enhancement classes
│   │   │   ├── model/         # 11 model classes
│   │   │   ├── scaffolding/   # 2 scaffolding classes
│   │   │   ├── services/      # 7 service classes
│   │   │   ├── settings/      # 3 settings classes
│   │   │   ├── toolwindow/    # 6 UI classes
│   │   │   ├── util/          # 4 utility classes
│   │   │   ├── validation/    # 6 validation classes
│   │   │   └── version/       # 1 version class
│   │   └── resources/
│   │       ├── META-INF/
│   │       │   └── plugin.xml # Plugin descriptor
│   │       └── icons/         # SVG icons
│   └── test/
│       └── java/com/johnnyb/openspec/
│           ├── ConfigServiceTest.java
│           └── SpecParsingServiceTest.java
└── openspec/                  # OpenSpec project data
    ├── config.yaml
    ├── specs/
    └── changes/
```

## Key Gradle Tasks

| Task | Purpose |
|------|---------|
| `./gradlew build` | Compile, test, and package the plugin |
| `./gradlew test` | Run JUnit 5 tests |
| `./gradlew runIde` | Launch sandboxed IntelliJ with plugin |
| `./gradlew buildPlugin` | Produce distributable ZIP |
| `./gradlew verifyPlugin` | Validate plugin descriptor |

## Build Configuration Highlights

```kotlin
// Java 17 target
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

// IntelliJ Platform
intellijPlatform {
    intellijIdeaCommunity("2024.1")
    bundledPlugin("org.jetbrains.plugins.yaml")
}

// Dependencies
implementation("com.google.code.gson:gson:2.10.1")
testImplementation("org.junit.jupiter:junit-jupiter")
```

## plugin.xml Anatomy

The plugin descriptor registers all extensions:

### Services (12)
All project-level services: `OpenSpecProjectService`, `ConfigService`, `SpecParsingService`, `ChangeService`, `CliDetectionService`, `ArtifactOrchestrationService`, `AiToolDetectionService`, `DirectApiService`, `AiCredentialStore`, `BuiltInValidator`, `ScaffoldingService`, `OpenSpecSettings`, `OpenSpecConsoleService`

### Startup Activity
`OpenSpecProjectService$StartupDetection` — detects CLI and AI tools when project opens

### Tool Window
- ID: `OpenSpec`
- Anchor: `right`
- Factory: `OpenSpecToolWindowFactory`
- Icon: `/icons/openspec.svg`

### Inspections (3)
`SpecFormatInspection`, `DeltaSpecInspection`, `ConfigValidationInspection`

### Annotators (2)
`SpecAnnotator`, `ScenarioAnnotator`

### Line Marker
`OpenSpecLineMarkerProvider`

### Actions
12 actions organized in `OpenSpec.MainMenu` group (top-level menu) and `OpenSpec.ToolWindowToolbar` group.

### Settings
`OpenSpecConfigurable` at **Settings → Tools → OpenSpec**

### Notifications
Group `OpenSpec Notifications` with `BALLOON` display type.

## runIde Workflow

1. `./gradlew runIde` launches a sandboxed IntelliJ instance
2. Open or create a test project
3. Run **OpenSpec → Init** to create the project structure
4. Changes to plugin code require restarting `runIde`
5. Resource-only changes (icons, plugin.xml) may hot-reload

### Debugging

1. Create a **Gradle** run configuration for `runIde`
2. Run in Debug mode
3. Set breakpoints in action or service code
4. Trigger the action in the sandboxed IDE

## Testing

Tests use JUnit 5:

```bash
./gradlew test
```

Current test coverage:
- `ConfigServiceTest` — config loading and parsing
- `SpecParsingServiceTest` — spec markdown parsing

See [[Contributing]] for guidance on writing new tests.

---

**Previous:** [[UI-Components]] | **Next:** [[Contributing]]
