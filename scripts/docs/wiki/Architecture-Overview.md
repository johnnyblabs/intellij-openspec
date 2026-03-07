# Architecture Overview

The OpenSpec plugin is organized into 13 packages with clear layer separation. This page provides visual architecture diagrams.

## Package Architecture

```mermaid
graph TB
    subgraph UI["UI Layer"]
        toolwindow["toolwindow<br/>6 classes"]
        dialogs["dialogs<br/>1 class"]
        editor["editor<br/>3 classes"]
    end

    subgraph Actions["Action Layer"]
        actions["actions<br/>13 classes"]
    end

    subgraph Services["Service Layer"]
        services["services<br/>7 classes"]
        ai["ai<br/>5 classes"]
        validation["validation<br/>6 classes"]
        scaffolding["scaffolding<br/>2 classes"]
        settings["settings<br/>3 classes"]
    end

    subgraph Foundation["Foundation"]
        model["model<br/>11 classes"]
        util["util<br/>4 classes"]
        version["version<br/>1 class"]
    end

    toolwindow --> actions
    toolwindow --> services
    actions --> services
    actions --> ai
    actions --> validation
    services --> model
    services --> util
    services --> version
    ai --> model
    validation --> model
    scaffolding --> model
    editor --> services
```

## Service Dependency Graph

```mermaid
graph LR
    OPS[OpenSpecProjectService] --> CS[ConfigService]
    OPS --> SPS[SpecParsingService]
    OPS --> CHS[ChangeService]
    OPS --> CDS[CliDetectionService]
    OPS --> ATD[AiToolDetectionService]
    OPS --> AOS[ArtifactOrchestrationService]
    OPS --> SET[OpenSpecSettings]

    CHS --> VS[VersionSupport]
    CHS --> SET
    AOS --> CR[CliRunner]
    AOS --> COP[CliOutputParser]
    CR --> CDS

    DAS[DirectApiService] --> ACS[AiCredentialStore]
    DAS --> AP[AiProvider]
    BIV[BuiltInValidator] --> VS
```

## Action Class Hierarchy

```mermaid
classDiagram
    class AnAction {
        <<IntelliJ Platform>>
        +update(AnActionEvent)
        +actionPerformed(AnActionEvent)
    }

    class OpenSpecBaseAction {
        <<abstract>>
        #isOpenSpecProject(Project) boolean
        +update(AnActionEvent)
    }

    class OpenSpecCliAction {
        <<abstract>>
        #getCliCommand() String
        #handleCliMissing(Project)
        #processOutput(String)
    }

    AnAction <|-- OpenSpecBaseAction
    OpenSpecBaseAction <|-- OpenSpecCliAction
    OpenSpecBaseAction <|-- OpenSpecInitAction
    OpenSpecBaseAction <|-- OpenSpecProposeAction
    OpenSpecBaseAction <|-- OpenSpecApplyAction
    OpenSpecBaseAction <|-- OpenSpecValidateAction
    OpenSpecBaseAction <|-- GenerateArtifactAction
    OpenSpecBaseAction <|-- GenerateAllArtifactsAction
    OpenSpecCliAction <|-- OpenSpecArchiveAction
    OpenSpecCliAction <|-- OpenSpecListAction
    OpenSpecCliAction <|-- OpenSpecRefreshAction
    AnAction <|-- CreateDeltaSpecAction
```

## Data Model

```mermaid
classDiagram
    class SpecFile {
        +String domain
        +String title
        +String filePath
        +List~Requirement~ requirements
    }

    class Requirement {
        +String name
        +String body
        +String keyword
        +List~Scenario~ scenarios
    }

    class Scenario {
        +String name
        +List~String~ clauses
    }

    class Change {
        +String name
        +String path
        +ChangeMetadata metadata
        +List~String~ artifactFiles
    }

    class ChangeMetadata {
        +String schema
        +ChangeStatus status
        +String created
    }

    class ChangeStatus {
        <<enum>>
        PROPOSED
        APPLIED
        ARCHIVED
        UNKNOWN
    }

    class ChangeArtifactDag {
        +String changeName
        +String schemaName
        +boolean isComplete
        +List~String~ applyRequires
        +List~ArtifactInfo~ artifacts
    }

    class ArtifactInfo {
        +String id
        +String outputPath
        +ArtifactStatus status
        +List~String~ missingDeps
    }

    class ArtifactStatus {
        <<enum>>
        DONE
        READY
        BLOCKED
        UNKNOWN
    }

    class ArtifactInstruction {
        +String changeName
        +String artifactId
        +String instruction
        +String template
        +buildPrompt() String
    }

    SpecFile "1" --> "*" Requirement
    Requirement "1" --> "*" Scenario
    Change "1" --> "1" ChangeMetadata
    ChangeMetadata --> ChangeStatus
    ChangeArtifactDag "1" --> "*" ArtifactInfo
    ArtifactInfo --> ArtifactStatus
```

## Tree Model Structure

```mermaid
graph TD
    root["OpenSpec (root)"] --> specs["Specs"]
    root --> changes["Changes"]
    root --> archive["Archive"]

    specs --> d1["domain-1/"]
    specs --> d2["domain-2/"]
    d1 --> s1["spec.md"]
    s1 --> r1["Requirement 1"]
    s1 --> r2["Requirement 2"]

    changes --> c1["change-1 &#91;proposed&#93;"]
    changes --> c2["change-2 &#91;applied&#93;"]
    c1 --> a1["✓ proposal.md"]
    c1 --> a2["○ design.md"]
    c1 --> a3["− tasks.md"]
    c1 --> ds["delta-specs/"]
    ds --> ds1["actions.md"]

    archive --> ac1["old-change &#91;archived&#93;"]
```

**Node data is stored as `TreeNodeData` records** with: `label`, `type` (TreeNodeType enum), `filePath`, `changeName`, `artifactId`.

## CLI/Built-in Hybrid Flow

```mermaid
flowchart TD
    A[Action triggered] --> B{Is CLI available?}
    B -->|Yes| C[Run CLI command]
    C --> D{CLI succeeded?}
    D -->|Yes| E[Process CLI output]
    D -->|No| F[Show error in Console]
    B -->|No| G[handleCliMissing]
    G --> H{Has built-in<br/>implementation?}
    H -->|Yes| I[Run built-in logic]
    H -->|No| J[Show 'CLI required' message]
    I --> K[Show result]
    E --> K
```

This hybrid approach ensures the plugin is functional without the CLI while providing enhanced features when it's available.

---

**Previous:** [[Troubleshooting]] | **Next:** [[Package-Reference]]
