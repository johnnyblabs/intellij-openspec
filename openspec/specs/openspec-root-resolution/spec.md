# openspec-root-resolution Specification

## Purpose
Find the project's OpenSpec root the way the OpenSpec CLI does, so every surface works on the files the CLI works on: the project's own `openspec/`, one in a parent folder, a registered store the project's config points at, or the machine-wide default store. Resolution reads only files on disk, runs off the UI thread, and reports why a named store can't be used.

## Requirements
### Requirement: Root resolution order

The plugin SHALL resolve the project's OpenSpec root in the CLI's order: the nearest qualifying `openspec/` in the project directory or its ancestors, then the `store:` pointer in that directory's config, then the global `defaultStore`, then no root. A directory qualifies when it holds a `specs/` or `changes/` directory or a `config.yaml` or `config.yml` file. Artifacts SHALL be read from `<root>/openspec`.

#### Scenario: A local planning tree is the root
- **WHEN** `<project>/openspec` has a `specs/` or `changes/` directory
- **THEN** the resolved root SHALL be the project with source `nearest`

#### Scenario: An ancestor's planning tree is the root
- **WHEN** the project directory has no qualifying `openspec/` and its parent directory's `openspec/` has a `changes/` directory
- **THEN** the resolved root SHALL be the parent directory with source `nearest`, as the CLI run from the project directory resolves it

#### Scenario: A local planning tree wins over a store pointer
- **WHEN** `<project>/openspec` has a `changes/` directory and its config also declares `store: demo`
- **THEN** the resolved root SHALL be the project with source `nearest`, and the plugin SHALL log the CLI's warning that the declaration is ignored

#### Scenario: A config-only directory with a pointer resolves to the store
- **WHEN** `<project>/openspec` holds only a config declaring `store: demo`, and `demo` is registered at `<store>`
- **THEN** the resolved root SHALL be `<store>` with source `declared` and store id `demo`

#### Scenario: The global default store applies when nothing local qualifies
- **WHEN** no local `openspec/` qualifies and the global config sets `defaultStore` to a registered store
- **THEN** the resolved root SHALL be that store with source `global_default`

#### Scenario: A bare directory does not qualify
- **WHEN** `<project>/openspec` exists but is empty, and no default store is set
- **THEN** the plugin SHALL resolve no root and SHALL treat the project as not set up

### Requirement: Resolution reads only files on disk

The plugin SHALL resolve the root from files alone: the project config, the global config `<config-dir>/openspec/config.json`, and the store registry `<data-dir>/openspec/stores/registry.yaml`. It SHALL NOT need the CLI. The config dir SHALL be `$XDG_CONFIG_HOME` when set, otherwise `~/.config` on macOS and Linux and `%APPDATA%` on Windows. All paths SHALL be compared after canonicalization.

#### Scenario: Resolve without a CLI
- **WHEN** no CLI is installed and the project declares a registered store
- **THEN** the plugin SHALL resolve the store root from the files on disk

#### Scenario: Matches the CLI's resolution
- **WHEN** a captured CLI `context --json` reports a root for a fixture layout
- **THEN** the plugin's resolution of the same layout SHALL report the same path, source and store id

#### Scenario: Paths are canonicalized
- **WHEN** the registered store path and the actual path differ only by a symlink such as `/tmp` and `/private/tmp`
- **THEN** the plugin SHALL treat them as the same root

### Requirement: Store sources follow the CLI release

The plugin SHALL honor a `store:` pointer only from CLI 1.5.0 and the global `defaultStore` only from CLI 1.7.0, judged against the release it follows: the detected CLI, or its target release when no CLI is detected. Below 1.5.0 only a local root SHALL resolve.

#### Scenario: Pointer ignored before 1.5.0
- **WHEN** the followed release is 1.4.1 and the project config declares a registered store
- **THEN** the plugin SHALL NOT resolve the store

#### Scenario: Default store ignored before 1.7.0
- **WHEN** the followed release is 1.5.0, nothing local qualifies, and `defaultStore` is set
- **THEN** the plugin SHALL NOT resolve the default store

#### Scenario: Default store honored from 1.7.0
- **WHEN** the followed release is 1.7.0 or later, nothing local qualifies, and `defaultStore` names a registered store
- **THEN** the plugin SHALL resolve that store with source `global_default`

### Requirement: Store root checks and problems

