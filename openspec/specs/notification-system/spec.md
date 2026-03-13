# Notification System

## Purpose
Defines how the OpenSpec plugin delivers notifications to users, including categorized groups, titled messages, contextual actions, and collapsed summaries for bulk operations.

### Requirement: Categorized notification groups
The plugin SHALL register multiple notification groups in `plugin.xml` organized by concern so users can configure notification behavior per category.

#### Scenario: Notification groups registered
- **WHEN** the plugin is loaded
- **THEN** the following notification groups SHALL be registered: `OpenSpec.Workflow`, `OpenSpec.Generation`, `OpenSpec.Validation`, `OpenSpec.System`, `OpenSpec.Tracker`

#### Scenario: System group uses sticky balloons
- **WHEN** a notification is fired through the `OpenSpec.System` group
- **THEN** it SHALL display as a `STICKY_BALLOON` that persists until the user dismisses it

#### Scenario: Non-system groups use standard balloons
- **WHEN** a notification is fired through `OpenSpec.Workflow`, `OpenSpec.Generation`, `OpenSpec.Validation`, or `OpenSpec.Tracker`
- **THEN** it SHALL display as a `BALLOON` that auto-dismisses after the default timeout

### Requirement: Titled notifications
Every notification SHALL include a title identifying the operation so users can scan balloon headers without reading body text.

#### Scenario: Workflow action notification has title
- **WHEN** a propose, apply, or archive action fires a notification
- **THEN** the notification SHALL include a title matching the operation name (e.g., "Propose", "Apply", "Archive")

#### Scenario: Generation notification has title
- **WHEN** an artifact generation fires a notification
- **THEN** the notification SHALL include the title "Generate"

#### Scenario: Validation notification has title
- **WHEN** validation completes and fires a notification
- **THEN** the notification SHALL include the title "Validate"

#### Scenario: System notification has title
- **WHEN** a CLI detection or API failure fires a notification
- **THEN** the notification SHALL include a descriptive title (e.g., "CLI Detection", "API Error")

### Requirement: Notification actions
Notifications SHALL include contextual action links where applicable.

#### Scenario: Generation success includes Open File action
- **WHEN** an artifact is successfully generated and a notification is shown
- **THEN** the notification SHALL include an "Open File" action that opens the generated artifact in the editor

#### Scenario: CLI missing includes Open Settings action
- **WHEN** the CLI-missing notification is shown
- **THEN** the notification SHALL include an "Open Settings" action that opens the OpenSpec settings panel

#### Scenario: API failure includes Open Settings action
- **WHEN** an API failure notification is shown
- **THEN** the notification SHALL include an "Open Settings" action that opens the OpenSpec settings panel

### Requirement: Generate All notification collapse
During a Generate All operation, the system SHALL suppress per-artifact notifications and display a single summary notification on completion.

#### Scenario: Per-artifact notifications suppressed
- **WHEN** a Generate All operation is in progress
- **THEN** individual artifact completion notifications SHALL NOT be displayed as balloons

#### Scenario: Summary notification on completion
- **WHEN** a Generate All operation completes successfully
- **THEN** the system SHALL display a single notification with title "Generate All" and content summarizing the count of artifacts generated and elapsed time

#### Scenario: Error notification fires immediately
- **WHEN** a Generate All operation fails on an artifact
- **THEN** the error notification SHALL fire immediately (not collapsed) with the failed artifact name and error details

### Requirement: Backward-compatible convenience API
The `OpenSpecNotifier` class SHALL maintain its existing `info(project, content)`, `warn(project, content)`, and `error(project, content)` convenience methods so existing call sites work without modification during migration.

#### Scenario: Legacy info call works
- **WHEN** code calls `OpenSpecNotifier.info(project, "message")`
- **THEN** a notification SHALL be created in the `OpenSpec.Workflow` group with the given message and no title

#### Scenario: New titled API available
- **WHEN** code calls `OpenSpecNotifier.info(project, "title", "message")`
- **THEN** a notification SHALL be created with the specified title and message in the `OpenSpec.Workflow` group

#### Scenario: Group-specific API available
- **WHEN** code calls `OpenSpecNotifier.notify(project, group, title, content, type)`
- **THEN** the notification SHALL be created in the specified group with the given title, content, and type

### Requirement: Structured API error notifications

API error notifications SHALL display a human-readable error message with an actionable suggestion instead of raw HTTP response bodies. The `AiApiException` SHALL carry structured fields (HTTP status code, provider name, suggestion) so callers can format context-appropriate messages.

#### Scenario: Authentication error suggests checking API key
- **WHEN** the AI provider returns HTTP 401 or 403
- **THEN** the error notification SHALL include the suggestion "Check your API key in Settings → Tools → OpenSpec"
- **AND** the notification SHALL include an "Open Settings" action

#### Scenario: Rate limit error suggests waiting
- **WHEN** the AI provider returns HTTP 429
- **THEN** the error notification SHALL include the suggestion "Rate limited — wait a moment and retry"

#### Scenario: Server error suggests checking provider status
- **WHEN** the AI provider returns HTTP 500 or higher
- **THEN** the error notification SHALL include the suggestion "The provider may be experiencing issues — try again shortly"

#### Scenario: Error message extracted from provider JSON
- **WHEN** the AI provider returns an error with a JSON body containing an error message field
- **THEN** the notification SHALL display the extracted message instead of the raw JSON body

#### Scenario: Fallback for unparseable error responses
- **WHEN** the AI provider returns an error with a body that cannot be parsed as JSON
- **THEN** the notification SHALL display a truncated version of the raw body (max 200 characters)

#### Scenario: Full error details logged for debugging
- **WHEN** an API error occurs
- **THEN** the full HTTP status code and raw response body SHALL be logged at WARN level
