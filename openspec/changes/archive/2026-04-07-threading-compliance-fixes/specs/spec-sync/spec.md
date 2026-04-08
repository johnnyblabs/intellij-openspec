## MODIFIED Requirements

### Requirement: Spec sync application

The plugin SHALL apply delta spec operations to main spec files under `openspec/specs/<capability>/spec.md` in a defined order: REMOVED, RENAMED, MODIFIED, then ADDED. File content SHALL be written via plain filesystem I/O on the calling thread. VFS refresh SHALL be performed within `WriteAction` on the EDT.

#### Scenario: Apply ADDED operation
- **WHEN** an ADDED operation targets a capability
- **THEN** the service SHALL append the requirement block to the main spec file, creating the file with a standard header if it does not exist

#### Scenario: Apply MODIFIED operation
- **WHEN** a MODIFIED operation targets a requirement name that exists in the main spec
- **THEN** the service SHALL replace the entire requirement block (from `### Requirement:` header through all scenarios) with the updated content

#### Scenario: Apply MODIFIED with unmatched name
- **WHEN** a MODIFIED operation targets a requirement name not found in the main spec
- **THEN** the service SHALL report a warning and skip the operation

#### Scenario: Apply REMOVED operation
- **WHEN** a REMOVED operation targets a requirement name that exists in the main spec
- **THEN** the service SHALL remove the entire requirement block from the main spec

#### Scenario: Apply RENAMED operation
- **WHEN** a RENAMED operation has FROM and TO names
- **THEN** the service SHALL update the `### Requirement:` header text from the old name to the new name

#### Scenario: Apply to new capability
- **WHEN** an ADDED operation targets a capability with no existing main spec file
- **THEN** the service SHALL create `openspec/specs/<capability>/spec.md` with a purpose header and the added requirements

#### Scenario: Confirm and write
- **WHEN** the user confirms the sync preview
- **THEN** the plugin SHALL write spec file content via `Files.writeString()` on the background thread, then refresh the virtual file system within `WriteAction` on the EDT