A store root SHALL be used only when the id is registered, `<store>/.openspec-store/store.yaml` names the same id, and `<store>/openspec` is a directory with a config file. Otherwise the plugin SHALL resolve no root and show the problem with the CLI's code (`unknown_store`, `invalid_store_pointer`, `store_identity_mismatch` or `unhealthy_store_root`) and a fix.

#### Scenario: Declared store is not registered
- **WHEN** the project config declares `store: demo` and no store `demo` is registered
- **THEN** the plugin SHALL show an `unknown_store` problem naming the config file, and SHALL NOT show an empty tree as if the project had no specs

#### Scenario: Malformed pointer
- **WHEN** the project config's `store:` value is not a string or the config does not parse
- **THEN** the plugin SHALL show an `invalid_store_pointer` problem

#### Scenario: Store identity mismatch
- **WHEN** the registered path's `.openspec-store/store.yaml` is missing or names a different id
- **THEN** the plugin SHALL show a `store_identity_mismatch` problem

#### Scenario: Unhealthy store root
- **WHEN** the registered store has no `openspec/` directory or no config file in it
- **THEN** the plugin SHALL show an `unhealthy_store_root` problem

### Requirement: Every surface uses the resolved root

The spec tree, the changes list, validation, archive, Sync Specs, the workflow panel, Explore's context, new-change creation, file icons and inspections SHALL read and write artifacts under the resolved root. Project initialization SHALL keep writing the local `<project>/openspec`. When `<project>/openspec` has a planning tree, the resolved root SHALL be the project, so behavior SHALL be unchanged.

#### Scenario: The tree shows the store's specs
- **WHEN** the project declares a registered store that has specs and changes
- **THEN** the spec tree and changes list SHALL show the store's specs and changes

#### Scenario: Built-in validation reads the store
- **WHEN** the project resolves to a store and validation runs without a CLI
- **THEN** the built-in validator SHALL validate the store's changes and specs

#### Scenario: Sync Specs writes into the store
- **WHEN** the user syncs a change in a store project
- **THEN** the merged main spec SHALL be written under `<store>/openspec/specs`

#### Scenario: Unchanged without a store
- **WHEN** the project has a local `openspec/` with specs and changes
- **THEN** every surface SHALL read the same paths as before this change

#### Scenario: A pointer-only config is not nudged to add a schema
- **WHEN** `<project>/openspec/config.yaml` declares `store: demo` and no `schema:`
- **THEN** the config inspection SHALL NOT suggest adding a `schema` field, because the store's config governs the schema

#### Scenario: Init writes locally
- **WHEN** the user initializes OpenSpec in a project
- **THEN** the plugin SHALL create the local `<project>/openspec`, never a store tree

### Requirement: Root resolution stays off the UI thread

Resolution SHALL run on a background thread and be cached. Reading the cached root SHALL never touch the disk, refresh the file system or run the CLI, so actions, inspections and file-event handlers may read it. Until the first resolution finishes, the tool window SHALL show a resolving state rather than an empty or local tree.

#### Scenario: Reading the root does no work
- **WHEN** an action update, an inspection or a file-event handler reads the root
- **THEN** it SHALL get the cached value without file-system refresh, disk reads or a CLI run

#### Scenario: Resolving state at startup
- **WHEN** the tool window opens before the first resolution finishes
- **THEN** it SHALL show a resolving state and then the resolved tree, without first showing a different root

### Requirement: Root changes are picked up

The plugin SHALL re-resolve when the local `openspec/` or its config changes, when the global config or the store registry changes, when the resolved root is deleted, and on Refresh. It SHALL watch the resolved `<root>/openspec` for file changes even when it lies outside the project, and stop watching a root it no longer uses.

#### Scenario: Adding a pointer switches to the store
- **WHEN** the user adds `store: demo` to a config-only `openspec/config.yaml` and saves
- **THEN** the plugin SHALL re-resolve and the tree SHALL show the store

#### Scenario: Store file changes appear
- **WHEN** a change is created in the store's `openspec/changes` outside the IDE
- **THEN** the changes list SHALL show it after the file system reports the change, or after Refresh

#### Scenario: Old watch released
- **WHEN** the resolved root changes from one store to another
- **THEN** the plugin SHALL stop watching the old store's tree

### Requirement: Root paths come from the resolver

