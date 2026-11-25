# Design

## Context

Upstream's core profile is `propose, explore, apply, update, sync, archive`. It is the same from CLI 1.6 through 2.1.1 (`core/profiles.js`), and the full set still has `new`, `continue`, `ff`, `onboard`, `bulk-archive` and `verify`. `propose` creates a change and generates the apply-required artifacts in one step. Its skill runs `openspec context --json` (1.15 and later), asks about scope-changing ambiguity before it writes anything, takes a kebab-case name **or** a description as input, and asks whether to continue an existing change of that name. The 2.x template differs from 1.17's only in its apply-handoff wording. `new` only scaffolds ("do not create any artifacts yet"); `continue` steps through the artifacts; `ff` is the predecessor of `propose` and is only in custom profiles.

On CLI 2.x every tool gets skills only, written to the shared `.agents/skills/` (some tools get a link from their own folder), and no command files. A tool invokes propose as `/openspec-propose`; Codex as `$openspec-propose`, Kimi as `/skill:openspec-propose`, and three tools by naming the skill in words. On 1.x a tool may instead have a propose command, whose form varies by tool (`/opsx:propose`, `/opsx-propose`, Amazon Q's `@opsx-propose`).

What the plugin has today:

- `OpenSpecProposeAction` (gated on `propose`) opens `ProposeChangeDialog` (name, why, what changes, optional schema) and calls `scaffolding.ScaffoldingService.createChange`, which writes `.openspec.yaml`, `proposal.md` and an empty `specs/`. That is upstream `new` plus a proposal.
- `WorkflowActionPanel` already has the create-and-generate path: `onFfGo()` runs `openspec new change <name> [--schema s]` in a background task, then `startFastForwardGeneration(name, description)` runs Generate All over Direct API with the description as the request (ff-panel spec, "FF generation uses the user's description as the request"). With clipboard or editor tab it generates only the first ready artifact.
- The panel's empty-state card (`buildNoChangesCard`) shows "Propose a change" and, when Direct API is configured, an ungated "or Fast-Forward" link.
- Explore, Revise Change and Apply already hand a workflow to the user's AI tool through `WorkflowInvocation.find(...)`, `WorkflowInvocation.handoff(invocation, skill, args)` and `WorkflowHandoffDelivery.deliver(...)`, choosing the mode with `DeliveryMethodResolver`. `find` already resolves 2.x skills wherever they are (the shared folder, a tool folder's link or copy, the home directory) and produces upstream's 2.x invocation strings. `WorkflowInvocationContractTest` checks the invocation rules against `fixtures/cli/<v>/workflow-invocations.json` for 1.15.0 through 2.1.1, captured by `scripts/capture-workflow-invocations.py` for `explore` and `update`.
- `WorkflowProfileService.refresh()` resolves the active workflows from `config list --json` and `CliDetectionService.getDetectedVersion()`. It runs on project open, on a profile change, and when the status-bar widget or tool window is created. `CliDetectionService.TOPIC` (`detectionCompleted(available, version)`) is published after every detection, from the detecting thread.

## Goals / Non-Goals

**Goals**
- Propose produces upstream propose's result in every delivery mode.
- Upstream `new` has its own action, gated on `new`.
- No ff-named entry appears on core.
- Gating follows a CLI version change during a session.

**Non-Goals**
- No `onboard` action (custom profiles only; recorded as a follow-up).
- No change to the menu's Fast-Forward... or the inline FF input card's behaviour, beyond its description.
- No re-implementation of propose's ambiguity questions in the IDE. In handoff modes the agent asks them; with Direct API the plugin generates from the description, as Fast-Forward does today.
- No `openspec/config.yaml` side effect without the CLI. `openspec new change` writes one when the project has none; the plugin's no-CLI scaffold writes only the change's `.openspec.yaml`. Neither Propose nor New Change passes `--description` or `--goal` to `new change`: `--description` writes a `README.md`, which is not a schema artifact.

## Decisions

### 1. One Propose dialog, a description first

`ProposeChangeDialog` becomes a description form: a multi-line "Describe what you want to build" field.

The dialog says which mode it is in, so the form never changes shape silently. Its title names the mode, and a line under the description says what will happen: "Creates the change and generates its planning artifacts with <provider>", or "Copies <invocation> for <tool>; your agent creates the change". The line names the tool window's delivery selector as the place to change the mode; the dialog has no selector of its own.

