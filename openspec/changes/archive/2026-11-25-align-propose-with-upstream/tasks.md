# Tasks

## 1. Contract fixtures for the propose workflow

- [x] 1.1 Add `propose: 'openspec-propose'` to both workflow maps in `scripts/capture-workflow-invocations.py` (1.x and 2.x), and re-capture `workflow-invocations.json` for every captured release, 1.15.0 through 2.1.1, from the cached CLI packages (`OPENSPEC_BIN`), never npx.
  - Verify: each fixture's diff only adds propose rows.
  - Verify: on 2.0.0 and later, every propose row is skills-only, at `.agents/skills/openspec-propose/SKILL.md`, with upstream's invocation (`/openspec-propose`, Codex `$openspec-propose`, Kimi `/skill:openspec-propose`, words for the plain-words tools).
- [x] 1.2 Add `WorkflowInvocation.PROPOSE` and `PROPOSE_SKILL`, and extend `WorkflowInvocationContractTest` so every captured propose row resolves to its captured invocation and paths: its workflow list (`List.of("explore", "update")`) gains `propose`, and the handoff test gains a propose case.
  - Mutation: the wrong skill name turns it red.

## 2. Propose = upstream propose

- [x] 2.1 Make `ProposeChangeDialog` a description form.
  - Direct API: an optional name (derived with `deriveKebabName` when blank) and the schema combo when several schemas exist; OK disabled on an empty description.
  - Handoff modes: the description alone; empty allowed.
  - Both: the title names the mode, and a line under the description says what will happen (the provider, or the invocation and tool) and points to the tool window's delivery selector.
- [x] 2.2 Factor `WorkflowActionPanel.onFfGo()`'s create-and-generate body into a public `proposeAndGenerate(name, description, schema, delivery)` used by both Fast-Forward and Propose. Propose passes Direct API explicitly; `onFfGo` passes the panel's selector. Propose reports a creation error as a notification. Neither passes `--description` or `--goal` to `new change`.
- [x] 2.2a Make a Propose or Fast-Forward Direct API run stop once `ChangeArtifactDag.isApplyReady()` holds, in `ArtifactOrchestrationService`; the panel's Generate All button keeps generating everything. Test with a captured custom-schema status whose artifacts extend past `applyRequires`: the run stops at the apply-ready set; Generate All doesn't.
- [x] 2.3 Add `ScaffoldingService.createEmptyChange(name, schema)`, writing only `.openspec.yaml` as `openspec new change` does. `proposeAndGenerate` uses it when the CLI is unavailable.
- [x] 2.4 Rewrite `OpenSpecProposeAction` and fix its class Javadoc.
  - Resolve the delivery mode.
  - Direct API: show the dialog, then call the panel through `OpenSpecToolWindowPanel`.
  - Clipboard or editor tab:
    - find the propose invocation before the dialog;
    - if it's missing, warn and offer **Update OpenSpec…**;
    - otherwise deliver the handoff (the description, or no args) through `WorkflowHandoffDelivery`.
  - Direct API landing: show the tool window (waiting for its content when not created yet), select the change, and show the pipeline generating.
  - Reword the first-proposal "What's next" notice, and show it after generation completes. Reword the dialog's first-proposal banner too ("Propose a change, then generate artifacts (design, specs, tasks)…").
  - Replace `ProposeCreatesUpstreamSetTest`'s use of `ScaffoldingService.createChange`, then remove `createChange` if nothing else uses it. Update the `OpenSpecBaseAction` comment that lists Propose as a built-in write.
- [x] 2.5 Tests:
  - the handoff text for a description and for an empty one;
  - the not-installed notice, with no change created;
  - Direct API calls `proposeAndGenerate` with the dialog's values;
  - the handoff dialog has no name or schema field;
  - an empty description blocks Direct API submit only;
  - `createEmptyChange` writes `.openspec.yaml` alone;
  - the mode line's text in each mode;
  - `SchemaComboVisibilityTest` updated for both dialogs.

