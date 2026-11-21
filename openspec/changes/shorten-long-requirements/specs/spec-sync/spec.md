## MODIFIED Requirements

### Requirement: Delta spec parsing

The plugin SHALL parse every delta `spec.md` at any depth under a change's `specs/` folder, identified by its capability path (relative to `specs/`, `/`-separated, such as `identity/user-auth`). It SHALL extract the ADDED, MODIFIED, REMOVED and RENAMED sections into structured operations, matching titles case-insensitively, reading repeated sections, and recognizing entries exactly as OpenSpec's archive does.

#### Scenario: Parse ADDED section
- **WHEN** a delta spec contains `## ADDED Requirements` with one or more `### Requirement:` blocks
- **THEN** the service SHALL produce an ADDED operation for each requirement block, capturing the full content from heading through all scenarios

#### Scenario: Parse MODIFIED section
- **WHEN** a delta spec contains `## MODIFIED Requirements` with one or more `### Requirement:` blocks
- **THEN** the service SHALL produce a MODIFIED operation for each requirement block with the updated content

#### Scenario: Parse REMOVED section
- **WHEN** a delta spec contains `## REMOVED Requirements` with one or more `### Requirement:` blocks
- **THEN** the service SHALL produce a REMOVED operation for each requirement, capturing the Reason and Migration fields

#### Scenario: Parse bullet-form REMOVED entries
- **WHEN** a `## REMOVED Requirements` section lists requirements as bullets carrying a requirement header — any one CommonMark bullet marker (`-`, `*`, `+`) followed by `### Requirement: <name>`, optionally wrapped in backticks (e.g. ``* `### Requirement: Works` ``)
- **THEN** the service SHALL produce a REMOVED operation for each such entry, named by the requirement name with the backticks and `### Requirement:` prefix stripped, matching the OpenSpec client, which applies these removals on archive

#### Scenario: Parse RENAMED section
- **WHEN** a delta spec contains `## RENAMED Requirements` with FROM:/TO: entries, each line optionally prefixed by any one CommonMark bullet marker (`-`, `*`, `+`)
- **THEN** the service SHALL produce a RENAMED operation for each FROM line followed by its TO line, with the old and new requirement names

#### Scenario: RENAMED names written in the client's canonical form are normalized
- **WHEN** a RENAMED entry is written in the OpenSpec client's canonical form, carrying the requirement header in the name (e.g. ``- FROM: `### Requirement: Old` `` / ``- TO: `### Requirement: New` ``)
- **THEN** the service SHALL strip the optional backticks and the `### Requirement:` prefix so the operation's old and new names are the bare requirement names (`Old`, `New`), and applying it SHALL rename the matching main-spec requirement rather than reporting it not found
- **AND** a RENAMED line written with bare names (`FROM: Old` / `TO: New`) SHALL NOT be read as a rename, as OpenSpec does not read it; a delta whose only entries are such lines has no operations, so the change is refused

#### Scenario: No delta sections
- **WHEN** a change has no delta spec sections in any spec file
- **THEN** the service SHALL return an empty operation list

#### Scenario: Nested capability delta is read
- **WHEN** a change has a delta spec at `specs/identity/user-auth/spec.md`
- **THEN** the service SHALL produce its operations for capability `identity/user-auth`, exactly as OpenSpec's archive keys it, and the change SHALL count as having delta specs

#### Scenario: Flat and nested deltas in one change
- **WHEN** a change has delta specs at `specs/flat/spec.md`, `specs/identity/sessions/spec.md` and `specs/identity/user-auth/spec.md`
- **THEN** the service SHALL read all three, ordered by capability path (`flat`, `identity/sessions`, `identity/user-auth`) as OpenSpec's archive lists them

#### Scenario: Dot-folders and symlinked folders are not read
- **WHEN** a change's `specs/` folder contains a dot-folder, a symlink to a folder outside the change, or a symlink back to one of its own ancestors
- **THEN** the service SHALL read none of them, as OpenSpec's archive reads none of them, and SHALL finish without following the loop

### Requirement: Spec sync application