Plugin code SHALL obtain OpenSpec root paths only from the resolver. A hygiene test SHALL fail when code outside the resolver and an allow-list builds an `openspec` root path by hand.

#### Scenario: A hand-built path fails the build
- **WHEN** a class outside the allow-list builds `basePath + "/openspec/changes"`
- **THEN** the hygiene test SHALL fail naming the file and line

### Requirement: Where the specs live is shown

When the resolved root isn't the project's own `openspec/`, the Browse tree's root label SHALL name it: a store by id with its source (`declared` or `global default`), or `parent folder`. Its tooltip SHALL give the root's path and, for a root outside the project, say to commit changes from that folder's own repository. A project's own root SHALL keep the plain label.

#### Scenario: A declared store is named
- **WHEN** the project's config points at the registered store `demo`
- **THEN** the tree's root label SHALL read `OpenSpec · store demo (declared)` and its tooltip SHALL give the store's path and the commit advice

#### Scenario: The global default store is named
- **WHEN** the root resolves from the global `defaultStore` `demo`
- **THEN** the tree's root label SHALL read `OpenSpec · store demo (global default)`

#### Scenario: A parent folder's root is named
- **WHEN** the root is a parent folder's `openspec/`
- **THEN** the tree's root label SHALL read `OpenSpec · parent folder` and its tooltip SHALL give that folder's path

#### Scenario: A project's own root is unchanged
- **WHEN** the project has its own planning tree
- **THEN** the tree's root label SHALL read `OpenSpec` with no provenance

### Requirement: An ignored store pointer is shown

When the project's config names a store but the project's own planning tree wins, the plugin SHALL show a hint in the Browse tree saying the store is ignored and why. It SHALL notify once when that state begins, not on every refresh.

#### Scenario: Pointer next to a local planning tree
- **WHEN** `openspec/config.yaml` declares `store: demo` and `openspec/changes/` exists in the project
- **THEN** the tree SHALL show a hint that store `demo` is ignored because the project has its own `openspec/` tree

#### Scenario: Notified once
- **WHEN** the ignored-pointer state begins and the tree then refreshes several times
- **THEN** the plugin SHALL show one notification about it, not one per refresh

### Requirement: Store files are edited without the not-in-project prompt

Files under the resolved root that lie outside the project SHALL be writable without the platform's "file does not belong to the project" prompt. Such a file SHALL show an editor banner naming the store or folder and saying to commit changes from its own repository. The banner SHALL be dismissible for the project. Deciding either SHALL NOT read the disk or run the CLI.

#### Scenario: Typing in a store spec
- **WHEN** the user edits a spec in the resolved store
- **THEN** no "does not belong to the project" prompt SHALL appear

#### Scenario: The banner names the store
- **WHEN** the user opens a file in the resolved store `demo`
- **THEN** an editor banner SHALL say the file is in OpenSpec store `demo`, outside this project, and to commit changes from the store's own repository

#### Scenario: Files elsewhere keep the platform's prompt
- **WHEN** the user edits a file outside both the project and the resolved root
- **THEN** the platform's own prompt SHALL behave as before

### Requirement: A tree hint never initializes over a store

Activating a hint under the Browse tree's root SHALL run Initialize only for the hint that says the project isn't set up. The hints for an unusable store and for an ignored store pointer SHALL NOT initialize, because a new local planning tree would silently override the store.

#### Scenario: The not-set-up hint initializes
- **WHEN** the project resolves no root and has no store problem, and the user double-clicks the tree's hint
- **THEN** the plugin SHALL run Initialize

#### Scenario: The store-problem hint doesn't
- **WHEN** the project points at a store that can't be used and the user double-clicks the tree's hint
- **THEN** the plugin SHALL NOT run Initialize and SHALL NOT create `openspec/specs` or `openspec/changes` in the project

#### Scenario: The ignored-pointer hint doesn't
- **WHEN** the tree shows the ignored-pointer hint and the user double-clicks it
- **THEN** the plugin SHALL NOT run Initialize

### Requirement: Store problems name the store

When a declared or default store can't be used and its id is known, the Getting Started card and the Browse tree hint SHALL name it, for example "The OpenSpec store 'demo' can't be used", followed by the CLI's message and fix.

#### Scenario: Unhealthy store named
- **WHEN** the project points at registered store `sick`, whose root has no `openspec/` directory
- **THEN** the card title SHALL read "The OpenSpec store 'sick' can't be used"
