# Delta — feedback-action

## ADDED Requirements

### Requirement: Send feedback via the CLI

The plugin SHALL provide a Send OpenSpec Feedback action that collects a non-empty message (and optional body) from the user and delegates to `openspec feedback <message> [--body <body>]` on a background thread, reporting the outcome through the standard notification surface. On failure the notification SHALL include the CLI's error output. The action SHALL be hidden when no OpenSpec CLI is detected.

#### Scenario: Successful submission
- **WHEN** the user submits a non-empty feedback message and the CLI exits successfully
- **THEN** the plugin SHALL show a success notification

#### Scenario: CLI failure
- **WHEN** the CLI invocation fails
- **THEN** the plugin SHALL show an error notification containing the CLI's error output

#### Scenario: Empty message rejected
- **WHEN** the user attempts to submit an empty message
- **THEN** the dialog SHALL block submission with inline validation and no CLI call SHALL occur

#### Scenario: CLI unavailable
- **WHEN** no OpenSpec CLI is detected
- **THEN** the action SHALL NOT be visible
