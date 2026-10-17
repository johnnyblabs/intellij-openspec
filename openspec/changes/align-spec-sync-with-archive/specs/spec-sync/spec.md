## MODIFIED Requirements

### Requirement: Delta spec parsing

The plugin SHALL parse delta spec files from a change's `specs/*/spec.md` directories, extracting sections headed by `## ADDED Requirements`, `## MODIFIED Requirements`, `## REMOVED Requirements`, and `## RENAMED Requirements` (titles matched case-insensitively, repeated sections all read) into structured operation objects, recognizing entries exactly as OpenSpec's archive does.

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

### Requirement: Spec sync application

The plugin SHALL apply delta spec operations to main spec files under `openspec/specs/<capability>/spec.md` following OpenSpec's archive rules: in the order RENAMED, REMOVED, MODIFIED, then ADDED, matching requirement names exactly after normalization (a trailing closing-`#` run stripped, then trimmed; no case folding), and treating the `### Requirement:` header token case-insensitively. A change SHALL be applied all-or-nothing. File content SHALL be written with plain filesystem I/O on the calling thread, and the VFS refresh SHALL be handed to the EDT with `invokeAndWait` inside `WriteAction`, carrying the caller's modality. After writing, the plugin SHALL validate each affected main spec and report errors as warnings.

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

## ADDED Requirements

### Requirement: Fenced content is never parsed as spec structure

When parsing delta specs and main specs, the plugin SHALL ignore everything inside fenced code blocks, as OpenSpec's archive does: a fence opens with three or more backticks or tildes at any indentation, and closes with the same character, at least as long, alone on its line. An unclosed fence SHALL run to the end of the file.

#### Scenario: A fenced example in a delta adds nothing
- **WHEN** a delta requirement contains a fenced example that includes `## ADDED Requirements` and `### Requirement: Example`
- **THEN** the merge SHALL treat the fence as part of the requirement's text and SHALL NOT produce an operation for the fenced lines

#### Scenario: A fenced heading in a main requirement doesn't end the block
- **WHEN** a main-spec requirement contains a fenced example with `## Notes` or `### Requirement:` lines, and a delta modifies another requirement
- **THEN** the merged spec SHALL keep that requirement block and its fence intact

### Requirement: Sync refuses what OpenSpec archive refuses

The plugin SHALL refuse to apply a change that OpenSpec's archive would refuse, writing nothing for it and showing why in OpenSpec's wording. The sync preview SHALL show the refusal and not offer to apply the change, and Bulk Archive SHALL block it.

#### Scenario: Case or spacing near-miss
- **WHEN** an ADDED name, a REMOVED name, or a RENAMED target differs from an existing requirement only in case or spacing
- **THEN** the change SHALL be refused, naming the existing requirement

#### Scenario: A MODIFIED block drops a scenario
- **WHEN** a MODIFIED block omits a scenario the current requirement has
- **THEN** the change SHALL be refused, naming the missing scenario(s)

#### Scenario: Unpaired or conflicting RENAMED
- **WHEN** a RENAMED `FROM:` has no `TO:`, its source requirement is missing, or its target name already exists
- **THEN** the change SHALL be refused

#### Scenario: ADDED name already exists with different content
- **WHEN** an ADDED requirement's name exists exactly and its content differs
- **THEN** the change SHALL be refused; when the content is identical the operation SHALL be a no-op