## 3. New Change = upstream new

- [x] 3.1 Add `OpenSpecNewChangeAction` (`OpenSpec.New`, "New Change...", gated on `new`, `DumbAware`).
  - Its `NewChangeDialog` asks for a name and, when several schemas exist, a schema.
  - It creates the change without any artifact: the CLI's `new change` when available, else `createEmptyChange`.
  - It then refreshes and selects the change.
  - Its `plugin.xml` description, after upstream `new`'s: "Start a new change, then create its artifacts one at a time".
- [x] 3.2 Register it in `plugin.xml` after Propose in the OpenSpec menu, not the toolbar. Mirror upstream's command descriptions (2.1.1 templates), which the parity audit compares:
  - Propose: "Propose a new change: create it and generate all artifacts in one step";
  - Fast-Forward: "Create a change and generate all artifacts needed for implementation in one step" (today's "all artifacts" stops being true once a run stops at the apply-ready set).
- [x] 3.3 Tests:
  - disabled with the "(custom)" suffix on core, enabled when `new` is in a custom profile;
  - creates no artifact file and generates nothing;
  - the schema's first artifact (the proposal, for spec-driven) is the selected change's first READY artifact;
  - `WorkflowParityContractTest`: delete the `new` not-surfaced entry and add `new` to the known gated workflows;
  - the menu order (Explore, Propose, New Change, Fast-Forward, Continue) in `ExploreEntryPointsTest`.

## 4. Empty state without Fast-Forward

- [x] 4.1 Remove the "or" label and the Fast-Forward link from `WorkflowActionPanel.buildNoChangesCard`.
- [x] 4.2 Make `OpenSpecContinueAction`'s empty-state hint profile-aware: it names New Change only when `new` is active, and Propose otherwise (never Fast-Forward on core).
- [x] 4.3 Rewrite `GettingStartedPanel`'s Propose copy ("Give it a name, describe why…") to "Describe what you want to build".
- [x] 4.4 Tests: the no-changes card has no Fast-Forward link, with or without Direct API, and its Propose link still launches `OpenSpec.Propose`; replace the Fast-Forward-link tests in `WorkflowActionPanelTest`; the Continue hint per profile.

## 5. Gating follows a CLI change

- [x] 5.1 Record in `WorkflowProfileService` the CLI version (or none) its active workflows were resolved for, and add `onCliDetected(available, version)`, which refreshes on a pooled thread only when that value differs.
- [x] 5.2 Register a `<projectListeners>` listener on `CliDetectionService.TOPIC` that calls `onCliDetected`.
- [x] 5.3 Tests:
  - on core, 1.5.0 → 1.6.0 adds `update` and enables an `update`-gated action;
  - the same version doesn't refresh;
  - CLI removed resolves to the current core set;
  - the CLI appearing with a stored custom profile switches to that list;
  - the status-bar widget is updated after the refresh.

## 5a. Fast-Forward = upstream ff (added after the parity audit)

- [x] 5a.1 Capture the ff workflow's invocations (`ff: 'openspec-ff-change'` in both maps) for every release, and add `WorkflowInvocation.FF` and `FF_SKILL`, checked against every captured ff row.
- [x] 5a.2 Make `OpenSpecFfAction` resolve the delivery mode as Propose does: Direct API opens the panel's form (a Direct API run); clipboard and editor tab hand off the tool's ff workflow with the description through Propose's handoff, creating nothing. Gate on Direct API's configuration only in Direct API mode.
- [x] 5a.3 Tests: enabled without Direct API in a handoff, disabled in Direct API mode without a provider; the handoff text, title and no name field; the not-installed notice with Update OpenSpec…; the panel-level Fast-Forward clipboard tests replaced, since the panel no longer runs a handoff.

## 6. Docs, parity record and checks

- [x] 6.0 At the start of implementation, run test-engineer in PLAN mode, and jetbrains-platform-guru for the `<projectListeners>` registration and the dialog changes.
- [x] 6.1 Update the docs:
  - `docs/feature-reference.md`: the Propose rows and their CLI note, a New Change row, the list of workflow-bound actions, the Fast-Forward section that says it needs Direct API (say what Propose needs in each mode), the empty state, and gating after a CLI change;
  - `README.md` (New Change, Propose), `docs/feature-comparison-matrix.md`, the getting-started guides (Copilot, CLI companion, API) and `docs/openspec-support.md`, wherever they describe Propose or the Fast-Forward link;
  - CHANGELOG `## Unreleased` entries: Propose generates everything (or hands off), the three-field form is gone, the Fast-Forward link moved into Propose, and Fast-Forward with Direct API stops at the apply-ready set;
  - run doc-fidelity on the marketplace page.
  - Outcome: the feature reference, the three getting-started guides, the support page, the comparison matrix, the marketplace getting-started page and the CHANGELOG are updated; `DocumentationHygieneTest` passes. The README names no individual action today, so New Change goes in with the tracked README refresh, which rewrites that page.
- [x] 6.2 Update `WorkflowParityContractTest`'s class comment: drop the v0.17.0 Propose exception and record that Propose now matches upstream `propose`.
- [x] 6.3 Add UI smoke journeys for the Propose dialog: with Direct API it shows the mode line, the name and the schema field; in clipboard mode it shows the description only and its mode line. Existing journeys and the screenshot tour don't drive the old dialog (checked); confirm the Setup Wizard's "Create your first change" path still opens Propose.
- [x] 6.4 Final checks, with the outcome recorded here:
  - `./gradlew build`;
  - `./gradlew verifyPlugin`;
  - `openspec validate --all --strict`;
  - the full `uiSmoke` under `caffeinate -dimsu`;
  - the workflow ↔ UI parity audit (openspec-guru plus plugin-ui-specialist) for Propose, New Change and Fast-Forward;
  - test-engineer AUDIT and intellij-code-reviewer on the diff, with findings fixed or recorded.
  - Outcome, on the final code:
    - `./gradlew build` passed, including the coverage floor;
    - `verifyPlugin` reported all 6 target IDEs compatible;
    - `uiSmoke` passed 18 of 18, with the two new Propose journeys (two earlier runs failed across unrelated journeys whose dialogs never opened, an environment fault; the run on the same code later passed in full);
    - `openspec validate --all --strict` passed.
  - The parity audit found Propose, New Change and Continue matching upstream in every delivery mode, and one gap: Fast-Forward with clipboard or editor tab did upstream `new` plus one `continue`. It was fixed in this change (group 5a).
  - Code review fixes: Propose from Getting Started reaches a workflow panel; a run names its own change; late callbacks stop on a released panel; a run finishes while the tool window is hidden; the CLI version is read once per resolve and `refresh` is synchronized; New Change refreshes the folder the CLI wrote; creating a change is one undoable command.
  - Test audit fixes: panel-level tests of the apply-ready stop, the "Ready to apply" status and what follows the run, against the captured past-apply status; the real create-and-generate path with and without the CLI, through a test CLI; New Change's CLI path (`--schema` passed, never `--description`); the "What's next" notice once; "new change leaves only `.openspec.yaml`" checked against a capture of the real CLI; a barrier instead of a fixed wait in the re-gating test.
  - Not covered by a test: the Getting Started fallback in `withPanel` (it needs a registered tool window in the Getting Started state), recorded here.
  - Mutations: 46 run across the groups and the fixes; every one turned a test red.
- [x] 6.5 Record a follow-up tracker entry for an `onboard` action (custom profiles).

## Workflow follow-up

- Merge by rebase, archive with spec sync, close the tracker entries.
- Delete the parked planning branch once the change is on `main`.
