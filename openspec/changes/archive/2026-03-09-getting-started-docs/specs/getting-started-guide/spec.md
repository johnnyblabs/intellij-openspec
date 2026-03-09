## ADDED Requirements

### Requirement: Getting started guide exists

The project SHALL provide a getting-started guide at `docs/getting-started-copilot.md` that walks users through plugin setup and a complete worked example using GitHub Copilot.

#### Scenario: Guide file location
- **WHEN** a user looks for onboarding documentation
- **THEN** a file SHALL exist at `docs/getting-started-copilot.md`

### Requirement: Prerequisites section

The guide SHALL begin with a prerequisites section listing required and optional dependencies.

#### Scenario: Prerequisites are listed
- **WHEN** a user reads the prerequisites
- **THEN** the guide SHALL list IntelliJ IDEA 2024.2+, the OpenSpec plugin, GitHub Copilot extension, and the OpenSpec CLI as requirements

### Requirement: Settings reference section

The guide SHALL include a settings reference covering every configurable option in the plugin's Settings > Tools > OpenSpec panel.

#### Scenario: CLI settings documented
- **WHEN** a user reads the CLI settings section
- **THEN** the guide SHALL explain the CLI path field, Detect button, and version override option

#### Scenario: General settings documented
- **WHEN** a user reads the general settings section
- **THEN** the guide SHALL explain profile selection, auto-refresh toggle, and strict validation toggle

#### Scenario: Delivery settings documented
- **WHEN** a user reads the delivery settings section
- **THEN** the guide SHALL explain detected tools display, the unified delivery dropdown, and what each delivery mode means (Clipboard, Editor Tab, Direct API)

#### Scenario: Direct API settings documented
- **WHEN** a user reads the API settings section
- **THEN** the guide SHALL explain provider selection, API key entry, model selection, and the Test Connection button

### Requirement: Worked example section

The guide SHALL include a complete worked example that creates, generates, implements, and archives an OpenSpec change.

#### Scenario: Example covers full lifecycle
- **WHEN** a user follows the worked example
- **THEN** the guide SHALL walk through propose, generate (all 4 artifacts), apply, and archive steps in order

#### Scenario: Example uses Copilot as AI tool
- **WHEN** an artifact needs to be generated in the example
- **THEN** the guide SHALL show the user how to use GitHub Copilot Chat to generate it (copy prompt, paste in chat, save response)

### Requirement: OpenSpec vs AI role markers

The guide SHALL clearly indicate at each step whether OpenSpec (the framework/plugin) or AI (Copilot) is driving that step.

#### Scenario: Each step is attributed
- **WHEN** a user reads any step in the worked example
- **THEN** a visual marker SHALL indicate whether that step is driven by OpenSpec or by AI

### Requirement: Artifact pipeline explanation

The guide SHALL explain the artifact dependency pipeline and how it appears in the plugin's tool window.

#### Scenario: Pipeline concepts explained
- **WHEN** a user reads the pipeline section
- **THEN** the guide SHALL explain the DAG order (proposal → design → specs → tasks), status indicators (done/ready/blocked), and how the pipeline chips visualize progress
