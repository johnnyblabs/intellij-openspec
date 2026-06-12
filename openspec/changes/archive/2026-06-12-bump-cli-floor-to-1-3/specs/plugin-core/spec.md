## ADDED Requirements

### Requirement: CLI version floor notification

The plugin SHALL show a one-time notification when an OpenSpec project is opened and the detected CLI version is older than the supported floor of 1.3.0. The notification SHALL identify the detected version, explain that some CLI-dependent features may not work, and suggest the upgrade command. Users SHALL be able to dismiss the notification permanently via the standard "Don't show again" affordance.

#### Scenario: Old CLI detected at project open
- **WHEN** an OpenSpec project is opened, CLI detection succeeds with a version string older than `1.3.0` (e.g., `1.0.0`, `1.1.5`, `1.2.99`)
- **THEN** the plugin SHALL show a notification with title "OpenSpec CLI is older than 1.3.0" and a body including the detected version and the command `npm i -g @fission-ai/openspec@latest`

#### Scenario: Supported CLI detected at project open
- **WHEN** an OpenSpec project is opened and CLI detection succeeds with a version `1.3.0` or newer
- **THEN** the plugin SHALL NOT show the floor notification

#### Scenario: No CLI detected at project open
- **WHEN** an OpenSpec project is opened and CLI detection finds no CLI
- **THEN** the plugin SHALL fall back to the existing CLI-missing notification path (no floor notification — the user has no CLI to upgrade)

#### Scenario: Notification fires once per project open, not per tool window activation
- **WHEN** the floor notification has already fired during the current project session and the user opens/closes the OpenSpec tool window multiple times
- **THEN** the plugin SHALL NOT re-fire the floor notification — the per-project-open `StartupDetection` hook is the sole trigger
