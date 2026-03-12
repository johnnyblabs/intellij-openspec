## ADDED Requirements

### Requirement: Setup wizard dialog
The plugin SHALL provide a multi-step setup wizard implemented as a `DialogWrapper` with back/next/skip navigation. The wizard SHALL guide users through CLI detection, AI provider configuration, and project initialization.

#### Scenario: Wizard launches on first tool window open
- **WHEN** the user opens the OpenSpec tool window for the first time and `setupCompleted` setting is `false`
- **THEN** the setup wizard dialog SHALL appear

#### Scenario: Wizard does not re-launch after completion
- **WHEN** the user has completed or skipped the setup wizard (`setupCompleted` is `true`)
- **AND** the user opens the tool window again
- **THEN** the wizard SHALL NOT appear automatically

#### Scenario: Wizard re-launchable from toolbar
- **WHEN** the user clicks the setup wizard button in the toolbar
- **THEN** the wizard SHALL open regardless of the `setupCompleted` flag

#### Scenario: Wizard re-launchable from settings
- **WHEN** the user clicks "Run Setup Wizard" in Settings > Tools > OpenSpec
- **THEN** the wizard SHALL open regardless of the `setupCompleted` flag

### Requirement: Welcome step
The wizard SHALL display a welcome step as the first card, explaining what OpenSpec does and offering a "Let's get set up" action to proceed or a "Skip Setup" action to close the wizard.

#### Scenario: Welcome step displayed
- **WHEN** the wizard opens
- **THEN** the first step SHALL show a brief description of OpenSpec and two actions: "Let's get set up" (next) and "Skip Setup" (close)

#### Scenario: Skip setup from welcome
- **WHEN** the user clicks "Skip Setup" on the welcome step
- **THEN** the wizard SHALL close and `setupCompleted` SHALL be set to `true`

### Requirement: CLI detection step
The wizard SHALL include a CLI detection step that auto-detects the OpenSpec CLI and displays the result.

#### Scenario: CLI found
- **WHEN** the CLI detection step loads and the CLI is found on PATH
- **THEN** the step SHALL display the CLI path and version with a success indicator

#### Scenario: CLI not found
- **WHEN** the CLI detection step loads and the CLI is not found
- **THEN** the step SHALL display "Not found — built-in features will be used" with an option to enter a manual path and a "Skip" button to proceed without CLI

#### Scenario: Manual CLI path entry
- **WHEN** the user enters a manual CLI path in the detection step
- **THEN** the wizard SHALL validate the path and display the result

### Requirement: AI provider step
The wizard SHALL include an AI provider configuration step that detects installed AI tools and lets the user configure their preferred tool and delivery method.

#### Scenario: AI tools detected
- **WHEN** the AI provider step loads and one or more AI tools are detected
- **THEN** the step SHALL list detected tools and allow the user to select a preferred tool and delivery method

#### Scenario: No AI tools detected
- **WHEN** the AI provider step loads and no AI tools are detected
- **THEN** the step SHALL show delivery method options (clipboard, editor tab, Direct API) without a tool selector

#### Scenario: Direct API configuration
- **WHEN** the user selects Direct API as their delivery method
- **THEN** the step SHALL show provider selection (Claude, OpenAI, Gemini), API key input, model selection, and a "Test Connection" button

#### Scenario: API key stored securely
- **WHEN** the user enters an API key and proceeds
- **THEN** the key SHALL be stored via `AiCredentialStore` (PasswordSafe), not in plain text settings

### Requirement: Project initialization step
The wizard SHALL include a project initialization step that offers to create the `openspec/` directory structure.

#### Scenario: Project not initialized
- **WHEN** the initialization step loads and `openspec/` does not exist
- **THEN** the step SHALL offer an "Initialize Project" button

#### Scenario: Project already initialized
- **WHEN** the initialization step loads and `openspec/` already exists
- **THEN** the step SHALL display "Already initialized" with a success indicator and auto-advance

#### Scenario: Successful initialization
- **WHEN** the user clicks "Initialize Project"
- **THEN** `ScaffoldingService.initOpenSpec()` SHALL be called and the result displayed

### Requirement: Completion step
The wizard SHALL display a completion step summarizing what was configured and offering a "Create Your First Change" action.

#### Scenario: Completion summary
- **WHEN** the user reaches the completion step
- **THEN** the step SHALL display a summary of configured items (CLI status, AI tool, delivery method, project init status)

#### Scenario: Create first change action
- **WHEN** the user clicks "Create Your First Change" on the completion step
- **THEN** the wizard SHALL close and the Propose Change dialog SHALL open

### Requirement: Setup wizard model
The wizard SHALL use a `SetupWizardModel` data class to manage state across steps. On completion, the model SHALL persist settings to `OpenSpecSettings` and `AiCredentialStore`.

#### Scenario: Model persists on completion
- **WHEN** the user completes the wizard
- **THEN** the model SHALL write CLI path to `OpenSpecSettings.cliPath`, preferred tool to `OpenSpecSettings.preferredTool`, delivery method to `OpenSpecSettings.preferredDeliveryMethod`, AI provider/model to `OpenSpecSettings`, and API key to `AiCredentialStore`
- **AND** `OpenSpecSettings.setupCompleted` SHALL be set to `true`
