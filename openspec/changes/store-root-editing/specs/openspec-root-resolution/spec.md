## ADDED Requirements

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
