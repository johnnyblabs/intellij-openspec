## ADDED Requirements

### Requirement: CLI JSON responses follow one reading contract

Every CLI command the plugin runs with `--json` SHALL have its output read the same way. The JSON payload SHALL be taken from stdout even when other text comes before it. A response that carries an error-severity entry in its top-level `status` array SHALL be treated as a failed run whatever its `code`, and its `message` and `fix` SHALL be kept for display. A failed run SHALL NOT be reported as an empty success.

#### Scenario: No-root envelope is a failure, not an empty result
- **WHEN** a `--json` command prints the captured 1.14.3 `no_openspec_root` response (`root: null`, an error `status` entry) and exits 1
- **THEN** the plugin SHALL report the run as failed with the CLI's message, and SHALL NOT present it as an empty list or a passing result

#### Scenario: Any error code is recognised
- **WHEN** the response's error entry has a code the plugin has never seen, such as `no_root_with_registered_stores`
- **THEN** the plugin SHALL still treat the run as failed and show its message

#### Scenario: Text before the JSON is tolerated
- **WHEN** stdout contains a line of text before a valid JSON payload
- **THEN** the plugin SHALL read the payload exactly as it would read the payload without that text

#### Scenario: A successful archive with text before its JSON
- **WHEN** `openspec archive --json` exits 0 and prints a line of text before its archive result
- **THEN** the plugin SHALL report the archive as completed, not as refused

#### Scenario: Non-JSON output on a non-zero exit is a failure
- **WHEN** a `--json` command exits non-zero and stdout holds no parseable JSON
- **THEN** the plugin SHALL report the run as failed, using stderr as the reason when it has text

### Requirement: Artifact status and instructions failures show the CLI's reason

When `openspec status --change` or `openspec instructions` is rejected by the CLI, the plugin SHALL show the reason from the response's `status` envelope. It SHALL NOT show an earlier cached status for a change the CLI now rejects, and SHALL NOT show a generic "empty response" message when the envelope carries a reason.

#### Scenario: Status for a change that no longer exists
- **WHEN** a change's status was shown earlier and `openspec status --change` now returns the captured 1.14.3 `change_error` response
- **THEN** the plugin SHALL report the CLI's message for that change and SHALL NOT keep showing the earlier artifact status as current

#### Scenario: Instructions rejected for an unknown artifact
- **WHEN** `openspec instructions` returns the captured 1.14.3 `change_error` response with empty stderr
- **THEN** the error shown to the user SHALL contain the envelope's message

#### Scenario: A failure to run keeps the previous status
- **WHEN** the CLI cannot be run at all (not found or timed out) for a change whose status was shown earlier
- **THEN** the plugin MAY keep showing the earlier status, because the CLI gave no answer about the change

### Requirement: CLI runs request plain output

Every CLI process the plugin starts SHALL run with colour and update notices turned off: `NO_COLOR=1`, `FORCE_COLOR=0`, `OPENSPEC_NO_UPDATE_CHECK=1`, together with the existing `OPENSPEC_TELEMETRY=0`. Text output the plugin shows to the user or matches with patterns SHALL have any remaining ANSI escape sequences removed first.

#### Scenario: Coloured output still parses
- **WHEN** `openspec update` output captured with colour codes contains the legacy-cleanup block
- **THEN** the plugin SHALL recognise the block and offer the same files as for the uncoloured capture

#### Scenario: Messages show no escape codes
- **WHEN** a CLI failure message containing ANSI escape sequences is shown to the user
- **THEN** the shown text SHALL contain no escape sequences