- **With Direct API** it also shows an optional change name (derived from the description when blank, with the panel's existing `deriveKebabName`) and the schema combo when several schemas exist, because the plugin creates the change itself. OK stays disabled until the description has text.
- **In handoff modes** it shows the description alone, and an empty description is allowed. Upstream propose takes a name or a description, and picks the name itself from a description; it uses the project's default schema unless the user asks for another. Offering a name or schema field there would ask for input the handoff can't carry faithfully.

The three-field form (name, why, what changes) is retired. The description replaces why and what changes, and New Change asks only for a name and schema (Decision 4).

*Alternative considered:* skip the dialog in handoff modes and copy `/opsx:propose` alone. Rejected, because the description is the request upstream propose takes, and asking for it in the IDE costs one field.

### 2. Direct API: reuse the panel's create-and-generate path

`WorkflowActionPanel` gets a public entry point, `proposeAndGenerate(String name, String description, @Nullable String schema, DeliveryMethod delivery)`, factored out of `onFfGo()`. It creates the change in a background task and on success starts generation in the given delivery mode. Propose passes Direct API explicitly, as the action resolved it with `DeliveryMethodResolver`, so the panel's own selector can't turn the run into a single-artifact one. `onFfGo()` passes the panel's selector, as today. Errors are reported through a notification for Propose; the FF card keeps its inline status label.

**Where generation stops.** Upstream propose and ff generate the artifacts in `applyRequires` and everything they require, and leave the rest. The plugin's Generate All loops until the whole change is complete, which is the same for spec-driven but generates extra artifacts for a custom schema with artifacts outside that set. A Propose or Fast-Forward run therefore stops once `ChangeArtifactDag.isApplyReady()` holds (every `applyRequires` artifact done, which the DAG reaches only after their requirements). The panel's own Generate All button keeps generating everything.

Creation uses the CLI's `new change` when the CLI is available. Without the CLI it uses a new `ScaffoldingService.createEmptyChange(name, schema)`, which writes only `.openspec.yaml`, as `openspec new change` does, and no `proposal.md`, so Generate All generates the proposal from the description too.

`OpenSpecProposeAction` shows the OpenSpec tool window and calls the panel through `OpenSpecToolWindowPanel`, as `OpenSpecApplyAction` does with `selectChangeAndApply`, waiting for the tool window's content when it hasn't been created yet.

**Where the user lands.** With Direct API: the tool window, the new change selected, its pipeline showing GENERATING chips and the progress bar. The first-proposal "What's next" notice stays, reworded (it says today that the user generates design, specs and tasks next), and shows after generation completes. The dialog's first-proposal banner, which says the same, is reworded too. In handoff modes, `WorkflowHandoffDelivery` shows its copied-or-opened notice; the change appears in the tool window when the agent writes it, through the existing file watching.

*Alternative considered:* a separate generation path in the action. Rejected: it would fork the ff-panel rules (the description as request, the progress bar, chip states).

### 3. Clipboard and editor tab: hand off the tool's propose workflow

Add `WorkflowInvocation.PROPOSE = "propose"` and `PROPOSE_SKILL = "openspec-propose"`. Propose finds the invocation with `WorkflowInvocation.find(base, candidateTools(project), PROPOSE, PROPOSE_SKILL)` before the dialog opens, so nothing is asked in vain. It delivers `WorkflowInvocation.handoff(invocation, PROPOSE_SKILL, args)` with `WorkflowHandoffDelivery.deliver`.

- `args` is the description, or nothing when it is empty, so the agent asks what to build.
- If no candidate tool has the workflow, Propose warns and offers **Update OpenSpec…** (`ActionLauncher.notificationAction`), as Revise Change does.
- The plugin creates nothing in this mode; the agent does.
- A 1.x `/opsx:propose` command file that the user edited is kept by CLI 2.x, and `find` prefers a command over a skill, so such a project gets the command. That matches how Explore and Revise Change already behave, and is left as is.

`scripts/capture-workflow-invocations.py` gains `propose: 'openspec-propose'` in both its 1.x and 2.x workflow maps (upstream's `COMMAND_TO_SKILL_NAME` already maps propose), and every captured release is re-captured, 1.15.0 through 2.1.1, from the cached CLI packages. The contract test then covers propose's skill paths for every tool on 2.x, and its command paths on 1.x.

### 4. New Change

`OpenSpecNewChangeAction` (`OpenSpec.New`, "New Change...", gated on `new`, `DumbAware`) shows a small `NewChangeDialog` (a name, and the schema combo when several schemas exist). It creates the change without any artifact, as upstream `new` does: through the CLI's `new change` when the CLI is available, otherwise with `ScaffoldingService.createEmptyChange` (Decision 2). It then refreshes the tool window and selects the change, whose first READY chip is the schema's first artifact (the proposal, for spec-driven). Its menu description follows upstream `new`'s: "Start a new change, then create its artifacts one at a time". Propose's and Fast-Forward's follow upstream's too: "Propose a new change: create it and generate all artifacts in one step", and "Create a change and generate all artifacts needed for implementation in one step", which stays true once a run stops at the apply-ready set. Continue's empty-state hint names New Change only when `new` is in the active profile, and Propose otherwise. Nothing on the core path points to New Change, since it is disabled there.

*Alternative considered:* keep today's three-field form and write `proposal.md` from it. Rejected: upstream `new` creates no artifact, and an action named after one workflow must not do another's job. Menu order: Explore, Propose, New Change, Fast-Forward, Continue. It is menu-only; the toolbar keeps Explore, Propose and Fast-Forward. `OpenSpecBaseAction` already disables a gated action with the "(custom)" suffix on core.

### 5. Empty state

`buildNoChangesCard` drops the "or" label and the Fast-Forward link. "Propose a change" keeps launching `OpenSpec.Propose` through `ActionLauncher`. Fast-Forward... in the menu and toolbar is gated on `ff`, unchanged, with its description unchanged: upstream tags propose and new as experimental too, so the tag wouldn't set it apart.

### 5a. Fast-Forward hands off outside Direct API

Added after the workflow ↔ UI parity audit found that Fast-Forward, with clipboard or editor tab, created the change and handed off only the first artifact (upstream `new` plus one `continue`), and was enabled only when Direct API was configured.

- `OpenSpecFfAction` resolves the delivery mode with `DeliveryMethodResolver`, as Propose does. With Direct API it opens the panel's form, as before; the form's run is a Direct API run.
- With clipboard or editor tab it uses Propose's handoff (`OpenSpecProposeAction.handOff` with a `Handoff` for `ff` / `openspec-ff-change`): the description alone, no change created, the not-installed notice offering Update OpenSpec….
- Its Direct API gate applies only when Direct API is the delivery mode.
- `scripts/capture-workflow-invocations.py` gains `ff`, and the fixtures are re-captured, so the contract test covers ff's invocation for every tool and release.

### 6. Re-gate on a CLI change

Re-resolving matters wherever the version changes the result: the core set's 1.4 and 1.6 boundaries (it is fixed from 1.6 through 2.1.1), the custom-profile `sync` rule that changes at 1.10, and the CLI appearing or disappearing, which switches between the fallback core set and the stored custom list. `WorkflowProfileService` records the CLI version `resolve()` actually used. A project-level listener on `CliDetectionService.TOPIC`, registered in `plugin.xml` under `<projectListeners>`, calls `onCliDetected(available, version)`. When `available ? version : null` differs from the recorded value, it calls `refresh()` on a pooled thread, because `refresh()` may run `openspec config list --json`. The same value is a no-op. Action `update()` reads the cached set, so the next menu or toolbar update shows the new gating. The status-bar widget, which shows the profile, is updated after the refresh. Reading the detected version doesn't start detection, so the listener can't loop.

*Alternative considered:* subscribing in the service constructor. Rejected: a declarative listener needs no disposable and loads only when the topic fires.

## Risks / Trade-offs

- **Direct API Propose doesn't ask clarifying questions** as upstream propose's skill does. This is the same as Fast-Forward today, and the user can review and regenerate each artifact. Handoff modes keep upstream's questions.
- **Some tools have no generated propose workflow** (for example, a custom profile without `propose`). Propose is gated on `propose`, so it's disabled there; when the files are missing anyway, the notice points to Update OpenSpec….
- **UI smoke and the screenshot tour** may drive the old three-field Propose dialog or show the Fast-Forward link. Those journeys must be updated in this change, and the run stays local (`caffeinate -dimsu ./gradlew uiSmoke`).

## Migration Plan

No settings or on-disk data change. Users who relied on Propose's three fields describe the change instead; on profiles with `new`, New Change... scaffolds a change whose proposal they then write or generate. The CHANGELOG says so.
