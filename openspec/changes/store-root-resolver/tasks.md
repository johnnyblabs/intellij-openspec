# Tasks

## 1. Captures

- [x] 1.1 `scripts/capture-store-root.py`, built on `scripts/lib/openspec_capture.py`:
  - installs each release into scratch (never a pinned `npx`);
  - runs with an isolated `HOME`, `XDG_DATA_HOME` and `XDG_CONFIG_HOME`;
  - builds the layouts in design D7;
  - records `context --json`, stderr and the exit code, plus a layout manifest;
  - sanitizes paths to `<W>`.
- [x] 1.2 Capture 1.5.0, 1.7.0, 1.15.0, 2.0.0, 2.1.0 and 2.1.1 into `src/test/resources/fixtures/cli/<ver>/store-root/`, and record the runs in the fixtures README.

## 2. Resolver

- [x] 2.1 Add a global config path helper (`$XDG_CONFIG_HOME`, `~/.config`, `%APPDATA%`). Read `defaultStore` from `config.json`, reusing `CoordinationPaths` and the store registry parser for `local_path`.
- [x] 2.2 Add `OpenSpecRootResolver` with:
  - the resolution order, including the upward walk from the project directory;
  - the qualifying-directory rule;
  - the planning-shape rule that excludes a store kept at `specs/` or `changes/`;
  - the store checks with the CLI's problem codes;
  - the pointer-ignored warning;
  - release gates of 1.5.0 for the pointer and 1.7.0 for the default store, against `ValidationRelease.followed` (design D1–D3).
- [x] 2.3 Add the snapshot service, the `OpenSpecRoot` value object and the `OpenSpecRootListener` topic. Resolution runs on a debounced pooled `Alarm`, refreshes outside any read action, and warms up from the startup activity (D4).
- [x] 2.4 Watching and invalidation: watch roots swapped and released, the resolver's own `BulkFileListener`, and `invalidate()` from Refresh and the `CoordinationService` store writes (D6).

## 3. Surfaces

- [x] 3.1 `OpenSpecFileUtil` delegates to the snapshot. Remove the refresh fallbacks, and make `isUnderOpenSpec` ancestor-based (D5).
- [x] 3.2 Tool window and Getting Started:
  - show a resolving state while `PENDING`;
  - rebuild on `rootChanged`;
  - show the problem's message and fix as the empty state.
- [x] 3.3 Move the hand-built paths onto `OpenSpecRoot`:
  - workflow panel and `ArtifactFileWatcher`;
  - new-change action;
  - `ExploreContextService`;
  - `VerificationService`;
  - `ConfigService`;
  - `ArchiveReadinessService`;
  - `ArtifactOrchestrationService`;
  - `SpecSyncService`;
  - `BuiltInValidator`;
  - `SkillsScopeLine`;
  - the icon provider, the validate-from-project-view action, `ConfigValidationInspection`, `ValidationConsoleFormatter` and the remaining display strings (root-relative).

  `ScaffoldingService` keeps the local anchor.
- [x] 3.4 `DeltaSpecInspection` reads the main spec through the cached snapshot only.
- [x] 3.5 `ConfigValidationInspection` skips its missing-`schema` nudge on a pointer-only config (D5).

## 4. Tests

- [x] 4.1 `StoreRootContractTest` replays every captured layout on every captured release. It must match the CLI's path, source, store id and problem code, and on 1.5.0 the global default must not resolve.
- [x] 4.2 Resolver unit tests on real temp directories:
  - pointer, default store and bare directory;
  - an ancestor root, and a module content root found when the base directory has none;
  - each problem code, including the ones the captures can't reach (`no_registered_stores`, an unreadable metadata file, a store root that is a file);
  - a symlinked store path;
  - no CLI follows the target release.
- [x] 4.3 Surface tests in a store project:
  - the tree and changes list show the store;
  - built-in validation reads the store;
  - Sync Specs writes under `<store>/openspec/specs`;
  - init writes locally;
  - a project without a store reads unchanged paths.
- [x] 4.4 Threading and invalidation tests:
  - `snapshot()` does no I/O (fails on a disk read or refresh);
  - adding a pointer re-resolves;
  - the old watch is released on a root change.
- [x] 4.5 `OpenSpecRootLiteralHygieneTest` with its allow-list (D8).
- [x] 4.6 Mutations, each turning a test red:
  - the pointer is honored over a planning shape;
  - the default store is honored before 1.7.0;
  - the identity check is skipped;
  - canonicalization is dropped;
  - `snapshot()` refreshes;
  - a surface keeps a hand-built path.

- [x] 4.7 uiSmoke journey `aProjectPointingAtAStoreShowsTheStore`: isolated XDG data and config, store set up by the host CLI (1.5+, skipped below), project with only a pointer. It checks the store's change lists, a change made outside the IDE appears when the user returns to the IDE (platform sync, not Refresh), a pointer to an unregistered store makes the tree explain it, and fixing the pointer brings the store back. The tree's no-root hint names the store problem instead of offering init.

## 5. Verification and docs

- [x] 5.1 `./gradlew build` and `./gradlew verifyPlugin` (new `LocalFileSystem` watch and refresh references).
- [x] 5.2 Check in IDEA and in a non-IDEA IDE that a store project works and that inspections fire on a store spec:
  - IDEA: the uiSmoke journeys `aProjectPointingAtAStoreShowsTheStore` (tree, outside change, broken and fixed pointer) and `inspectionsRunOnAStoreSpec` (the keyword inspection fires on a spec in the store, outside the project) pass.
  - Non-IDEA: a free PyCharm can't be booted by the test framework (its 2024.2 Community download is the Professional build, which needs a licence). The risk was a bundled highlighting filter that skips non-project files, so the 2024.2 builds were scanned instead. IntelliJ IDEA Community registers four language-specific filters (Java, Kotlin, Maven archetypes) and the journey passes with them. PyCharm registers none, so nothing there can silence an inspection on a store spec.
- [x] 5.3 CHANGELOG (Unreleased) and `docs/feature-reference.md`. Note that WSL-hosted stores are unsupported.
- [x] 5.4 `openspec validate --all --strict` on the branch.
