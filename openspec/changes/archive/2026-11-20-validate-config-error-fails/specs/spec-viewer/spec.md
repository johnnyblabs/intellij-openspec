## MODIFIED Requirements

### Requirement: Change validation line

When a change is selected and the OpenSpec CLI shows its deltas, the Change Deltas pane SHALL show that change's validation verdict from the CLI under the header, with a link that runs the Validate action for that change. The verdict SHALL match what Validate reports for that change, SHALL be refreshed whenever a file under the OpenSpec directory changes, and SHALL NOT be stored. When the CLI rejects the change or returns no verdict, the line SHALL be left out. A configuration error response (`unparseable_config` or `unreadable_config`) is a verdict: Validate reports it as a failure, so the line SHALL show that failure.

#### Scenario: A valid change
- **WHEN** a selected change validates with no errors
- **THEN** the pane SHALL say the change passed Validate

#### Scenario: A change with errors
- **WHEN** a selected change fails validation
- **THEN** the pane SHALL show the number of errors and warnings Validate reports

#### Scenario: The link runs Validate for the change
- **WHEN** the user activates the validation line's link
- **THEN** the plugin SHALL run Validate for that change only, reporting where Validate always reports

#### Scenario: The verdict follows the files
- **WHEN** a file in the change or a main spec changes after the verdict was shown
- **THEN** selecting the change again SHALL show a verdict from a new validation run

#### Scenario: No CLI
- **WHEN** no OpenSpec CLI is available and a change is selected
- **THEN** the pane SHALL show the built-in check's result labelled "Built-in check"

#### Scenario: The CLI can't answer
- **WHEN** the CLI rejects the change, or its validate run fails or returns no verdict other than a configuration error response
- **THEN** the pane SHALL show no validation line

#### Scenario: A broken config.yaml
- **WHEN** a change is selected and the CLI's validate run for it answers with a configuration error response
- **THEN** the pane SHALL show the failure Validate reports for that run: one error and no warnings
