## MODIFIED Requirements

### Requirement: Propose action

When it creates a change itself (without the CLI), the plugin SHALL create what `openspec new change` creates (`.openspec.yaml`) plus the proposal from the Propose dialog (`proposal.md`) and an empty `specs/` folder. It SHALL NOT write template `design.md` or `tasks.md` files, which upstream never creates and which the CLI would report as done. The `createChange` call and subsequent UI updates SHALL execute via `invokeLater` from a pooled thread to avoid blocking the EDT. When multiple schemas are available, the propose dialog SHALL include a schema selector and pass the selected schema to the CLI.

#### Scenario: Change creation
- **WHEN** the user proposes a change
- **THEN** the plugin SHALL dispatch to a pooled thread, execute `createChange` via `invokeLater` (since it requires WriteAction), and chain the tool window refresh and auto-focus inside the same EDT dispatch

#### Scenario: Schema selection during propose
- **WHEN** multiple schemas are available and the user proposes a change
- **THEN** the ProposeChangeDialog SHALL display a schema combo box and pass `--schema "<schema>"` to the `openspec new change` command

### Requirement: Workflow action panel

The plugin SHALL display the selected change's pipeline status with interactive artifact chips that serve as the primary action surface. Pipeline chips SHALL be clickable (READY → generate, DONE → open file) with right-click context menus. A compact icon action bar SHALL provide secondary actions (Fast-Forward, Verify, Archive, overflow menu). A single-line status strip SHALL show compliance, task progress, and delivery mode. The panel SHALL NOT use a horizontal button row for workflow actions. The content area SHALL use CardLayout to manage NO_CHANGES, FF_INPUT, and PIPELINE views. The Fast-Forward link in the "no changes" card SHALL only be visible when Direct API is configured; when Direct API is not configured, the card SHALL show only the Propose link. The Explore tab context SHALL reflect the current change selection.

#### Scenario: Pipeline visualization
- **WHEN** a change is selected
- **THEN** the panel SHALL show interactive artifact status chips (DONE, READY, BLOCKED, GENERATING) with the status `openspec status` reports, a non-blocking hint on a DONE chip whose file looks like an unfilled template, hover affordances, and click/right-click actions

#### Scenario: Generate via chip click
- **WHEN** the user clicks a READY chip
- **THEN** it SHALL deliver via the selected method (clipboard, editor tab, or Direct API) with a guidance popover showing post-delivery feedback

#### Scenario: Sync Specs in overflow menu
- **WHEN** all artifacts are complete and the change contains delta spec sections
- **THEN** the overflow menu SHALL include a "Sync Specs" item

#### Scenario: Sync Specs hidden from overflow
- **WHEN** the change has no delta spec sections
- **THEN** the overflow menu SHALL NOT include "Sync Specs"

#### Scenario: Compliance chip displayed
- **WHEN** the workflow action panel renders for a selected change
- **THEN** the status strip SHALL include compliance status alongside task progress and delivery mode

#### Scenario: FF link visible with Direct API
- **WHEN** the "no changes" card renders and Direct API is configured
- **THEN** the card SHALL show "Propose or Fast-Forward" with both links active

#### Scenario: FF link hidden without Direct API
- **WHEN** the "no changes" card renders and Direct API is not configured
- **THEN** the card SHALL show only the Propose link without the FF option

#### Scenario: FF input activation
- **WHEN** the user clicks the FF toolbar button or "Fast-Forward" hyperlink
- **THEN** the panel SHALL switch to the FF_INPUT card while keeping the tool selector visible

#### Scenario: Explore tab reflects change context
- **WHEN** a change is selected in the workflow panel
- **THEN** the Explore tab SHALL include that change's details in the assembled context

### Requirement: Delta spec scaffolding uses the client's canonical delta grammar

When the plugin scaffolds a delta spec for a change (the Create Delta Spec action), the generated file SHALL use the section headings the OpenSpec client parses — `## ADDED Requirements`, `## MODIFIED Requirements`, `## REMOVED Requirements`, and `## RENAMED Requirements` — and its placeholder guidance SHALL describe only forms the client accepts: `### Requirement: <name>` blocks with at least one `#### Scenario:` for ADDED and MODIFIED, a `### Requirement: <name>` block with **Reason** and **Migration** for REMOVED, and `FROM:`/`TO:` pairs carrying the requirement header (``- FROM: `### Requirement: <old>` `` then ``- TO: `### Requirement: <new>` ``) for RENAMED. Placeholder guidance SHALL be written as HTML comments so that an unfilled scaffold carries no requirement content and is flagged by the scaffolding hint.

#### Scenario: A filled-in scaffold is accepted by the client
- **WHEN** a user scaffolds a delta spec and adds one valid `### Requirement:` block with a `#### Scenario:` under the ADDED section, leaving the other sections as scaffolded
- **THEN** the OpenSpec client SHALL report the change `valid:true` in default and `--strict` modes, as evidenced by captured real CLI output, rather than rejecting it for having no deltas

#### Scenario: Scaffolded headings are the canonical delta headings
- **WHEN** a delta spec is scaffolded
- **THEN** each of its delta section headings SHALL be exactly one of `## ADDED Requirements`, `## MODIFIED Requirements`, `## REMOVED Requirements`, or `## RENAMED Requirements`, and no bare `## ADDED`, `## MODIFIED`, or `## REMOVED` heading SHALL appear

#### Scenario: An unfilled scaffold is still detected as scaffolding
- **WHEN** a delta spec has just been scaffolded and not edited
- **THEN** the plugin's scaffolding detection SHALL classify it as scaffolding, because it contains only headings and HTML-comment guidance, and SHALL show that as a hint without changing the status `openspec status` reports
