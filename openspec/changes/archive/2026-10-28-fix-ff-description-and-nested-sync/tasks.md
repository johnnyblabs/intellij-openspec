# Tasks

## 1. Sync Specs reads nested capabilities

- [x] 1.1 Capture the contract fixture from the real CLI 1.15.0 in an isolated `HOME`/`XDG_*`: a project with existing `openspec/specs/identity/sessions/spec.md`, and a change with ADDED deltas at `specs/flat/`, `specs/identity/sessions/` and `specs/identity/user-auth/`; record the change's delta files, the main specs before, and the main specs `openspec archive <change> --yes` wrote. Add a re-capture script under `scripts/` and commit the sanitized output under `src/test/resources/fixtures/cli/1.15.0/archive-nested/`. Verify: the fixture's `identity/user-auth/spec.md` starts `# identity/user-auth Specification`.
- [x] 1.2 Make `SpecSyncService.readDeltaSpecs` walk the change's `specs/` folder recursively, keying each `spec.md` by its folder path relative to `specs/` joined with `/` (VFS relative path, never `Path.toString()`), skipping dot-folders, kept in a `TreeMap`. Verify: `NestedCapabilitySyncContractTest` (a platform test on a real temp directory, since the Mockito-based `SpecSyncServiceTest` has no file system) asserts keys `flat`, `identity/sessions`, `identity/user-auth` in that order, and `hasDeltaSpecs` is true for a change whose only delta is nested; both fail against the one-level loop.
- [x] 1.3 Add a contract test that runs `computeSyncPreview` and `applySync` over the 1.1 fixture and asserts every written main spec is byte-identical to the CLI's archive output, including the new nested file and its created `identity/user-auth/` folder, and that unrelated main specs are untouched. Verify: the test fails if 1.2 is reverted.
- [x] 1.4 Correct the wording that calls Sync Specs a view/diff utility: the `sync` reason in `WorkflowParityContractTest.NOT_SURFACED` and the comment in `OpenSpecSyncAction`, saying it is the built-in merge that writes main specs and why it isn't gated on the workflow. Verify: `./gradlew test --tests '*WorkflowParityContractTest*'` passes.

## 2. Fast-Forward passes the description as the request

- [x] 2.1 Add `ArtifactInstruction.REQUEST_HEADING` and prompt overloads taking a request (`buildPrompt(request, note)`, `buildDirectApiPrompt(request, note)`): the request section comes after the instructions and before any note; a null or blank request leaves the prompt byte-identical. Verify: `RegenerationNotePromptTest` (every captured instructions fixture) asserts placement, the heading differs from `NOTE_HEADING`, and the no-request prompt equals the existing one.
- [x] 2.2 Add `DirectApiService.generate(instruction, request)` and a `generateAllRemaining(changeName, apiService, listener, request)` overload that passes the request to every artifact in that run; the existing signatures delegate with `null`. Verify: `ArtifactOrchestrationServiceTest` asserts all three artifacts of a run get the request and a run without one uses the plain call; `DirectApiServiceTest` asserts the request is in the body sent to the provider and a request-less body is unchanged.
- [x] 2.3 In `WorkflowActionPanel.onFfGo`, keep the description and pass it to the generation it triggers: `generateAllRemaining(..., request)` for Direct API and `executeGeneration(mode, note, regenerate, request)` for clipboard and editor tab. Leave the `new change` arguments unchanged (no `--description`). The hand-off after creation is the package-private `startFastForwardGeneration(changeName, description)`, which takes the (possibly overridden) name and the description separately; the naming code is unchanged and isn't re-tested here, because `new change` runs through a static CLI call on a background thread that a test can't stub. Verify: `FastForwardRequestPanelTest`, on captured 1.15.0 new-change status, asserts the clipboard, editor-tab and Direct API deliveries carry the request and a later Generate does not; dropping the description from either path fails three of its four tests.
- [x] 2.4 Update `docs/feature-reference.md` where it describes Fast-Forward to say the description is sent as the request for every artifact the run generates, and add a `### Fixed` entry to `CHANGELOG.md` `## Unreleased` for each defect (user-facing wording, no tracker references). Verify: `./gradlew test --tests '*DocumentationHygieneTest*'` passes.

## 3. Verification

- [x] 3.1 Run `./gradlew build` (tests plus the coverage floor) and `openspec validate fix-ff-description-and-nested-sync --strict`. Verify: both succeed.
- [x] 3.2 Leak-check the change directory and the staged diff with the patterns from the repo's `.githooks/pre-push` guard, plus bare tracker numbers. Verify: no matches.

## Workflow follow-up

- Open the PR on origin, merge after CI, archive the change, then close its trackers.
- Then `/release-cut 0.15.0`.
