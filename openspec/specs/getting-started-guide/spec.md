# Getting Started Guide

## Purpose
User-facing onboarding documentation covering plugin settings and a complete worked example with GitHub Copilot.

## Requirements

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

### Requirement: NO_CHANGES empty state includes educational content
The GettingStartedPanel NO_CHANGES state SHALL include a brief explanation of what a change is and how to scope one, in addition to the "Propose a Change" button.

#### Scenario: Change concept explained
- **WHEN** the GettingStartedPanel displays the NO_CHANGES state
- **THEN** the description text SHALL explain that a change represents a scoped unit of work with a name, motivation, and list of what will be built or modified

#### Scenario: Scoping guidance provided
- **WHEN** the GettingStartedPanel displays the NO_CHANGES state
- **THEN** the description text SHALL include a brief tip on how to scope a change (e.g., "Keep it focused — one feature or fix per change")

### Requirement: GettingStartedPanel reacts to external filesystem changes

The GettingStartedPanel SHALL monitor the `openspec/` directory for filesystem changes and automatically re-evaluate its state when changes are detected.

#### Scenario: External change creation triggers transition
- **GIVEN** the GettingStartedPanel is displayed in the `NO_CHANGES` state
- **WHEN** a change directory is created externally (e.g., via CLI or AI tool)
- **THEN** the panel SHALL detect the new state and transition to the normal tree view

#### Scenario: External initialization triggers panel update
- **GIVEN** the GettingStartedPanel is displayed in the `NOT_INITIALIZED` state
- **WHEN** the `openspec/` directory is created externally
- **THEN** the panel SHALL rebuild to show the next step (AI configuration or propose)

#### Scenario: Listener is cleaned up on transition
- **GIVEN** the GettingStartedPanel has an active filesystem listener
- **WHEN** the panel transitions to the normal tree view
- **THEN** the listener SHALL be disposed and no longer process events

### Requirement: Branded onboarding visuals

The plugin's onboarding screens SHALL display a prominent OpenSpec brand icon and tagline for a polished first impression.

#### Scenario: Getting started panel shows brand icon
- **WHEN** the GettingStartedPanel displays a welcome state (NOT_INITIALIZED, NO_AI_CONFIGURED, or NO_CHANGES)
- **THEN** it SHALL display the 32x32 OpenSpec brand icon above the title

#### Scenario: Getting started panel shows tagline
- **WHEN** the GettingStartedPanel displays a welcome state
- **THEN** it SHALL display "Spec-Driven Development" as a subtitle below the "OpenSpec" title in a smaller gray font

#### Scenario: Setup wizard shows brand icon
- **WHEN** the SetupWizardDialog displays the welcome step or the done step
- **THEN** it SHALL display the 32x32 OpenSpec brand icon

#### Scenario: Brand icon has dark theme variant
- **WHEN** the IDE is using a dark theme
- **THEN** the brand icon SHALL use the dark variant (`openspec-brand_dark.svg`) automatically via IntelliJ's icon convention
