## ADDED Requirements

### Requirement: Scaffolding Content Detection

The ScaffoldingDetectionService SHALL determine whether an artifact file contains scaffolding placeholder content or real authored/generated content.

#### Scenario: File with only HTML comment placeholders
- **WHEN** an artifact file contains markdown headings and HTML comments but no substantive content
- **THEN** the service SHALL report the file as scaffolding

#### Scenario: File with placeholder task items
- **WHEN** a tasks file contains only generic placeholders (e.g., "Task 1", "Task 2", "Write unit tests")
- **THEN** the service SHALL report the file as scaffolding

#### Scenario: File with real content
- **WHEN** an artifact file contains substantive text beyond headings and comments (more than 20 non-whitespace characters of real content)
- **THEN** the service SHALL report the file as NOT scaffolding

#### Scenario: File does not exist
- **WHEN** the artifact file path does not exist
- **THEN** the service SHALL report the file as NOT scaffolding (file absence is handled by CLI status)

### Requirement: Artifact Status Override

The ArtifactOrchestrationService SHALL override CLI-reported artifact status when scaffolding is detected.

#### Scenario: Done artifact with scaffolding and met dependencies
- **WHEN** the CLI reports an artifact as "done" AND the file contains scaffolding AND all of the artifact's dependencies are truly done (not scaffolding)
- **THEN** the service SHALL override the status to READY

#### Scenario: Done artifact with scaffolding and unmet dependencies
- **WHEN** the CLI reports an artifact as "done" AND the file contains scaffolding AND one or more dependencies are also scaffolding
- **THEN** the service SHALL override the status to BLOCKED with the scaffolding dependencies listed in missingDeps

#### Scenario: Done artifact with real content
- **WHEN** the CLI reports an artifact as "done" AND the file contains real content
- **THEN** the service SHALL keep the status as DONE

#### Scenario: DAG completeness recalculated
- **WHEN** any artifact status has been overridden from DONE
- **THEN** the DAG's isComplete flag SHALL be recalculated as false
