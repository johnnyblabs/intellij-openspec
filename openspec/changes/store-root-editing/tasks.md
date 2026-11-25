# Tasks

## 1. Wording

- [ ] 1.1 `RootProvenance` (pure, `root` package) builds the tree root label, tooltip, ignored-pointer text, banner text and problem title from a `RootResolution` and the project base path (design D1). `RootResolution.Problem` exposes the store id.

## 2. Surfaces

- [ ] 2.1 The tree root becomes a `TreeNodeData` of a new `ROOT` type, labelled and tooltipped by `RootProvenance`, with path id `"root"`. `iconForType` gains a `ROOT` case. The panel's string-root check moves to the type, and `TreeRefreshStateTest` asserts the type (D2).
- [ ] 2.1a Root-level hints carry a kind (`NOT_SET_UP`, `STORE_PROBLEM`, `IGNORED_POINTER`). Double-click runs Initialize only for `NOT_SET_UP` (D3a).
- [ ] 2.2 The ignored-pointer hint node, and a one-time `OpenSpec.System` notification on the transition into that state, detected in the resolver (D3).
- [ ] 2.3 `nonProjectFileWritingAccessExtension` (project area, confirmed on 242): writable when the file is under the resolved root and outside the project. In-memory check only, never creates the service (D4).
- [ ] 2.4 `editorNotificationProvider` (`DumbAware`, `collectNotificationData`, confirmed on 242) for files under a resolved root outside the project, with a "Don't show again for this project" link. `EditorNotifications.updateAll()` on a root change (D5).
- [ ] 2.5 The Getting Started problem card title and the tree hint name the store when its id is known (D6).

## 3. Tests

- [ ] 3.1 `RootProvenanceTest` covering each source, root inside or outside the project, the warning, and problems with and without an id.
- [ ] 3.2 Platform tests:
  - the tree root label and tooltip per source, and expansion kept when the label changes;
  - double-click on each root-level hint kind: only the not-set-up hint initializes;
  - the ignored-pointer hint, and one notification across refreshes;
  - `isWritable` true for a store file, false for an unrelated non-project file, false without the service (with the platform's non-project checks enabled in tests);
  - the banner shown, hidden and dismissed;
  - banners refreshed on a root change.
- [ ] 3.3 The store uiSmoke journey also asserts the root label and the banner on an opened store file.
- [ ] 3.4 Mutations, each turning a test red:
  - the label shown for a local root;
  - the warning notified on every refresh;
  - `isWritable` true for every non-project file;
  - the banner shown for project files;
  - the store id dropped from the problem title;
  - the store-problem hint initializes on double-click;
  - the root's path id derived from its label.
- [ ] 3.5 New plain test classes run alone pass.

## 4. Verification and docs

- [ ] 4.1 `./gradlew build`, `./gradlew verifyPlugin`, and the full `caffeinate -dimsu ./gradlew uiSmoke`.
- [ ] 4.2 CHANGELOG (Unreleased) and `docs/feature-reference.md` (the store section gains the label, the banner and the ignored-pointer hint).
- [ ] 4.3 `openspec validate --all --strict` on the branch.
