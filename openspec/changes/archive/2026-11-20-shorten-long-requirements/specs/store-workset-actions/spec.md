## MODIFIED Requirements

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
