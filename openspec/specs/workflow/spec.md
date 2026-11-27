# Workflow

## Purpose
Artifact generation pipeline: propose, generate, apply, archive actions with delivery routing and progress feedback.

## Requirements

### Requirement: Propose action

Propose SHALL do what OpenSpec's `propose` workflow does: create a change and produce every artifact it needs before apply. It SHALL ask for a description. With Direct API it SHALL also offer a change name and a schema, create the change and generate the artifacts needed before apply from the description, and stop there. With clipboard or editor tab it SHALL hand the description to the AI tool's own propose workflow. Creation SHALL run off the EDT.

#### Scenario: Change creation
- **WHEN** the user proposes a change with Direct API as the delivery method
- **THEN** the plugin SHALL create the change off the EDT, as `openspec new change` does (through the CLI when it is available, without `--description`), switch the workflow panel to it, and generate the artifacts the change needs before apply, using the description as the request, leaving any other artifact ungenerated

#### Scenario: Schema selection during propose
- **WHEN** multiple schemas are available and the user proposes a change with Direct API
- **THEN** the Propose dialog SHALL display a schema combo box and the chosen schema SHALL be passed as `--schema "<schema>"` when the change is created

#### Scenario: No template design or tasks files
- **WHEN** a change is created without the CLI
- **THEN** no template `design.md` or `tasks.md` SHALL be written, because upstream never creates them and the CLI would report them as done

#### Scenario: Handoff to the AI tool's propose workflow
- **WHEN** the user proposes a change with clipboard or editor tab as the delivery method, and the project has the tool's generated propose skill or command
- **THEN** the dialog SHALL ask only for the description, and the plugin SHALL deliver that tool's propose invocation followed by the description, and SHALL NOT create the change itself

#### Scenario: Empty description in a handoff
- **WHEN** the user proposes with clipboard or editor tab and leaves the description empty
- **THEN** the plugin SHALL deliver the propose invocation alone, so the agent asks what to build

#### Scenario: Propose workflow not installed
- **WHEN** the user proposes with clipboard or editor tab and the project has no generated propose skill or command for any candidate tool
- **THEN** the plugin SHALL warn that the propose workflow is missing and offer **Update OpenSpec…**, and SHALL NOT create a change

#### Scenario: Direct API needs a description
- **WHEN** the user proposes with Direct API and the description is empty
- **THEN** the dialog SHALL NOT allow the request to be submitted

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

### Requirement: Revise change action

The plugin SHALL offer a "Revise Change..." action that hands an active change off to the user's AI tool's own `update` workflow, with an optional note saying what should change. It SHALL deliver only what the user types to run that workflow, the change name and the note, through Clipboard or Editor Tab, and SHALL NOT include the workflow's instructions. The action SHALL be gated on the `update` workflow and SHALL NOT be named "Update".

#### Scenario: Revise with a note
- **WHEN** the user chooses Revise Change... on an active change and enters "drop the export feature"
- **THEN** the plugin SHALL deliver the tool's update invocation, the change name and the note, for example `/opsx:update add-auth drop the export feature`

#### Scenario: Revise without a note
- **WHEN** the user leaves the note empty
- **THEN** the plugin SHALL deliver the invocation and the change name only, and the dialog SHALL say the agent will review the change's artifacts for coherence

#### Scenario: The tool's own invocation
- **WHEN** the user's tool has its update command or skill installed
- **THEN** the invocation SHALL follow upstream's rules for that tool and those files, as Explore's handoff does

#### Scenario: Update not installed
- **WHEN** the tool has neither its update skill nor its update command
- **THEN** the plugin SHALL deliver nothing and SHALL say to run Update OpenSpec… or add `update` to the profile, then retry

#### Scenario: Direct API
- **WHEN** the delivery mode is Direct API
- **THEN** the plugin SHALL NOT send a request, and SHALL deliver through Clipboard or Editor Tab with a note saying why

#### Scenario: Where it's offered
- **WHEN** an active change is selected
- **THEN** Revise Change... SHALL be on the change's right-click menu after Explore This Change..., on the workflow panel's change header, and in the OpenSpec menu, and SHALL NOT be offered for specs or delta specs

