# store-workset-actions Specification

## Purpose

Define the IDE write actions for OpenSpec's 1.5.0+ store/workset model — creating, registering, opening, and removing stores and worksets — including how they are tier- and version-gated and kept off the EDT, so the plugin mutates coordination state by delegating to the CLI and never reimplements it.

## Requirements

### Requirement: Gating of store and workset write actions

Store and workset write actions SHALL be enabled only at the Full coordination tier and with CLI 1.5.0 or later. Otherwise they SHALL be disabled or hidden with guidance, and no write command SHALL run. Each action's `update()` SHALL read only cached state, with no CLI call or disk I/O on the EDT.

#### Scenario: Write actions enabled at Full tier on CLI 1.5.0+
- **WHEN** the coordination surface is at the Full tier and the detected CLI is at or above 1.5.0
- **THEN** the store and workset write actions SHALL be enabled, subject to the selected node matching each action's target type

#### Scenario: Write actions disabled below CLI 1.5.0
- **WHEN** the detected CLI is below 1.5.0 or the surface is below the Full tier
- **THEN** the store and workset write actions SHALL be disabled with guidance explaining the CLI requirement, and no write command SHALL be invoked

#### Scenario: Action update touches no CLI or IO on the EDT
- **WHEN** an `update()` is evaluated for a store or workset write action
- **THEN** it SHALL determine enablement from cached tier and selection state only, without invoking the CLI or reading disk

### Requirement: New Store dialog with mandatory path

The plugin SHALL create a store through a dialog that collects a store id and a folder path, because the 1.5.0 `store setup` command requires `--path`. The dialog SHALL validate its input and block confirmation while the store id is blank or the folder path is blank or does not exist. On confirmation the plugin SHALL run `openspec store setup <id> --path <path> --json` on a background thread.

#### Scenario: Confirmation blocked on missing input
- **WHEN** the New Store dialog has a blank store id or a blank or non-existent folder path
- **THEN** the dialog SHALL block confirmation and indicate the invalid field

#### Scenario: Store created with an explicit path
- **WHEN** the user confirms the New Store dialog with a non-blank id and an existing folder
- **THEN** the plugin SHALL run `openspec store setup <id> --path <path> --json` off the EDT and refresh the surface on success

#### Scenario: Missing-path error surfaced as a field message
- **WHEN** a `store setup` invocation returns the `store_setup_path_required` error
- **THEN** the plugin SHALL surface it as a field-level message using the error's `fix` text rather than dumping raw stderr

### Requirement: Store registration outcome semantics across CLI generations

The plugin SHALL treat `openspec store register --json` outcomes by the CLI's parsed report, not by assumptions about root health. A registration the CLI reports as successful SHALL succeed even when the planning directories are absent. A refusal SHALL be shown from the parsed `status[]` envelope with its `fix` verbatim. The plugin SHALL NOT special-case a refusal code in a way that breaks when that code is absent on another supported CLI generation.

#### Scenario: Fresh root registers successfully on CLI 1.6+
- **WHEN** `store register` succeeds for a root that lacks the planning directories
- **THEN** the plugin SHALL treat the registration as a success and refresh the store listing, with the new store presented without any unhealthy or error marker

#### Scenario: Healthy not-yet-a-store root prompts, then registers on confirmation
- **WHEN** Register Existing Store is invoked on a healthy OpenSpec root that does not yet carry `.openspec-store/store.yaml`, and the probe `store register <path> --json` returns `store_register_identity_confirmation_required`
- **THEN** the plugin SHALL present a confirmation carrying the CLI's `message` and `fix`, and on confirmation SHALL invoke `store register <path> --yes --json`, the CLI SHALL create the store-identity metadata and report success, and the plugin SHALL treat it as success and refresh the surface

#### Scenario: Cancelling the identity confirmation registers nothing
- **WHEN** the user declines the identity confirmation
- **THEN** the plugin SHALL NOT invoke `store register` with `--yes`, SHALL leave the root unregistered, and SHALL NOT display an error dialog

#### Scenario: Pointer-root refusal surfaced with its fix
- **WHEN** `store register` fails with `invalid_store_pointer` or `store_root_pointer_declared`
- **THEN** the plugin SHALL surface the parsed `status[]` message and its `fix` string verbatim, and SHALL NOT display raw CLI stderr

#### Scenario: 1.5-generation unhealthy-root refusal still parsed
- **WHEN** a 1.5-generation CLI fails `store register` with `store_register_root_unhealthy`
- **THEN** the plugin SHALL surface the parsed failure message and its `fix` remediation exactly as before

#### Scenario: Probe without --yes
- **WHEN** the initial `store register` runs
- **THEN** it SHALL NOT pass `--yes`, so the plugin never silently writes store-identity metadata

#### Scenario: Identity confirmation
- **WHEN** the CLI reports `store_register_identity_confirmation_required` (a healthy root that is not yet a store)
- **THEN** the plugin SHALL present a Yes/Cancel confirmation carrying the CLI's `message` and `fix` text verbatim, not a dead-end failure
- **AND** on Yes it SHALL re-invoke `store register <path> --yes --json`, creating the store-identity metadata (`.openspec-store/store.yaml`), and refresh the surface
- **AND** on Cancel it SHALL leave the root unregistered and show no error dialog