The plugin SHALL apply delta operations to `openspec/specs/<capability>/spec.md` following OpenSpec's archive rules: in the order RENAMED, REMOVED, MODIFIED, then ADDED, with requirement names matched exactly after normalization (closing-`#` run stripped, then trimmed, no case folding) and the `### Requirement:` header token matched case-insensitively. A change SHALL be applied all-or-nothing, and each affected main spec SHALL be validated after writing, with its errors reported as warnings.

#### Scenario: Apply ADDED operation
- **WHEN** an ADDED operation targets a capability and no requirement with that name exists
- **THEN** the service SHALL append the requirement block after the existing requirements, creating the main spec with OpenSpec's new-capability skeleton if it does not exist

#### Scenario: Apply MODIFIED operation
- **WHEN** a MODIFIED operation targets a requirement name that exists exactly in the main spec, and the new block keeps every scenario the current block has
- **THEN** the service SHALL replace the entire requirement block (from `### Requirement:` header through all scenarios) with the updated content

#### Scenario: Apply operation to a requirement with non-canonical header casing
- **WHEN** a MODIFIED, REMOVED, or RENAMED operation targets a requirement whose main-spec header is written with non-canonical casing of the header token (e.g. `### requirement: Name`)
- **THEN** the service SHALL locate and operate on that requirement block exactly as if the header used canonical casing, and any header it rewrites SHALL be emitted with canonical `### Requirement:` casing

#### Scenario: Apply MODIFIED with unmatched name is an always-on guard
- **WHEN** a MODIFIED operation targets a requirement name not found exactly in the main spec
- **THEN** the service SHALL refuse the change with OpenSpec's message (`<capability> MODIFIED failed for header "### Requirement: <name>" - not found`) and SHALL write nothing for that change — always, with no dependency on any setting

#### Scenario: Apply REMOVED operation
- **WHEN** a REMOVED operation targets a requirement name that exists exactly in the main spec
- **THEN** the service SHALL remove the entire requirement block from the main spec; when no requirement by that name (or a case or spacing variant of it) exists, it SHALL treat it as already removed and report a warning

#### Scenario: Apply RENAMED operation
- **WHEN** a RENAMED operation has paired FROM and TO names, the FROM requirement exists, and no requirement named TO exists
- **THEN** the service SHALL update the `### Requirement:` header text from the old name to the new name, keeping the block in place

#### Scenario: Apply to new capability
- **WHEN** an ADDED operation targets a capability with no existing main spec file
- **THEN** the service SHALL create `openspec/specs/<capability>/spec.md` as `# <capability> Specification`, a `## Purpose` reading `TBD - created by archiving change <change>. Update Purpose after archive.` (or the delta's own `## Purpose` when it has one), and `## Requirements` followed by the added requirements

#### Scenario: Post-merge validation reports issues
- **WHEN** delta operations are applied and the merged main spec has validation errors
- **THEN** the service SHALL report the validation errors as warnings to the user via notification

#### Scenario: VFS refresh uses invokeLater with latch
- **WHEN** the service writes a spec file and needs a VFS refresh
- **THEN** the refresh SHALL be handed to the EDT with `invokeAndWait` wrapping `WriteAction.run` and the caller's modality, never an `invokeLater` plus a latch, so it neither stalls inside a modal dialog nor deadlocks when called on the EDT

#### Scenario: Apply order matches upstream
- **WHEN** a delta contains a mix of RENAMED, REMOVED, MODIFIED, and ADDED operations targeting the same capability
- **THEN** the service SHALL apply them in the order RENAMED → REMOVED → MODIFIED → ADDED (mirroring `@fission-ai/openspec`'s `specs-apply.js` precedence), so that a RENAMED FROM-name collision with a REMOVED block is resolved by the rename happening first

#### Scenario: Writes off the EDT
- **WHEN** a change is applied
- **THEN** file content SHALL be written with plain filesystem I/O on the calling thread
- **AND** the VFS refresh SHALL be handed to the EDT with `invokeAndWait` inside `WriteAction`, carrying the caller's modality