#### Scenario: Profile without update
- **WHEN** the active profile doesn't include the `update` workflow
- **THEN** Revise Change... SHALL be disabled, like other workflow-gated actions

### Requirement: Workflow pipeline panel

The panel SHALL show the selected change's pipeline as interactive artifact chips, the primary action surface. READY chips SHALL generate and DONE chips SHALL open the file, each with a right-click menu. A compact icon bar SHALL give secondary actions, and a one-line status strip SHALL show compliance, task progress and delivery mode. Its "no changes" card SHALL offer Propose and SHALL NOT offer Fast-Forward.

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

#### Scenario: No Fast-Forward on the empty card
- **WHEN** the "no changes" card renders, whatever the profile, delivery method or Direct API configuration
- **THEN** it SHALL offer Propose and SHALL NOT offer Fast-Forward, because Propose creates the change and generates its artifacts; Fast-Forward SHALL be reachable only through its menu and toolbar action

#### Scenario: FF input activation
- **WHEN** the user runs the Fast-Forward menu or toolbar action, which is enabled only when the active profile includes `ff`
- **THEN** the panel SHALL switch to the FF_INPUT card while keeping the tool selector visible

#### Scenario: Content views
- **WHEN** the panel shows content
- **THEN** a CardLayout SHALL switch between the NO_CHANGES, FF_INPUT and PIPELINE views

#### Scenario: Explore context
- **WHEN** the change selection changes
- **THEN** the Explore tab context SHALL include the selected change's details and follow the selection

### Requirement: New Change action

New Change SHALL do what OpenSpec's `new` workflow does: create the change without any artifact. It SHALL be gated on the `new` workflow, so on a profile without it the action is disabled with the profile suffix. It SHALL ask for a name and, when several schemas exist, a schema, and leave every artifact, the proposal first, for Continue or the workflow panel. Creation SHALL run off the EDT.

#### Scenario: Scaffold a change
- **WHEN** the user runs New Change with `new` in the active profile and submits the dialog
- **THEN** the plugin SHALL create the change as `openspec new change` does (through the CLI when it is available, without `--description`), with no artifact file, select the change in the workflow panel, and generate nothing

#### Scenario: Not in the profile
- **WHEN** the active profile does not include `new`, as on core
- **THEN** New Change SHALL be shown disabled with the profile suffix, as Fast-Forward and Continue are

### Requirement: Continue is offered beside Revise and Apply

On a profile that includes the `continue` workflow, the plugin SHALL offer Continue on an active change's right-click menu, directly after Revise Change, and as a workflow panel button after Revise. Launched for a change, Continue SHALL act on that change; it SHALL ask which change only when launched without one.

#### Scenario: Continue on the change's right-click menu
- **WHEN** the user right-clicks an active change in the Browse tree
- **THEN** the menu SHALL offer Explore This Change, Revise Change and Continue, then Apply and Archive, with Continue gated by the profile as Apply and Archive are

#### Scenario: Continue acts on the change it was launched for
- **WHEN** Continue is launched from a change's right-click menu or the workflow panel's button while several changes are active
- **THEN** it SHALL continue that change without asking which one

#### Scenario: Continue without a change asks
- **WHEN** Continue is launched from the OpenSpec menu while several changes are active
- **THEN** it SHALL ask which change to continue

### Requirement: The empty workflow panel points custom profiles at more workflows

When no change is active, the workflow panel's "No changes yet" card SHALL say "More workflows are in the OpenSpec menu." if the active profile is `custom` and includes a workflow outside the core set, and SHALL list those workflows in its tooltip. It SHALL say nothing more on the core profile.

#### Scenario: Custom profile with more workflows
- **WHEN** the active profile is `custom` and includes `continue` and `verify`
- **THEN** the card SHALL show the hint, and its tooltip SHALL name continue and verify

#### Scenario: Core profile
- **WHEN** the active profile is `core`, or `custom` with only core workflows
- **THEN** the card SHALL show only "No changes yet.", Propose a change and Explore an idea first