#### Scenario: Refusal codes
- **WHEN** the CLI refuses a registration with `invalid_store_pointer`, with `store_root_pointer_declared` (the root's `openspec/config.yaml` declares `store:`), or with the 1.5-generation code `store_register_root_unhealthy`
- **THEN** the plugin SHALL show the entry's `fix` verbatim

### Requirement: New Workset dialog with member list

The plugin SHALL create a workset through a dialog that collects a workset name and an editable list of members, where each member is a name and a folder. The dialog SHALL block confirmation while the name is blank or any member row is incomplete. On confirmation the plugin SHALL run `openspec workset create <name> --member name=path …` with one `--member` argument per row, on a background thread.

#### Scenario: Add and remove members
- **WHEN** the user adds or removes member rows in the New Workset dialog
- **THEN** the dialog SHALL reflect the current member list and each row SHALL capture a member name and a member folder

#### Scenario: Confirmation blocked on incomplete input
- **WHEN** the workset name is blank or any member row is missing its name or folder
- **THEN** the dialog SHALL block confirmation and indicate the invalid input

#### Scenario: Workset created from members
- **WHEN** the user confirms the New Workset dialog with a name and one or more complete members
- **THEN** the plugin SHALL run `openspec workset create <name> --member name=path …` off the EDT and refresh the surface on success

### Requirement: Action toolbar and context menu

The plugin SHALL present store and workset actions through an `ActionToolbar` of `AnAction`s and a tree right-click context menu, replacing the prior plain-button toolbar. The available actions SHALL be New Store, Register Existing Store, per-store Doctor, Open Root, Unregister, and Remove; and New Workset, Open, and Remove. Actions whose target type does not match the current selection SHALL be disabled or hidden.

#### Scenario: Toolbar and context menu expose the actions
- **WHEN** the coordination surface is displayed at the Full tier on CLI 1.5.0+
- **THEN** the store and workset actions SHALL be reachable from both the action toolbar and the tree right-click menu

#### Scenario: Selection-sensitive enablement
- **WHEN** a store node is selected
- **THEN** the per-store actions (Doctor, Open Root, Unregister, Remove) SHALL be enabled and the workset-specific actions SHALL be disabled, and vice versa for a workset selection

### Requirement: Guarded destructive removal

The plugin SHALL guard destructive removals with an explicit confirmation before invoking the CLI. Removing a store SHALL warn that it deletes the store's local files (`store remove <id>`). Removing a workset SHALL confirm the removal while stating that member folders are not touched (`workset remove <name>`).

#### Scenario: Store removal warns about file deletion
- **WHEN** the user invokes Remove on a store
- **THEN** the plugin SHALL show a confirmation naming that local files will be deleted, and SHALL only run `store remove <id>` if confirmed

#### Scenario: Workset removal states members are preserved
- **WHEN** the user invokes Remove on a workset
- **THEN** the plugin SHALL show a confirmation stating that member folders are not touched, and SHALL only run `workset remove <name>` if confirmed

### Requirement: Workset open reveals member folders

The plugin SHALL open a workset by revealing its member folders in the OS file manager, after an explicit confirmation stating how many folders will open. It SHALL NOT use `--code-workspace` and SHALL NOT auto-open a workset on load. Folder resolution and VFS refresh SHALL run off the EDT.

#### Scenario: Confirm before opening multiple folders
- **WHEN** the user invokes Open on a workset with N member folders
- **THEN** the plugin SHALL show a confirmation stating that N folders will open before opening anything

#### Scenario: Members revealed in the file manager
- **WHEN** the user confirms opening a workset
- **THEN** the plugin SHALL reveal each existing member folder in the OS file manager, without using the `--code-workspace` flag

#### Scenario: No auto-open on load
- **WHEN** the coordination surface loads or refreshes
- **THEN** the plugin SHALL NOT open any workset's folders automatically

#### Scenario: Deferred in-IDE integration
- **WHEN** a multi-folder or attached-project integration is considered
- **THEN** it is deferred, because the platform's attach-to-project API is not resolvable across the minimum supported IDE build, so a portable reveal is used for this release

### Requirement: Doctor-driven health strip

The plugin SHALL display a health strip at the top of the coordination surface that renders the highest-severity entry from the uniform `status:[{severity,code,message,target,fix}]` array returned by `doctor`. The strip SHALL expose that entry's `fix` string verbatim as an inline actionable link and SHALL be hidden when only informational entries exist. The plugin SHALL NEVER present raw stderr to the user; suggested actions come only from `fix`.

#### Scenario: Highest-severity status rendered with its fix
- **WHEN** `doctor` returns a `status[]` array containing one or more non-informational entries
- **THEN** the health strip SHALL render the highest-severity entry's message and expose its `fix` string verbatim as an inline action

#### Scenario: Strip hidden when healthy
- **WHEN** `doctor` returns only informational status entries or none
- **THEN** the health strip SHALL be hidden

#### Scenario: Raw stderr never surfaced
- **WHEN** a store or workset write or doctor invocation fails
- **THEN** the plugin SHALL surface the parsed `status[]` message and `fix`, and SHALL NOT display raw CLI stderr

### Requirement: Off-EDT execution of write actions

All store and workset write and doctor invocations SHALL run on a background thread, and all resulting UI updates SHALL be applied via `invokeLater`. Where opening or attaching folders requires a platform mutation, the plugin SHALL wrap it in a `WriteAction`. No store or workset command SHALL be invoked on the EDT.

#### Scenario: Write runs off the EDT
- **WHEN** the user confirms a store or workset write action
- **THEN** the CLI invocation SHALL run on a background thread and the surface SHALL be refreshed via `invokeLater` on completion

#### Scenario: Failure leaves the surface intact
- **WHEN** a store or workset write action's CLI invocation fails
- **THEN** the plugin SHALL surface the parsed `status[]`/`fix` message and leave the existing listing intact
