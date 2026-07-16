## MODIFIED Requirements

### Requirement: Spec format validation

The plugin SHALL validate spec files for structural completeness: title heading, requirement blocks, RFC 2119 keywords, and scenario format. Requirement headers (`### Requirement:`) SHALL be recognized case-insensitively on the header token, matching OpenSpec CLI 1.4+ parsing. Requirement-keyword presence SHALL be satisfied only by `SHALL` or `MUST` as whole words (matching the CLI's rule on every supported generation — `SHOULD`/`MAY` do not satisfy it), SHALL be evaluated against the requirement body with fenced code blocks masked (matching CLI 1.6 semantics — a keyword appearing only inside a code fence does not satisfy the check), and a keyword appearing only in the requirement header SHALL produce a targeted diagnostic directing the author to move the keyword onto a body line, with a quick-fix offered. Scenario presence (`#### Scenario:`) SHALL likewise be evaluated with fenced code blocks masked. The inspection SHALL guard against zero-length PSI elements and invalid offsets before creating problem descriptors.

#### Scenario: Missing title heading
- **WHEN** a spec file has no `# Title` heading
- **THEN** the validator SHALL report an ERROR with code `spec-title-required`

#### Scenario: Missing requirement block
- **WHEN** a spec file has no `### Requirement:` section
- **THEN** the validator SHALL report an ERROR with code `spec-requirement-required`

#### Scenario: SHOULD-only requirement is flagged
- **WHEN** a requirement's body contains `SHOULD` or `MAY` but neither `SHALL` nor `MUST` as a whole word
- **THEN** the validator SHALL report the missing-keyword ERROR, because the CLI accepts only SHALL/MUST

#### Scenario: Keyword only inside a code fence is not accepted
- **WHEN** a requirement's only `SHALL`/`MUST` occurrence sits inside a fenced code block
- **THEN** the validator SHALL report the missing-keyword ERROR, matching CLI 1.6 fence masking

#### Scenario: Scenario header only inside a code fence is not accepted
- **WHEN** a requirement's only `#### Scenario:` header sits inside a fenced code block
- **THEN** the validator SHALL report the missing-scenario ERROR, matching CLI 1.6 fence-aware scenario counting

### Requirement: Delta spec validation

The plugin SHALL validate delta spec files for structural correctness, including section headings (ADDED, MODIFIED, REMOVED, RENAMED), removal metadata, scenario coverage, and rename FROM/TO structure. Keyword and scenario evaluation SHALL apply the same fence masking as spec-format validation. Non-canonical level-3 headers inside ADDED or MODIFIED sections — headers the upstream parser skips — SHALL produce an INFO-severity issue anchored to the header's line, mirroring CLI 1.6's advisory: a nameless requirement header (`### Requirement:` with no name) SHALL be hinted to add a name, and any other level-3 header SHALL be hinted that it is ignored by validation unless written as `### Requirement: <name>`. INFO-severity issues SHALL never affect the file's validation verdict.

#### Scenario: Missing delta sections
- **WHEN** a delta spec file under a change's `specs/` directory has no ADDED, MODIFIED, REMOVED, or RENAMED sections
- **THEN** the validator SHALL report a WARNING with code `delta-spec-sections`

#### Scenario: Removed requirement missing metadata
- **WHEN** a REMOVED requirement block is missing a **Reason** field or a **Migration** field (recognizing both the `**Reason:**` colon-inside and `**Reason**:` colon-outside bold forms)
- **THEN** the validator SHALL report a WARNING with code `delta-removed-fields`
- **AND** the validator SHALL NOT report this as an ERROR, because Reason/Migration are an OpenSpec authoring convention only — the upstream `@fission-ai/openspec` client validates REMOVED blocks by name and does not require these fields, so the plugin must not be stricter than the client it wraps

#### Scenario: Added requirement missing scenario
- **WHEN** an ADDED requirement block has no `#### Scenario:` section
- **THEN** the validator SHALL report an ERROR with code `delta-requirement-scenario`

#### Scenario: Modified requirement missing scenario
- **WHEN** a MODIFIED requirement block has no `#### Scenario:` section with updated content
- **THEN** the validator SHALL report an ERROR with code `delta-requirement-scenario`

#### Scenario: Non-canonical header in a delta section gets an INFO hint
- **WHEN** an ADDED or MODIFIED section contains a level-3 header that is not a named `### Requirement:` header (e.g. `### Implementation notes`, or a nameless `### Requirement:`)
- **THEN** the validator SHALL report an INFO-severity issue with code `delta-skipped-header` anchored to that header's line, and the file's verdict SHALL be unaffected by it

#### Scenario: Renamed section missing FROM/TO
- **WHEN** a `## RENAMED Requirements` section contains no well-formed `FROM:`/`TO:` pair (matching `^\s*(?:-\s*)?FROM:\s*(.+)$\s*^\s*(?:-\s*)?TO:\s*(.+)$`, mirroring the sync layer's parser)
- **THEN** the validator SHALL report an ERROR with code `delta-renamed-fields`

#### Scenario: Renamed section with valid FROM/TO
- **WHEN** a `## RENAMED Requirements` section contains one or more well-formed `FROM:`/`TO:` pairs (bullet or non-bullet form)
- **THEN** the validator SHALL NOT report `delta-renamed-fields` or `delta-spec-sections` for that file

#### Scenario: Verdict parity with the 1.6 CLI
- **WHEN** the verdict-parity corpus (specs and delta files exercising the keyword, fence, scenario, and skipped-header rule classes) is validated by the plugin and by the captured real 1.6.0 CLI output
- **THEN** the plugin's per-case valid/invalid verdict SHALL match the CLI's `valid` flag for every case
