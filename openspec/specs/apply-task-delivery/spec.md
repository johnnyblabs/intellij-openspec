# Apply Task Delivery

## Purpose
Delivery-aware Apply action that assembles implementation context and delivers it to the user's selected AI tool.

## Requirements

### Requirement: Apply prompt assembly
The system SHALL assemble a full-context implementation prompt containing the change name, design document, specification files, and task list with completion status when the Apply action is triggered.

#### Scenario: Prompt includes design and specs context
- **WHEN** the user triggers Apply for a change with completed design.md and spec files
- **THEN** the assembled prompt SHALL include the full content of design.md and all spec files under the change's specs directory

#### Scenario: Prompt includes task list with completion markers
- **WHEN** the user triggers Apply for a change with tasks.md containing completed and pending tasks
- **THEN** the assembled prompt SHALL include the full tasks.md content with `- [x]` and `- [ ]` markers preserved
- **AND** SHALL include an instruction to start from the first incomplete task and work through remaining tasks in order

#### Scenario: Prompt includes change name header
- **WHEN** the user triggers Apply
- **THEN** the assembled prompt SHALL include a `# Change: <name>` header at the top

#### Scenario: Prompt includes save-path hint for CLI tools
- **WHEN** the user triggers Apply with a CLI-based tool selected
- **THEN** the assembled prompt SHALL include a save-path hint for the tasks.md file

### Requirement: Apply delivery via tool selector
The system SHALL deliver the assembled Apply prompt using the same delivery mechanism as Generate — clipboard, editor tab, or Direct API — based on the user's tool selector choice.

#### Scenario: Clipboard delivery
- **WHEN** the user triggers Apply with a clipboard-based tool selected
- **THEN** the system SHALL copy the assembled prompt to the clipboard
- **AND** show a notification confirming the copy

#### Scenario: IDE panel clipboard Apply guidance
- **WHEN** the user triggers Apply with an IDE panel tool selected via clipboard
- **THEN** the guidance SHALL show the tool-specific paste action (e.g., "Open Copilot Chat and paste the prompt")
- **AND** SHALL show "Save tasks.md when the tool finishes working through the tasks"

#### Scenario: CLI tool clipboard Apply guidance
- **WHEN** the user triggers Apply with a CLI tool selected via clipboard
- **THEN** the guidance SHALL show "Paste into <tool> — watching tasks.md for progress..."

#### Scenario: Editor tab delivery
- **WHEN** the user triggers Apply with Editor Tab selected
- **THEN** the system SHALL open the assembled prompt in a new editor tab

#### Scenario: Direct API delivery
- **WHEN** the user triggers Apply with Direct API selected
- **THEN** the system SHALL send the assembled prompt to the configured AI provider

### Requirement: Task progress tracking
The system SHALL track task completion by watching tasks.md for checkbox changes after an Apply delivery.

#### Scenario: File watcher detects task completion
- **WHEN** the tasks.md file is modified after an Apply delivery
- **THEN** the system SHALL re-parse the file and update the task progress display

#### Scenario: Manual progress check
- **WHEN** the user clicks "Check progress" after an Apply delivery
- **THEN** the system SHALL re-parse tasks.md and update the task progress display

#### Scenario: All tasks complete
- **WHEN** all tasks in tasks.md are marked `- [x]`
- **THEN** the system SHALL show "All tasks complete" with guidance to archive the change
