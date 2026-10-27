## MODIFIED Requirements

### Requirement: Client-owned state only

The preview SHALL reflect only client-owned state. It SHALL NOT display per-spec coverage, score, completion, or status (specs carry none); SHALL NOT present code-to-spec links; SHALL NOT persist a search index or any derived artifact into the OpenSpec tree; and SHALL NOT synthesize a post-archive "effective" spec and present it as spec truth. Change-owned state that the preview surfaces (a change's delta assembly, validation verdict, task progress, or completion) SHALL be sourced from the OpenSpec CLI rather than recomputed. The one exception is a change's validation when no CLI is available: the preview MAY then show the plugin's built-in check, labelled as the built-in check.

#### Scenario: No spec status or coverage shown
- **WHEN** a main spec is rendered
- **THEN** the preview SHALL NOT display any per-spec status, completion, or coverage indicator

#### Scenario: Change state comes from the CLI
- **WHEN** the preview surfaces a change's delta assembly, validation verdict, task progress, or completion
- **THEN** those values SHALL be sourced from the OpenSpec CLI, not hand-recomputed from files

#### Scenario: Built-in check only without the CLI
- **WHEN** no OpenSpec CLI is available and a change is selected
- **THEN** any validation shown SHALL be the plugin's built-in check, labelled as the built-in check and never as the CLI's verdict

## ADDED Requirements

### Requirement: Change validation line

When a change is selected and the OpenSpec CLI shows its deltas, the Change Deltas pane SHALL show that change's validation verdict from the CLI under the header, with a link that runs the Validate action for that change. The verdict SHALL match what Validate reports for that change, SHALL be refreshed whenever a file under the OpenSpec directory changes, and SHALL NOT be stored. When the CLI rejects the change or returns no verdict, the line SHALL be left out.

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
- **WHEN** the CLI rejects the change, or its validate run fails or returns no verdict
- **THEN** the pane SHALL show no validation line

### Requirement: Delta operation legend

When the selected change has at least one delta, the Change Deltas pane SHALL show one line saying what archive does with each operation: ADDED adds the requirement to the main spec, MODIFIED replaces the whole requirement and so must keep its scenarios, REMOVED deletes it, and RENAMED renames it and keeps its text.

#### Scenario: A change with deltas
- **WHEN** the selected change has at least one delta
- **THEN** the pane SHALL show the legend once

#### Scenario: A change without deltas
- **WHEN** the selected change has no deltas
- **THEN** the pane SHALL NOT show the legend
