## MODIFIED Requirements

### Requirement: Propose action

When the plugin creates a change itself (without the CLI), it SHALL create what `openspec new change` creates (`.openspec.yaml`), plus `proposal.md` from the Propose dialog and an empty `specs/` folder. It SHALL NOT write `design.md` or `tasks.md`. The create call SHALL run off the EDT, with UI updates dispatched back to it. With several schemas, the dialog SHALL offer a schema selector and pass the choice to the CLI.

#### Scenario: Change creation
- **WHEN** the user proposes a change
- **THEN** the plugin SHALL dispatch to a pooled thread, execute `createChange` via `invokeLater` (since it requires WriteAction), and chain the tool window refresh and auto-focus inside the same EDT dispatch

#### Scenario: Schema selection during propose
- **WHEN** multiple schemas are available and the user proposes a change
- **THEN** the ProposeChangeDialog SHALL display a schema combo box and pass `--schema "<schema>"` to the `openspec new change` command

#### Scenario: No template design or tasks files
- **WHEN** a change is created without the CLI
- **THEN** no template `design.md` or `tasks.md` SHALL be written, because upstream never creates them and the CLI would report them as done

### Requirement: Workflow action panel

The panel SHALL show the selected change's pipeline as interactive artifact chips, the primary action surface. READY chips SHALL generate and DONE chips SHALL open the file, each with a right-click menu. A compact icon bar SHALL give secondary actions, and a one-line status strip SHALL show compliance, task progress and delivery mode. The panel SHALL NOT use a horizontal button row for workflow actions.

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

#### Scenario: Content views
- **WHEN** the panel shows content
- **THEN** a CardLayout SHALL switch between the NO_CHANGES, FF_INPUT and PIPELINE views

#### Scenario: Fast-Forward visibility
- **WHEN** Direct API is not configured
- **THEN** the "no changes" card SHALL show only the Propose link, and the Fast-Forward link SHALL appear only when Direct API is configured

#### Scenario: Explore context
- **WHEN** the change selection changes
- **THEN** the Explore tab context SHALL follow it

### Requirement: Archive action

The plugin SHALL archive completed changes through the OpenSpec CLI when it is available, otherwise itself, with post-archive commit, push and tracker updates. A Bulk Archive option SHALL appear when several active changes exist. When a change has unsynced delta specs, the plugin SHALL show a three-option prompt first. Archiving SHALL run in a background task, with post-archive UI updates dispatched to the UI thread.

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

### Requirement: Schema/mode-aware workflow surfaces

Workflow surfaces (propose, action panel, pipeline, status strip) SHALL consult the resolved workflow schema context and adapt to `actionContext.mode`, not assume a repo-local `spec-driven` layout. The default spec-driven repo-local case SHALL behave as it does today. A non-default mode SHALL be reflected, not presented with spec-driven-only affordances.

#### Scenario: Default spec-driven repo-local is unchanged
- **WHEN** the resolved schema context reports `spec-driven` / repo-local
- **THEN** the workflow surfaces SHALL render and behave exactly as before this change

#### Scenario: Non-default mode is reflected
- **WHEN** the resolved schema context reports a non-default `actionContext.mode` such as `workspace-planning`
- **THEN** the workflow surfaces SHALL adapt to that mode and SHALL NOT present spec-driven-only affordances as applicable

#### Scenario: Surfaces read context, not filesystem layout
- **WHEN** a workflow surface needs to know the active mode or source of truth
- **THEN** it SHALL obtain it from the resolved workflow schema context rather than inferring it from the on-disk directory layout

#### Scenario: Non-default mode
- **WHEN** the mode is non-default (such as `workspace-planning`)
- **THEN** the surfaces SHALL show that mode and SHALL NOT present spec-driven-only affordances as if they applied

### Requirement: Generation prompt tolerates glob-valued dependency paths

When assembling an artifact prompt, the plugin SHALL inline each completed dependency. A glob-valued dependency path (containing `*`, `?` or `[`) SHALL NOT be resolved to a single file, on any OS. The plugin SHALL inline every existing matching file, labelled with its path, and fall back to a path-only reference when none match. Generation SHALL NOT fail because a path cannot be resolved.

#### Scenario: Completed dependency has a glob-valued path
- **WHEN** an artifact is generated whose completed dependency declares a glob-valued path (e.g. `specs/**/*.md`)
- **THEN** the plugin SHALL NOT attempt to read that glob as a single file
- **AND** the generation prompt SHALL inline each file in the change that matches the glob, labeled with its path relative to the change, or SHALL reference the dependency by its path when no file matches
- **AND** generation SHALL succeed identically on Windows and on POSIX platforms, without a path-resolution error

