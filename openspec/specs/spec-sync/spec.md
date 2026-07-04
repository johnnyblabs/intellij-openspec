# Spec Sync

## Purpose
Parsing, applying, and previewing delta spec merges into main specs.

## Requirements

### Requirement: Delta spec parsing

The plugin SHALL parse delta spec files from a change's `specs/*/spec.md` directories, extracting sections headed by `## ADDED Requirements`, `## MODIFIED Requirements`, `## REMOVED Requirements`, and `## RENAMED Requirements` into structured operation objects.

#### Scenario: Parse ADDED section
- **WHEN** a delta spec contains `## ADDED Requirements` with one or more `### Requirement:` blocks
- **THEN** the service SHALL produce an ADDED operation for each requirement block, capturing the full content from heading through all scenarios

#### Scenario: Parse MODIFIED section
- **WHEN** a delta spec contains `## MODIFIED Requirements` with one or more `### Requirement:` blocks
- **THEN** the service SHALL produce a MODIFIED operation for each requirement block with the updated content

#### Scenario: Parse REMOVED section
- **WHEN** a delta spec contains `## REMOVED Requirements` with one or more `### Requirement:` blocks
- **THEN** the service SHALL produce a REMOVED operation for each requirement, capturing the Reason and Migration fields

#### Scenario: Parse RENAMED section
- **WHEN** a delta spec contains `## RENAMED Requirements` with FROM:/TO: entries
- **THEN** the service SHALL produce a RENAMED operation for each entry with the old and new requirement names

#### Scenario: No delta sections
- **WHEN** a change has no delta spec sections in any spec file
- **THEN** the service SHALL return an empty operation list

### Requirement: Spec sync application

The plugin SHALL apply delta spec operations to main spec files under `openspec/specs/<capability>/spec.md` in a defined order matching upstream OpenSpec CLI: RENAMED, REMOVED, MODIFIED, then ADDED. Requirement-header matching SHALL treat the `### Requirement:` header token case-insensitively (matching OpenSpec CLI 1.4+ parsing), with requirement-name matching keeping its existing semantics; headers the service writes or rewrites SHALL use the canonical `### Requirement:` casing. File content SHALL be written via plain filesystem I/O on the calling thread. VFS refresh SHALL be performed within `WriteAction` on the EDT via `invokeLater` with a `CountDownLatch` to synchronize the background thread. The background thread SHALL wait on the latch before proceeding to post-merge validation. After applying all operations, the plugin SHALL run `BuiltInValidator.validateSpecFile()` on each affected main spec file and report any validation errors as warnings to the user. In strict mode, validation errors on merged specs SHALL block the sync.

#### Scenario: Apply ADDED operation
- **WHEN** an ADDED operation targets a capability
- **THEN** the service SHALL append the requirement block to the main spec file, creating the file with a standard header if it does not exist

#### Scenario: Apply MODIFIED operation
- **WHEN** a MODIFIED operation targets a requirement name that exists in the main spec
- **THEN** the service SHALL replace the entire requirement block (from `### Requirement:` header through all scenarios) with the updated content

#### Scenario: Apply operation to a requirement with non-canonical header casing
- **WHEN** a MODIFIED, REMOVED, or RENAMED operation targets a requirement whose main-spec header is written with non-canonical casing of the header token (e.g. `### requirement: Name`)
- **THEN** the service SHALL locate and operate on that requirement block exactly as if the header used canonical casing, and any header it rewrites SHALL be emitted with canonical `### Requirement:` casing

#### Scenario: Apply MODIFIED with unmatched name in lenient mode
- **WHEN** a MODIFIED operation targets a requirement name not found in the main spec and strict validation is disabled
- **THEN** the service SHALL report a warning and skip the operation

#### Scenario: Apply MODIFIED with unmatched name in strict mode
- **WHEN** a MODIFIED operation targets a requirement name not found in the main spec and strict validation is enabled
- **THEN** the service SHALL report an ERROR and block the sync for that capability

#### Scenario: Apply REMOVED operation
- **WHEN** a REMOVED operation targets a requirement name that exists in the main spec
- **THEN** the service SHALL remove the entire requirement block from the main spec

#### Scenario: Apply RENAMED operation
- **WHEN** a RENAMED operation has FROM and TO names
- **THEN** the service SHALL update the `### Requirement:` header text from the old name to the new name

#### Scenario: Apply to new capability
- **WHEN** an ADDED operation targets a capability with no existing main spec file
- **THEN** the service SHALL create `openspec/specs/<capability>/spec.md` with a purpose header and the added requirements

#### Scenario: Post-merge validation reports issues
- **WHEN** delta operations are applied and the merged main spec has validation errors
- **THEN** the service SHALL report the validation errors as warnings to the user via notification

#### Scenario: VFS refresh uses invokeLater with latch
- **WHEN** the service writes a spec file and needs VFS refresh
- **THEN** the VFS refresh SHALL be dispatched via `invokeLater` wrapping `WriteAction.run`, with a `CountDownLatch` that the background thread awaits before proceeding to validation

#### Scenario: Apply order matches upstream
- **WHEN** a delta contains a mix of RENAMED, REMOVED, MODIFIED, and ADDED operations targeting the same capability
- **THEN** the service SHALL apply them in the order RENAMED → REMOVED → MODIFIED → ADDED (mirroring `@fission-ai/openspec`'s `specs-apply.js` precedence), so that a RENAMED FROM-name collision with a REMOVED block is resolved by the rename happening first

### Requirement: Sync preview

The plugin SHALL compute a preview of all spec changes before applying them, showing the before and after content for each affected main spec file.

#### Scenario: Compute preview
- **WHEN** the user triggers Sync Specs for a change
- **THEN** the service SHALL return a list of sync results, each containing the capability name, the current main spec content, and the projected content after applying all operations

#### Scenario: Display diff
- **WHEN** sync results are available
- **THEN** the plugin SHALL display each affected spec in an IntelliJ diff viewer with side-by-side before/after panels

#### Scenario: Confirm and write
- **WHEN** the user confirms the sync preview
- **THEN** the plugin SHALL write spec file content via `Files.writeString()` on the background thread, then refresh the virtual file system within `WriteAction` on the EDT

### Requirement: Post-merge spec validation

The plugin SHALL validate each affected main spec file after applying delta spec operations by running `BuiltInValidator.validateSpecFile()` on the merged content. The validation results SHALL distinguish between pre-existing issues in the main spec and issues introduced by the sync by comparing pre-merge and post-merge validation results.

#### Scenario: Clean merge
- **WHEN** delta operations are applied and the merged main spec passes validation
- **THEN** the service SHALL report the sync as successful with no validation warnings

#### Scenario: Merge introduces new issues
- **WHEN** delta operations are applied and the merged main spec has validation errors that did not exist in the pre-merge version
- **THEN** the service SHALL report only the newly introduced errors, not pre-existing ones

#### Scenario: Pre-existing issues unchanged
- **WHEN** the main spec had validation errors before the merge and the merge does not fix or worsen them
- **THEN** the service SHALL NOT report the pre-existing errors as sync-related issues
