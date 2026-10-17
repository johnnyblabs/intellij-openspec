## MODIFIED Requirements

### Requirement: Delta spec validation

The plugin SHALL validate delta spec files for structural correctness, including section headings (ADDED, MODIFIED, REMOVED, RENAMED), removal metadata, scenario coverage, and rename FROM/TO structure. Keyword and scenario evaluation SHALL apply the same fence masking as spec-format validation. Non-canonical level-3 headers inside ADDED or MODIFIED sections — headers the upstream parser skips — SHALL produce an INFO-severity issue anchored to the header's line, mirroring CLI 1.6's advisory: a nameless requirement header (`### Requirement:` with no name) SHALL be hinted to add a name, and any other level-3 header SHALL be hinted that it is ignored by validation unless written as `### Requirement: <name>`. INFO-severity issues SHALL never affect the file's validation verdict.

The plugin SHALL discover a change's delta spec files by recursively locating `spec.md` files under the change's `specs/` directory, matching the CLI's discovery rules: a `spec.md` at any depth of at least one directory below `specs/` (e.g. `specs/<capability-path>/spec.md`, including multi-segment capability paths) is a delta and SHALL be structurally validated. A **regular file named exactly `spec.md` directly at the `specs/` root** SHALL be reported as a misplaced-delta ERROR (rule `delta-spec-misplaced`) — mirroring the CLI, which ignores such a file on apply/archive so its requirements are silently dropped. This detection SHALL be path-based and independent of the file's content, and SHALL flag only a `spec.md` at the root (never a `spec.md` at depth ≥ 1), so the fallback is never more restrictive than the CLI. When a change whose schema requires specs (its schema declares a `specs` artifact) has **no delta `spec.md` anywhere** under `specs/`, the validator SHALL report a no-deltas ERROR (rule `delta-none-found`); this ERROR SHALL be suppressed when the change declares `skip_specs: true`, matching the CLI, which treats a `skip_specs` change with no deltas as valid. A misplaced root `spec.md` counts as a delta found for this purpose, so a change whose only delta is misplaced yields the misplaced-delta ERROR alone, never also a no-deltas ERROR. Both ERRORs mirror CLI errors and SHALL fail the file's verdict in default and strict modes.

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
- **WHEN** a `## RENAMED Requirements` section contains at least one `FROM:` or `TO:` line but no well-formed `FROM:`/`TO:` pair (matching `^\s*(?:-\s*)?FROM:\s*(.+)$\s*^\s*(?:-\s*)?TO:\s*(.+)$`, mirroring the sync layer's parser)
- **THEN** the validator SHALL report an ERROR with code `delta-renamed-fields`

#### Scenario: An empty RENAMED section is not an error
- **WHEN** a `## RENAMED Requirements` section contains no `FROM:` or `TO:` line at all (it is empty, or holds only HTML-comment guidance such as a freshly scaffolded delta), and the built-in validator or the delta IDE inspection evaluates it
- **THEN** neither SHALL report `delta-renamed-fields` for that section, matching the OpenSpec client, which reports a change `valid:true` when its delta has an empty RENAMED section beside a parsed ADDED requirement — so the fallback is never more restrictive than the client

#### Scenario: Renamed section with valid FROM/TO
- **WHEN** a `## RENAMED Requirements` section contains one or more well-formed `FROM:`/`TO:` pairs (bullet or non-bullet form)
- **THEN** the validator SHALL NOT report `delta-renamed-fields` or `delta-spec-sections` for that file

#### Scenario: Verdict parity with the 1.6 CLI
- **WHEN** the verdict-parity corpus (specs and delta files exercising the keyword, fence, scenario, and skipped-header rule classes) is validated by the plugin and by the captured real 1.6.0 CLI output
- **THEN** the plugin's per-case valid/invalid verdict SHALL match the CLI's `valid` flag for every case

#### Scenario: Delta spec at the specs root is a misplaced-delta error
- **WHEN** a change has a regular file named exactly `spec.md` directly at its `specs/` root (no capability directory), and the built-in validator is the verdict
- **THEN** the validator SHALL report an ERROR with code `delta-spec-misplaced`, matching the CLI which reports such a change `valid:false` because a root-level delta is dropped on apply/archive, and the file's verdict SHALL fail

#### Scenario: Delta nested under a capability path is discovered and validated
- **WHEN** a change's delta lives at `specs/<capability-path>/spec.md`, including a multi-segment path such as `specs/<area>/<capability>/spec.md`
- **THEN** the validator SHALL discover it as a delta and apply the delta structural rules to it (it SHALL NOT be reported as misplaced), matching the CLI which accepts a `spec.md` at any depth of at least one directory below `specs/`

#### Scenario: Change requiring specs with no deltas is an error
- **WHEN** a change whose schema requires specs has no delta `spec.md` under `specs/` (an empty or missing `specs/` directory, or only non-`spec.md` files) and does not declare `skip_specs`
- **THEN** the validator SHALL report an ERROR with code `delta-none-found`, matching the CLI which reports such a change `valid:false`

#### Scenario: A skip_specs change with no deltas is not an error
- **WHEN** a change declares `skip_specs: true` in its `.openspec.yaml` and has no delta `spec.md`
- **THEN** the validator SHALL NOT report a `delta-none-found` error, matching the CLI which treats a `skip_specs` change with no deltas as valid

#### Scenario: A misplaced delta does not also trigger the no-deltas error
- **WHEN** a change's only delta is a misplaced `spec.md` at the `specs/` root
- **THEN** the validator SHALL report the `delta-spec-misplaced` ERROR only, and SHALL NOT additionally report `delta-none-found`, matching the CLI which reports the misplaced issue alone
