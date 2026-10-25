# Workflow

## Purpose
Artifact generation pipeline: propose, generate, apply, archive actions with delivery routing and progress feedback.

## Requirements

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

### Requirement: FF action requires Direct API

The FF menu action SHALL be disabled when Direct API is not configured. When disabled, the action SHALL display a tooltip indicating that an AI provider must be configured in Settings → Tools → OpenSpec. The action SHALL re-evaluate its enabled state on each menu presentation update.

#### Scenario: FF action disabled without Direct API
- **WHEN** the FF menu action updates and Direct API is not configured
- **THEN** the action SHALL be disabled with description "Requires AI provider. Configure in Settings → Tools → OpenSpec."

#### Scenario: FF action enabled with Direct API
- **WHEN** the FF menu action updates and Direct API is configured
- **THEN** the action SHALL be enabled (subject to existing profile-based visibility)

#### Scenario: FF input guard without Direct API
- **WHEN** `activateFfInput()` is called and Direct API is not configured
- **THEN** the panel SHALL show an inline message directing the user to configure an API provider instead of showing the FF form

### Requirement: Generate All

The plugin SHALL sequentially generate all remaining artifacts in dependency order via Direct API with progress reporting and cancellation support.

#### Scenario: Orchestration
- **WHEN** Generate All is triggered
- **THEN** artifacts SHALL generate in DAG dependency order with real-time progress feedback

### Requirement: Apply action

The plugin SHALL assemble a full-context implementation prompt (proposal, specs, design, tasks) and deliver via the selected tool.

#### Scenario: Prompt assembly
- **WHEN** Apply is triggered
- **THEN** the plugin SHALL assemble context from all change artifacts and deliver via the user's preferred method

### Requirement: Archive action

The plugin SHALL archive completed changes, through the OpenSpec CLI when it is available (see "Archive delegates to the OpenSpec CLI") and otherwise itself, with post-archive commit, push, and tracker updates. A Bulk Archive option SHALL be available when multiple active changes exist. When the change contains unsynced delta specs, the plugin SHALL prompt the user before proceeding with a three-option dialog. The archive SHALL run in a background task, never on the UI thread, and the post-archive UI updates (popover, refresh, notifications) SHALL be dispatched to the UI thread with `invokeLater`.

#### Scenario: Archive flow
- **WHEN** the user archives a change
- **THEN** the change SHALL be moved to the archive directory and post-archive steps SHALL execute

#### Scenario: Bulk archive entry point
- **WHEN** multiple active changes exist
- **THEN** a Bulk Archive action SHALL be available in the menu and toolbar

#### Scenario: Archive guard with unsynced delta specs
- **WHEN** the user clicks Archive and the change has unsynced delta specs (`hasDeltaSpecs` is true)
- **THEN** the plugin SHALL display a confirmation dialog with three options: "Sync First", "Archive Without Syncing", and "Cancel"

#### Scenario: Archive guard — Sync First
- **WHEN** the user selects "Sync First" from the archive guard dialog
- **THEN** the plugin SHALL trigger the sync specs workflow and SHALL NOT proceed with archiving

#### Scenario: Archive guard — Archive Without Syncing
- **WHEN** the user selects "Archive Without Syncing" from the archive guard dialog
- **THEN** the plugin SHALL proceed with archiving the change as-is, including the unsynced delta specs, without updating main specs (`--skip-specs` when the CLI archives)

#### Scenario: Archive guard — Cancel
- **WHEN** the user selects "Cancel" from the archive guard dialog
- **THEN** the plugin SHALL abort the archive action and take no further action

#### Scenario: Archive without delta specs
- **WHEN** the user clicks Archive and the change has no delta specs (`hasDeltaSpecs` is false)
- **THEN** the plugin SHALL proceed directly with archiving without showing the guard dialog

#### Scenario: No invokeAndWait in archive background task
- **WHEN** the background task executes the archive operation
- **THEN** the archive SHALL run on the background task's thread, and the popover and refresh SHALL be chained in a single `invokeLater` to the UI thread, never `invokeAndWait`

#### Scenario: Archive error reported from invokeLater
- **WHEN** the archive fails or is refused
- **THEN** the failure SHALL be reported from an `invokeLater` on the UI thread: the archive button SHALL be re-enabled and an error notification SHALL be shown

### Requirement: Change selector

The plugin SHALL provide a change selector for switching between active changes with session persistence.

#### Scenario: Multiple changes
- **WHEN** multiple active changes exist
- **THEN** the user SHALL be able to switch between them via dropdown

