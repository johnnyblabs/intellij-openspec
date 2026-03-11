## ADDED Requirements

### Requirement: Hero button styling

The Generate All button SHALL be styled as the panel's primary action with a gradient background, bold font, an execute icon, and a remaining artifact count in the label.

#### Scenario: Button appearance when visible
- **WHEN** the Generate All button is visible (Direct API configured, 2+ artifacts remaining)
- **THEN** the button SHALL display an execute icon (AllIcons.Actions.Execute), bold text reading "Generate All (N)" where N is the remaining artifact count, and use IntelliJ's gradient button style

#### Scenario: Button hidden states
- **WHEN** Direct API is not configured or fewer than 2 artifacts remain
- **THEN** the Generate All button SHALL NOT be visible, identical to current behavior

### Requirement: Animated progress bar

During a Generate All operation, the panel SHALL display a determinate progress bar beneath the pipeline chips showing completed vs. total artifacts.

#### Scenario: Progress bar appears on generation start
- **WHEN** a Generate All operation begins
- **THEN** a JProgressBar SHALL appear below the pipeline chips with value 0 and maximum set to the total number of artifacts to generate

#### Scenario: Progress bar advances on artifact completion
- **WHEN** an artifact completes during Generate All
- **THEN** the progress bar value SHALL increment by 1 and display string text "N of M artifacts"

#### Scenario: Progress bar hidden when not generating
- **WHEN** no Generate All operation is in progress
- **THEN** the progress bar SHALL NOT be visible

### Requirement: Elapsed time display

During a Generate All operation, the panel SHALL display a running elapsed time counter next to the progress bar.

#### Scenario: Timer starts on generation begin
- **WHEN** a Generate All operation begins
- **THEN** an elapsed time label SHALL appear showing "0s elapsed" and update every second

#### Scenario: Timer format for short durations
- **WHEN** elapsed time is under 60 seconds
- **THEN** the label SHALL display "Ns elapsed" (e.g., "12s elapsed")

#### Scenario: Timer format for longer durations
- **WHEN** elapsed time is 60 seconds or more
- **THEN** the label SHALL display "Nm Ns elapsed" (e.g., "1m 30s elapsed")

#### Scenario: Timer stops on completion
- **WHEN** a Generate All operation completes, is cancelled, or errors
- **THEN** the timer SHALL stop updating and the label SHALL show the final elapsed time for 3 seconds before hiding

### Requirement: Completion celebration

When all artifacts complete during Generate All, the panel SHALL display a brief visual celebration.

#### Scenario: All artifacts generated successfully
- **WHEN** a Generate All operation completes with all artifacts done
- **THEN** the progress bar SHALL turn green, all pipeline chips SHALL briefly flash with a bright green highlight for 300ms, and an inline success message "All artifacts generated" SHALL appear

#### Scenario: Celebration auto-dismisses
- **WHEN** the completion celebration is displayed
- **THEN** the progress bar and elapsed time label SHALL auto-hide after 3 seconds, leaving the normal all-done pipeline state with the inline success message

### Requirement: Error recovery UX

When an artifact fails during Generate All, the panel SHALL display an inline error state with a retry option.

#### Scenario: Artifact generation fails
- **WHEN** an API call or file write fails during Generate All
- **THEN** the failed artifact's pipeline chip SHALL display in red with an error icon (AllIcons.General.Error), the progress bar SHALL turn red and stop, and an inline error message SHALL appear identifying the failed artifact and error

#### Scenario: Retry after failure
- **WHEN** the error state is displayed with a "Retry" button
- **THEN** clicking Retry SHALL re-trigger Generate All, which SHALL skip already-completed artifacts and resume from the first non-done artifact

#### Scenario: Error state persists until action
- **WHEN** an error state is displayed
- **THEN** the error state SHALL persist until the user clicks Retry, manually refreshes, or navigates away from the change

### Requirement: First-click confirmation skipped

The Generate All button SHALL NOT display a confirmation dialog, relying instead on the button label's artifact count and the Cancel button for user control.

#### Scenario: Direct invocation
- **WHEN** the user clicks Generate All
- **THEN** generation SHALL begin immediately without a confirmation prompt
