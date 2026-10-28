## MODIFIED Requirements

### Requirement: Delta spec parsing

The plugin SHALL parse every delta spec file named `spec.md` at any depth under a change's `specs/` folder, identifying each by its capability path (the folder's path relative to `specs/`, with `/` separators, such as `user-auth` or `identity/user-auth`), extracting sections headed by `## ADDED Requirements`, `## MODIFIED Requirements`, `## REMOVED Requirements`, and `## RENAMED Requirements` (titles matched case-insensitively, repeated sections all read) into structured operation objects, recognizing entries exactly as OpenSpec's archive does.

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

## ADDED Requirements

### Requirement: Nested capabilities sync to the matching main spec path

The plugin SHALL merge a nested capability's delta into the main spec at the same relative path under `openspec/specs/`, following OpenSpec's archive, so syncing then archiving a change yields the same main specs as `openspec archive`.

#### Scenario: Update an existing nested main spec
- **WHEN** a change's `specs/identity/sessions/spec.md` adds a requirement and `openspec/specs/identity/sessions/spec.md` exists
- **THEN** sync SHALL append the requirement to `openspec/specs/identity/sessions/spec.md` and leave every other main spec untouched

#### Scenario: Create a new nested main spec
- **WHEN** a change's `specs/identity/user-auth/spec.md` adds requirements and no main spec exists at that path
- **THEN** sync SHALL create `openspec/specs/identity/user-auth/spec.md`, including missing parent folders, titled `# identity/user-auth Specification`, with OpenSpec's new-capability Purpose and the added requirements, byte-identical to what `openspec archive` writes for the same change