#### Scenario: Completed dependency is a real file
- **WHEN** an artifact is generated whose completed dependency declares a concrete (non-glob) file path that exists on disk
- **THEN** the plugin SHALL inline that file's content into the generation prompt

#### Scenario: Specs dependency
- **WHEN** the `specs` dependency declares `specs/**/*.md`
- **THEN** every existing matching file SHALL be inlined, each labelled with its path

### Requirement: Delta spec scaffolding uses the client's canonical delta grammar

Create Delta Spec SHALL generate a file with the section headings the client parses: `## ADDED Requirements`, `## MODIFIED Requirements`, `## REMOVED Requirements` and `## RENAMED Requirements`. Its placeholder guidance SHALL describe only forms the client accepts, and SHALL be written as HTML comments, so an unfilled scaffold carries no requirement content and is flagged by the scaffolding hint.

#### Scenario: A filled-in scaffold is accepted by the client
- **WHEN** a user scaffolds a delta spec and adds one valid `### Requirement:` block with a `#### Scenario:` under the ADDED section, leaving the other sections as scaffolded
- **THEN** the OpenSpec client SHALL report the change `valid:true` in default and `--strict` modes, as evidenced by captured real CLI output, rather than rejecting it for having no deltas

#### Scenario: Scaffolded headings are the canonical delta headings
- **WHEN** a delta spec is scaffolded
- **THEN** each of its delta section headings SHALL be exactly one of `## ADDED Requirements`, `## MODIFIED Requirements`, `## REMOVED Requirements`, or `## RENAMED Requirements`, and no bare `## ADDED`, `## MODIFIED`, or `## REMOVED` heading SHALL appear

#### Scenario: An unfilled scaffold is still detected as scaffolding
- **WHEN** a delta spec has just been scaffolded and not edited
- **THEN** the plugin's scaffolding detection SHALL classify it as scaffolding, because it contains only headings and HTML-comment guidance, and SHALL show that as a hint without changing the status `openspec status` reports

#### Scenario: Accepted forms per section
- **WHEN** guidance is written for ADDED or MODIFIED
- **THEN** it SHALL show `### Requirement: <name>` blocks, each with at least one `#### Scenario:`
- **WHEN** guidance is written for REMOVED
- **THEN** it SHALL show a `### Requirement: <name>` block with **Reason** and **Migration**
- **WHEN** guidance is written for RENAMED
- **THEN** it SHALL show `FROM:`/`TO:` pairs carrying the requirement header

### Requirement: In-panel links and buttons launch actions through the platform action pipeline

A link or button in the tool window, Getting Started panel, workflow panel or Setup Wizard that launches an action SHALL run it through the IDE's standard action pipeline, with a data context from the component the user used (or the project frame, once that component is disposed). The action's availability check SHALL run first. The plugin SHALL NOT use API deprecated on a supported IDE when a non-deprecated equivalent exists at the floor.

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

#### Scenario: Available while indexing
- **WHEN** the IDE is indexing
- **THEN** the Propose, Explore and delta-spec diff actions SHALL remain available, because they do not depend on the indexes

### Requirement: Archive target directory

When the plugin archives a change itself (no CLI, or a CLI older than 1.5), it SHALL move the change to `openspec/changes/archive/YYYY-MM-DD-<name>/`, using the local date and keeping a name that already starts with a date. It SHALL refuse, before writing any spec, when that target exists. It SHALL create `openspec/changes/archive/` and SHALL NOT create `openspec/archive/`.

#### Scenario: Dated archive name
- **WHEN** the user archives change `add-login` on 2026-10-17
- **THEN** the change SHALL be moved to `openspec/changes/archive/2026-10-17-add-login/`

#### Scenario: Target already exists
- **WHEN** `openspec/changes/archive/2026-10-17-add-login/` already exists
- **THEN** the archive SHALL be refused with no spec written and the change left active

#### Scenario: No archive directory yet
- **WHEN** `openspec/changes/archive/` does not exist
- **THEN** the plugin SHALL create it and archive into it, and SHALL NOT create `openspec/archive/`

#### Scenario: Legacy archive still read
- **WHEN** an existing legacy `openspec/archive/` is present
- **THEN** the plugin SHALL still read archived changes from it
