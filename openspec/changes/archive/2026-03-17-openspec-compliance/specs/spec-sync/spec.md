## MODIFIED Requirements

### Requirement: Spec sync application

The plugin SHALL apply delta spec operations to main spec files under `openspec/specs/<capability>/spec.md` in a defined order: REMOVED, RENAMED, MODIFIED, then ADDED. After applying all operations, the plugin SHALL run `BuiltInValidator.validateSpecFile()` on each affected main spec file and report any validation errors as warnings to the user. In strict mode, validation errors on merged specs SHALL block the sync.

#### Scenario: Apply ADDED operation
- **WHEN** an ADDED operation targets a capability
- **THEN** the service SHALL append the requirement block to the main spec file, creating the file with a standard header if it does not exist

#### Scenario: Apply MODIFIED operation
- **WHEN** a MODIFIED operation targets a requirement name that exists in the main spec
- **THEN** the service SHALL replace the entire requirement block (from `### Requirement:` header through all scenarios) with the updated content

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

## ADDED Requirements

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
