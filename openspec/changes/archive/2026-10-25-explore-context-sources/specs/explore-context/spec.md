## MODIFIED Requirements

### Requirement: Context assembly

The plugin SHALL assemble a Markdown-formatted project context from the current OpenSpec project state: a config summary with context and rules, detected AI tools, active changes with the content of every artifact file each change has, and every capability with its requirement summaries. Capabilities and artifact files at nested paths SHALL be included and named by their full path.

#### Scenario: Full context assembly
- **WHEN** the user triggers context assembly
- **THEN** the service SHALL produce a Markdown document with sections for Project Config, Detected AI Tools, Active Changes, and Specs

#### Scenario: Config section includes context and rules
- **WHEN** context is assembled and `config.yaml` has `context` and `rules` fields
- **THEN** the Project Config section SHALL include the schema, context description, and rules as a bulleted list
- **AND** it SHALL NOT include a `version` field, which upstream does not read

#### Scenario: Active changes with full artifacts
- **WHEN** an active change has artifact files and the CLI reports them in its status for the change
- **THEN** the Active Changes section SHALL include the full content of each reported file under the change heading, in the order the CLI lists the artifacts
- **AND** each file SHALL be labelled with its artifact id, and a file of an artifact that generates several files SHALL also be labelled with its path inside the change

#### Scenario: Active changes with missing artifacts
- **WHEN** an active change is missing some artifact files
- **THEN** the Active Changes section SHALL include only the artifacts that exist, silently skipping missing ones

#### Scenario: Nested delta spec
- **WHEN** an active change has a delta spec for a nested capability, such as `specs/identity/user-auth/spec.md`
- **THEN** the Active Changes section SHALL include that delta spec, labelled with its path `specs/identity/user-auth/spec.md`

#### Scenario: Custom-schema artifacts
- **WHEN** an active change uses a schema with artifacts other than proposal, specs, design and tasks
- **THEN** the Active Changes section SHALL include those artifacts' files, labelled with their artifact ids

#### Scenario: Change artifacts without the CLI
- **WHEN** the CLI is unavailable, fails, rejects the change, or reports no artifact paths for it
- **THEN** the Active Changes section SHALL include every Markdown file under the change directory, at any depth, labelled with its path inside the change
- **AND** it SHALL skip files and directories whose names start with a dot

#### Scenario: Spec domain with requirement summaries
- **WHEN** a capability has a `spec.md` with `### Requirement:` blocks
- **THEN** the Specs section SHALL list each requirement name with its description text (not scenarios), under the capability's full id

#### Scenario: Nested capability
- **WHEN** the project has a capability at `openspec/specs/identity/user-auth/spec.md`
- **THEN** the Specs section SHALL list it as `identity/user-auth`, whether the capability list came from the CLI or from the recursive walk

#### Scenario: No active changes
- **WHEN** the project has no active changes
- **THEN** the Active Changes section SHALL indicate no changes are in progress

#### Scenario: Assembled off the UI thread
- **WHEN** the user starts Explore from the menu or the Explore tab, with any delivery mode
- **THEN** the plugin SHALL assemble the context in a cancellable background task and deliver the prompt when it completes
- **AND** cancelling the task SHALL deliver nothing and, in the Explore tab, SHALL leave the input enabled
