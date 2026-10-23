# Change: demo-add-farewell

Create specification files that define WHAT the system should do.

A spec is a behavior contract, not an implementation plan.

Good spec content:
- Observable behavior users or downstream systems rely on
- Inputs, outputs, and error conditions
- External constraints (security, privacy, reliability, compatibility)
- Scenarios that can be tested or explicitly validated

Avoid in specs:
- Internal class/function names
- Library or framework choices
- Step-by-step implementation details
- Detailed execution plans (those belong in design.md or tasks.md)

Quick test: if the implementation can change without changing externally
visible behavior, it likely does not belong in the spec.

Create one spec file per capability listed in the proposal's Capabilities section.
`<capability-path>` is the spec directory relative to `specs/` (for example,
`user-auth` or `identity/user-auth`). Preserve the full path:
- New capabilities: use the exact path from the proposal at `specs/<capability-path>/spec.md`. Any path segment newly introduced in the proposal must be kebab-case. Follow the project's existing organization; do not add a new domain level when the project uses a flat layout.
- Modified capabilities: use the exact existing path from `openspec/specs/<capability-path>/` when creating the delta at `specs/<capability-path>/spec.md`. Run `openspec list --specs` to confirm that path before writing the delta, appending `--store "<id>"` only for a registered standalone store - a mistyped or invented path targets a capability that does not exist rather than the one you meant. Do not move or rename the capability.

Write the spec files one at a time, so the user sees progress and an
interrupted run keeps the files it already wrote:
- Before writing anything, compare the capabilities the proposal lists
  with the `specs/<capability-path>/spec.md` files that already exist,
  and start with the first capability that has no file. Do not rewrite
  an existing file unless the user asks you to.
- Before each file, say in one line which capability you are writing.
- Write each file as soon as you have drafted it. Do not plan every file
  before writing the first.
- Stop when every capability the proposal lists has its file.

There must be at least one spec file unless the change's `.openspec.yaml`
sets `skip_specs: true` (no spec-level behavior change) - `openspec validate`
rejects a zero-delta change without that marker. If the proposal lists no
capabilities and `skip_specs` is not set, revisit the proposal first.

Delta operations (use ## headers):
- **ADDED Requirements**: New capabilities
- **MODIFIED Requirements**: Changed behavior - MUST include full updated content
- **REMOVED Requirements**: Deprecated features - MUST include **Reason** and **Migration**
- **RENAMED Requirements**: Name changes only - use FROM:/TO: format

Format requirements:
- Each requirement: `### Requirement: <name>` followed by description
- Use SHALL/MUST for normative requirements (avoid should/may)
- Each scenario: `#### Scenario: <name>` with WHEN/THEN format
- **CRITICAL**: Scenarios MUST use exactly 4 hashtags (`####`). Using 3 hashtags or bullets will fail silently.
- Every requirement MUST have at least one scenario.
- Keep each requirement's description (the text between `### Requirement:` and its first scenario) to 500 characters or fewer. `openspec validate` flags longer descriptions in ADDED requirements and in the main spec. This is a warning: normal validation still passes, but `openspec validate --strict` fails on it. When writing a new requirement, state one behavior per requirement: move examples and edge cases into scenarios, and split a requirement that covers several behaviors into separate `### Requirement:` blocks, each with its own scenarios. Under MODIFIED, keep the existing requirement block whole; never split, trim or rewrite existing text just to meet the length. Split an existing long requirement only when the user asks for it, in a change made for that purpose: under MODIFIED, keep its header and every scenario and cut its description down to one behavior without changing its meaning, then add each behavior you removed as its own ADDED requirement with its own scenarios.

New capabilities only: the delta spec's first section is `## Purpose` -
one or two sentences (50+ characters, or `openspec validate --strict`
reports it as too brief) describing what the capability is for. Archive
copies it into the main spec it creates; without it the new main spec is
left with a `TBD ... Update Purpose after archive` placeholder to fill in
by hand. Do NOT add `## Purpose` to a delta for an existing capability -
that spec already has one and the delta's is ignored. To change an
existing capability's Purpose - including a leftover `TBD` placeholder -
edit `<planningHome.root>/openspec/specs/<capability-path>/spec.md`
directly. `planningHome.root` comes from the `openspec instructions ...
--json` response. Always use it rather than a repo-relative path: it
resolves to the store whenever the change lives in one - whether that
came from `--store`, a project `store:` pointer, or a global default
store - and to the current repository otherwise. Do not try to work out
which case applies; the field already has.

MODIFIED requirements workflow:
1. Locate the existing requirement in `<planningHome.root>/openspec/specs/<capability-path>/spec.md` (the same store-aware root as above)
2. Copy the ENTIRE requirement block (from `### Requirement:` through all scenarios)
3. Paste under `## MODIFIED Requirements` and edit to reflect new behavior
4. Ensure header text matches exactly (whitespace-insensitive)

Common pitfall: Using MODIFIED with partial content loses detail at archive time.
If adding new concerns without changing existing behavior, use ADDED instead.

Example (a new capability, so its first section is `## Purpose`):
```
# Spec Delta

## Purpose

Lets users take their data out of the product in a portable format.

## ADDED Requirements

### Requirement: User can export data
The system SHALL allow users to export their data in CSV format.

#### Scenario: Successful export
- **WHEN** user clicks "Export" button
- **THEN** system downloads a CSV file with all user data

## REMOVED Requirements

### Requirement: Legacy export
**Reason**: Replaced by new export system
**Migration**: Use new export endpoint at /api/v2/export
```

Specs should be testable - each scenario is a potential test case.


---

Template:
# Spec Delta

## Purpose
<!-- New capabilities only: one or two sentences (50+ characters) on what this capability is for. Delete this section for an existing capability. -->

## ADDED Requirements

### Requirement: <!-- requirement name -->
<!-- requirement text -->

#### Scenario: <!-- scenario name -->
- **WHEN** <!-- condition -->
- **THEN** <!-- expected outcome -->


---

Dependencies:

### proposal — Initial proposal document outlining the change

```
## Why
The demo needs a change mid-lifecycle so the workflow chips show done / ready / blocked states.

## What Changes
- Add a farewell message alongside the greeting.

## Capabilities

### Modified Capabilities
- `greeting`: adds a farewell requirement.

## Impact
Demo only.

```


---

## Output format (Direct API)

Your response is not read by a person: the plugin writes it to files. You can't run commands or write files yourself, so the context you would look up is given below. Ignore any instruction above to run a command or to announce progress; write only the files.

Start each file with a marker on a line of its own, exactly:

<!-- file: <path> -->

where `<path>` is relative to the change directory and matches `specs/**/*.md` (for specs: `specs/<capability-path>/spec.md`). Put the file's full content after the marker. Write nothing before the first marker or between files, and don't wrap files in code fences.

### Existing capabilities (under `openspec/specs/`)

- `greeting`
- `keyword-in-header`

### Main specs the proposal names

Copy whole requirement blocks from these into MODIFIED deltas.

#### openspec/specs/greeting/spec.md

```
# Greeting

## Purpose
Demo capability for the lifecycle walkthrough.

## Requirements

### requirement: Friendly greeting
The system SHALL greet the user by name.

#### Scenario: Greet
- **WHEN** the user arrives
- **THEN** the system greets them by name
```

### Files this change already has

None.
