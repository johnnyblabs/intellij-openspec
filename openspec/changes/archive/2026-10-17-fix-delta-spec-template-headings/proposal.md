## Why

The delta spec scaffolded by the plugin's **Create Delta Spec** action uses section headings the OpenSpec client does not recognize: `## ADDED`, `## MODIFIED` and `## REMOVED`, without `Requirements`. Its placeholder also teaches bare-name renames (`- FROM: Old name`). The client only parses `## ADDED Requirements` and its siblings, and `FROM:`/`TO:` lines that carry `### Requirement: <name>`.

So a user who follows the scaffold writes a delta the client cannot see. On the real CLI (1.12.0 and 1.13.2), a scaffolded delta filled in with a valid requirement is rejected with *"Change must have at least one delta. No deltas found"*. 1.13.2 also warns that the requirement sits under `## ADDED`, which is not a delta section. The same content under the canonical headings validates on 1.12.0, 1.13.2 and 1.14.3, in both default and `--strict` mode.

Tracked in the linked tracker entry.

## What Changes

- The delta spec template writes the client's canonical headings: `## ADDED Requirements`, `## MODIFIED Requirements`, `## REMOVED Requirements`, `## RENAMED Requirements`.
- The placeholder guidance in each section matches what the client parses:
  - ADDED and MODIFIED: `### Requirement:` blocks with `#### Scenario:`.
  - REMOVED: blocks with **Reason** and **Migration**.
  - RENAMED: pairs in the canonical ``- FROM: `### Requirement: Old` `` / ``- TO: `### Requirement: New` `` form.
- A freshly scaffolded delta, containing only headings and comments, is still detected as scaffolding (not done). This is unchanged behavior.
- **The built-in validator and the Delta Spec inspection no longer report an ERROR for an empty `## RENAMED Requirements` section** (one with no `FROM:`/`TO:` lines). The scaffold leaves this section empty by design. The CLI accepts it, but the fallback reported `delta-renamed-fields`, so a correctly filled scaffold was rejected offline. A section that has `FROM:`/`TO:` lines without a well-formed pair is still an ERROR.
- A contract test proves that a filled-in scaffold is accepted by the real client. It runs against captured CLI output, not an assumed shape.
- **Not changed:** the built-in validator keeps accepting the short `## ADDED` headings and bare-name renames. Accepting more than the client is allowed by the never-more-restrictive policy, and deltas that users already wrote keep validating offline.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `workflow`: add a requirement that built-in delta-spec scaffolding uses the client's canonical delta grammar.
- `validation`: in "Delta spec validation", an empty RENAMED section is not a `delta-renamed-fields` error. The error now requires `FROM:`/`TO:` content that fails to pair.

## Impact

- **Code:** `TemplateProvider.deltaSpecTemplate` (used by `ScaffoldingService.createDeltaSpec`, which is reached from the Create Delta Spec action); the RENAMED check in `BuiltInValidator.validateDeltaSpecStructure` and `DeltaSpecInspection`.
- **Tests/fixtures:**
  - `TemplateProviderTest` and `ScaffoldingContractTest`, whose existing assertions (`contains("## ADDED")`) pass for both the wrong and the right heading. They will be tightened.
  - A new captured-CLI fixture of a filled-in scaffold validating `valid:true`.
- **Docs (vendor-neutral):** `docs/feature-reference.md` if it shows the template; a CHANGELOG `## Unreleased` fix entry.
- **Platforms/CLI:** no change to IntelliJ 2024.2+ compatibility or the supported CLI range.
