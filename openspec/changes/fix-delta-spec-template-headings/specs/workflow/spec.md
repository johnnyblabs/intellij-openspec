## ADDED Requirements

### Requirement: Delta spec scaffolding uses the client's canonical delta grammar

When the plugin scaffolds a delta spec for a change (the Create Delta Spec action), the generated file SHALL use the section headings the OpenSpec client parses — `## ADDED Requirements`, `## MODIFIED Requirements`, `## REMOVED Requirements`, and `## RENAMED Requirements` — and its placeholder guidance SHALL describe only forms the client accepts: `### Requirement: <name>` blocks with at least one `#### Scenario:` for ADDED and MODIFIED, a `### Requirement: <name>` block with **Reason** and **Migration** for REMOVED, and `FROM:`/`TO:` pairs carrying the requirement header (``- FROM: `### Requirement: <old>` `` then ``- TO: `### Requirement: <new>` ``) for RENAMED. Placeholder guidance SHALL be written as HTML comments so that an unfilled scaffold carries no requirement content and continues to be detected as scaffolding rather than as a completed artifact.

#### Scenario: A filled-in scaffold is accepted by the client
- **WHEN** a user scaffolds a delta spec and adds one valid `### Requirement:` block with a `#### Scenario:` under the ADDED section, leaving the other sections as scaffolded
- **THEN** the OpenSpec client SHALL report the change `valid:true` in default and `--strict` modes, as evidenced by captured real CLI output, rather than rejecting it for having no deltas

#### Scenario: Scaffolded headings are the canonical delta headings
- **WHEN** a delta spec is scaffolded
- **THEN** each of its delta section headings SHALL be exactly one of `## ADDED Requirements`, `## MODIFIED Requirements`, `## REMOVED Requirements`, or `## RENAMED Requirements`, and no bare `## ADDED`, `## MODIFIED`, or `## REMOVED` heading SHALL appear

#### Scenario: An unfilled scaffold is still detected as scaffolding
- **WHEN** a delta spec has just been scaffolded and not edited
- **THEN** the plugin's scaffolding detection SHALL classify it as scaffolding (not done), because it contains only headings and HTML-comment guidance