### Requirement: Tree-to-panel synchronization

The plugin SHALL synchronize the workflow panel's active change with the tree view selection. When the user selects a change node (or a child of a change node) in the tree, the workflow panel SHALL update to display that change's pipeline, icon bar, and status strip. The synchronization SHALL be one-way: tree drives panel.

#### Scenario: Select change in tree
- **WHEN** the user clicks a change node in the tree
- **THEN** the workflow panel SHALL update its active change to match and refresh the pipeline display

#### Scenario: Select child of change node
- **WHEN** the user clicks an artifact or spec node under a change
- **THEN** the workflow panel SHALL update its active change to the parent change

#### Scenario: Select non-change node
- **WHEN** the user clicks a node that is not a change or child of a change (e.g., main specs, config, archive)
- **THEN** the workflow panel SHALL NOT change its active change selection

#### Scenario: Dropdown remains functional
- **WHEN** the user selects a change from the workflow panel's dropdown
- **THEN** the panel SHALL update normally without affecting the tree selection

### Requirement: Background thread for change selection refresh

The plugin SHALL perform artifact status lookups on a background thread when the active change is set via external sources (e.g., tree selection). The EDT SHALL NOT be blocked by CLI or orchestration service calls during change selection.

#### Scenario: setActiveChange dispatches to background thread
- **WHEN** `setActiveChange()` is called from the EDT (e.g., tree selection handler)
- **THEN** the artifact status lookup SHALL execute on a pooled background thread, not on the EDT

#### Scenario: Pipeline updates asynchronously after selection
- **WHEN** `setActiveChange()` dispatches the refresh to a background thread
- **THEN** the pipeline display SHALL update on the EDT via `invokeLater` after the background work completes

### Requirement: FF action registration

The plugin SHALL register a Fast-Forward action in the OpenSpec menu and toolbar, accessible via menu item and keyboard shortcut.

#### Scenario: Menu registration
- **WHEN** the user opens the OpenSpec menu
- **THEN** Fast-Forward SHALL appear as a menu item with appropriate icon

### Requirement: Bulk Archive action registration

The plugin SHALL register a Bulk Archive action in the OpenSpec menu for archiving multiple changes at once.

#### Scenario: Menu registration
- **WHEN** the user opens the OpenSpec menu
- **THEN** Bulk Archive SHALL appear as a menu item

### Requirement: Schema/mode-aware workflow surfaces

Workflow surfaces (propose, action panel, pipeline, status strip) SHALL consult the resolved workflow schema context and adapt to `actionContext.mode` rather than assuming a `spec-driven`, repo-local layout. For the default spec-driven repo-local case, surfaces SHALL behave exactly as they do today. For a non-default mode (e.g. `workspace-planning`), surfaces SHALL reflect that mode rather than presenting spec-driven-only affordances as if they applied.

#### Scenario: Default spec-driven repo-local is unchanged
- **WHEN** the resolved schema context reports `spec-driven` / repo-local
- **THEN** the workflow surfaces SHALL render and behave exactly as before this change

#### Scenario: Non-default mode is reflected
- **WHEN** the resolved schema context reports a non-default `actionContext.mode` such as `workspace-planning`
- **THEN** the workflow surfaces SHALL adapt to that mode and SHALL NOT present spec-driven-only affordances as applicable

#### Scenario: Surfaces read context, not filesystem layout
- **WHEN** a workflow surface needs to know the active mode or source of truth
- **THEN** it SHALL obtain it from the resolved workflow schema context rather than inferring it from the on-disk directory layout

### Requirement: Generation prompt tolerates glob-valued dependency paths

When assembling the prompt to generate an artifact, the plugin inlines the content of each completed dependency. A dependency's declared path MAY be a glob pattern rather than a single file — the OpenSpec CLI declares the `specs` dependency's path as `specs/**/*.md`. The plugin SHALL NOT resolve a glob-valued path (one containing `*`, `?`, or `[`) to a single filesystem path, on any operating system. It SHALL instead inline every existing file in the change that matches the glob, each labeled with its path, and SHALL fall back to a path-only reference when none match. Artifact generation SHALL NOT fail because a dependency path cannot be resolved to a filesystem path.

#### Scenario: Completed dependency has a glob-valued path
- **WHEN** an artifact is generated whose completed dependency declares a glob-valued path (e.g. `specs/**/*.md`)
- **THEN** the plugin SHALL NOT attempt to read that glob as a single file
- **AND** the generation prompt SHALL inline each file in the change that matches the glob, labeled with its path relative to the change, or SHALL reference the dependency by its path when no file matches
- **AND** generation SHALL succeed identically on Windows and on POSIX platforms, without a path-resolution error

