## MODIFIED Requirements

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

#### Scenario: Parse bullet-form REMOVED entries
- **WHEN** a `## REMOVED Requirements` section lists requirements as bullets carrying a requirement header — any one CommonMark bullet marker (`-`, `*`, `+`) followed by `### Requirement: <name>`, optionally wrapped in backticks (e.g. ``* `### Requirement: Works` ``)
- **THEN** the service SHALL produce a REMOVED operation for each such entry, named by the requirement name with the backticks and `### Requirement:` prefix stripped, matching the OpenSpec client, which applies these removals on archive

#### Scenario: Parse RENAMED section
- **WHEN** a delta spec contains `## RENAMED Requirements` with FROM:/TO: entries, each line optionally prefixed by any one CommonMark bullet marker (`-`, `*`, `+`)
- **THEN** the service SHALL produce a RENAMED operation for each entry with the old and new requirement names

#### Scenario: RENAMED names written in the client's canonical form are normalized
- **WHEN** a RENAMED entry is written in the OpenSpec client's canonical form, carrying the requirement header in the name (e.g. ``- FROM: `### Requirement: Old` `` / ``- TO: `### Requirement: New` ``)
- **THEN** the service SHALL strip the optional backticks and the `### Requirement:` prefix so the operation's old and new names are the bare requirement names (`Old`, `New`), and applying it SHALL rename the matching main-spec requirement rather than reporting it not found
- **AND** a RENAMED entry written with bare names (`FROM: Old` / `TO: New`) SHALL continue to parse to the same bare names

#### Scenario: No delta sections
- **WHEN** a change has no delta spec sections in any spec file
- **THEN** the service SHALL return an empty operation list
