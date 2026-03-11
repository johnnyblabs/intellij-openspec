# Generate All

## Purpose
Orchestrated sequential generation of all remaining artifacts via Direct API with progress, cancellation, and error recovery.

## Requirements

### Requirement: Generate All orchestration

The plugin SHALL provide a "Generate All" capability that sequentially generates all remaining artifacts for a change via the Direct API in dependency order.

#### Scenario: All remaining artifacts are generated
- WHEN the user triggers Generate All on a change with multiple remaining artifacts
- THEN the system SHALL generate each artifact in dependency order (proposal → design → specs → tasks), writing each to disk before starting the next

#### Scenario: Already-complete artifacts are skipped
- WHEN the user triggers Generate All on a change where some artifacts are already done
- THEN the system SHALL skip completed artifacts and only generate those with status READY or BLOCKED-but-will-become-ready

#### Scenario: DAG is re-evaluated between artifacts
- WHEN an artifact generation completes during a Generate All chain
- THEN the system SHALL re-read the artifact DAG status before selecting the next artifact, ensuring dependency state is current

### Requirement: Generate All progress reporting

The plugin SHALL report progress to the UI during a Generate All operation, including support for a GENERATING artifact state.

#### Scenario: Artifact generation starts
- **WHEN** the system begins generating an artifact during a Generate All chain
- **THEN** the system SHALL notify listeners with the artifact ID, current index, and total count

#### Scenario: Artifact generation completes
- **WHEN** an artifact is successfully generated and written to disk
- **THEN** the system SHALL notify listeners that the artifact is complete

#### Scenario: All artifacts complete
- **WHEN** the last artifact in the chain is successfully generated
- **THEN** the system SHALL notify listeners that the full generation is complete

### Requirement: Generate All cancellation

The plugin SHALL support cancelling a Generate All operation between artifacts.

#### Scenario: User cancels mid-chain
- WHEN the user requests cancellation during a Generate All operation
- THEN the system SHALL finish the current in-progress artifact generation, then stop the chain without starting the next artifact

#### Scenario: Completed artifacts are preserved on cancel
- WHEN a Generate All operation is cancelled
- THEN all artifacts that were successfully generated before cancellation SHALL remain written to disk

### Requirement: Generate All error handling

The plugin SHALL handle errors during Generate All gracefully.

#### Scenario: API error stops the chain
- WHEN an API call fails during a Generate All operation
- THEN the system SHALL stop the chain, notify listeners with the failed artifact ID and error, and preserve all previously completed artifacts

#### Scenario: File write error stops the chain
- WHEN writing a generated artifact to disk fails
- THEN the system SHALL stop the chain and notify listeners with the error details