#### Scenario: Completed dependency is a real file
- **WHEN** an artifact is generated whose completed dependency declares a concrete (non-glob) file path that exists on disk
- **THEN** the plugin SHALL inline that file's content into the generation prompt

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

### Requirement: In-panel links and buttons launch actions through the platform action pipeline

When a link or button inside the plugin's tool window, Getting Started panel, workflow panel, or Setup Wizard launches a plugin action (for example Propose, Explore, or the delta-spec diff from the deltas preview), the plugin SHALL run that action through the IDE's standard action-execution pipeline, with a data context derived from the UI component the user interacted with (or, when that component has been disposed, from the project's frame). The action's own availability check SHALL run first, so an action that is disabled in the current context does not run. The plugin SHALL NOT use platform API that is deprecated on any IDE in its supported range for this purpose when a non-deprecated equivalent exists at the compatibility floor.

The Propose action, the Explore action and the delta-spec diff action SHALL be available while the IDE is indexing, because they do not depend on the IDE's indexes.

#### Scenario: Propose from an in-panel link opens the Propose flow
- **WHEN** the user clicks an in-panel Propose affordance (the Getting Started button, the workflow panel link, or the "No active changes" tree hint) in an OpenSpec project
- **THEN** the Propose action SHALL run, exactly as when it is invoked from the menu

#### Scenario: Explore from an in-panel link starts Explore
- **WHEN** the user clicks an in-panel "Explore an idea first" link in an OpenSpec project
- **THEN** the Explore action SHALL run, exactly as when it is invoked from the menu

#### Scenario: A disabled action does not run
- **WHEN** an in-panel affordance launches an action whose availability check reports it disabled in the current context
- **THEN** the action SHALL NOT run

#### Scenario: Available during indexing
- **WHEN** the IDE is indexing and the user clicks an in-panel Propose affordance, an "Explore an idea first" link or a delta-spec diff link
- **THEN** the action SHALL run rather than being silently skipped

#### Scenario: No deprecated action-invocation API
- **WHEN** the Plugin Verifier checks the plugin against every IDE in the supported range
- **THEN** it SHALL report no usage of the deprecated `ActionUtil.invokeAction` overloads

### Requirement: Archive target directory

When the plugin archives a change itself (no OpenSpec CLI, or a CLI older than 1.5), it SHALL move the change to `openspec/changes/archive/YYYY-MM-DD-<name>/`, using the local date and keeping a name that already starts with a date unchanged. It SHALL refuse, before writing any spec, when that target already exists. It SHALL create `openspec/changes/archive/` when missing and SHALL NOT create `openspec/archive/`, though it SHALL still read archived changes from an existing legacy `openspec/archive/`.

#### Scenario: Dated archive name
- **WHEN** the user archives change `add-login` on 2026-10-17
- **THEN** the change SHALL be moved to `openspec/changes/archive/2026-10-17-add-login/`

#### Scenario: Target already exists
- **WHEN** `openspec/changes/archive/2026-10-17-add-login/` already exists
- **THEN** the archive SHALL be refused with no spec written and the change left active

#### Scenario: No archive directory yet
- **WHEN** `openspec/changes/archive/` does not exist
- **THEN** the plugin SHALL create it and archive into it, and SHALL NOT create `openspec/archive/`

### Requirement: Archive delegates to the OpenSpec CLI

With OpenSpec CLI 1.5 or later available, the plugin SHALL archive by running `openspec archive <name> --yes --json`, adding `--skip-specs` when the user archives without syncing. It SHALL report the archived folder on success, and on a non-zero exit SHALL show the CLI's `status[].message` and `fix` and leave the change active. Without a CLI, or with an older one, the plugin SHALL archive itself.

#### Scenario: CLI archives the change
- **WHEN** OpenSpec CLI 1.14 is available and the user archives `add-login`
- **THEN** the plugin SHALL run `openspec archive add-login --yes --json` and report the folder named in its `archivedAs`

#### Scenario: CLI refuses the change
- **WHEN** the CLI exits non-zero with a `status` entry
- **THEN** the plugin SHALL show that entry's `message` and `fix`, and the change SHALL stay active

#### Scenario: Older or missing CLI
- **WHEN** no OpenSpec CLI is available, or its version is below 1.5
- **THEN** the plugin SHALL archive the change itself, as described in "Archive target directory"
