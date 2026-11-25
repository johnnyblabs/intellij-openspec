# Proposal

## Why

In OpenSpec, `propose` is a core workflow: it creates a change and generates every artifact needed to implement it, in one step. The plugin's **Propose...** does what upstream `new` does instead. It creates the change folder and a `proposal.md` from a dialog, and the user then generates the rest one artifact at a time. On the default core profile, the only one-step path is an ungated "Fast-Forward" link in the workflow panel, which shows only when Direct API is set up. Clipboard and editor-tab users have no one-step path at all, and core users see a Fast-Forward entry for a workflow their profile doesn't have.

The workflow ↔ UI parity audit for this release found this as the one core workflow that no UI action reaches with upstream's behaviour, which blocks the release. The linked tracker issue records the full review.

## What Changes

- **Propose... does what upstream `propose` does.** It asks for a description of what the user wants to build. Then:
  - with **Direct API**, it also offers an optional change name and, when several schemas exist, a schema. It creates the change and generates the artifacts the change needs before it can be applied, using the description as the request, and stops there as upstream does (today's Fast-Forward generation path, which gets the same stop);
  - with **clipboard or editor tab**, it hands the user's AI tool its own propose workflow with the description, as Explore, Revise Change and Apply do. On CLI 2.x that is the tool's propose skill; on 1.x it may be a propose command, whose form varies by tool. The agent then picks the name, asks its own questions and writes the artifacts. Upstream propose takes a name or a description, so the dialog asks only for the description here. If the project has no generated propose workflow, a notice offers **Update OpenSpec…**.
- **New Change... is a new action for upstream `new`.** It is gated on the `new` workflow, so on core it is shown disabled with the "(custom)" suffix, like Fast-Forward and Continue. Like upstream `new`, it only scaffolds: it asks for a name and, when several schemas exist, a schema, creates the change without any artifact, and the user steps through the artifacts with Continue or the panel, starting with the proposal.
- **The dialog says what it will do.** Its title and a line under the description name the mode: generating with the Direct API provider, or copying the tool's propose invocation for the agent.
- **No Fast-Forward link on the workflow panel's empty state.** The card offers Propose only. **Fast-Forward...** stays in the menu and toolbar for profiles that include `ff`. A Fast-Forward run with Direct API also stops once the artifacts needed before apply are done.
- **Menu gating follows a CLI change.** When the plugin detects a different CLI version during a session, it re-resolves the active workflows, so a CLI upgrade that changes the core set (for example 1.5 → 1.6 adds `update`) takes effect without reopening the project.
- **Descriptions match.** Propose's menu description, Continue's empty-state hint, the Propose action's code comment, the feature reference and the CHANGELOG describe the new behaviour. Today's three-field Propose form (name, why, what changes) goes away: the description replaces why and what changes. Update OpenSpec… and Revise Change... keep distinct names and descriptions.
- Out of scope: an `onboard` action (custom profiles only), recorded as a follow-up.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `workflow`: the Propose action does upstream `propose` in every delivery mode; a new New Change action does upstream `new` (scaffold only); the panel's empty state no longer offers Fast-Forward.
- `profile-visibility`: active workflows are re-resolved when the detected CLI version changes during a session.
- `ff-panel`: Fast-Forward is opened from its menu or toolbar action only, and its Direct API run stops at the artifacts needed before apply.
- `ai-integration`: Direct API gates the Fast-Forward action; there is no panel link to gate.
- `edt-compliance`: how Propose and New Change create a change off the EDT.
- `explore-context`: the menu order includes New Change.
- `documentation`: the README names New Change.

## Impact

- Code: `OpenSpecProposeAction`, a new `OpenSpecNewChangeAction`, `ProposeChangeDialog` (a description form; name and schema only with Direct API), a small `NewChangeDialog` (name and schema), `ScaffoldingService` (a scaffold-only create), `WorkflowActionPanel` (empty-state card; the Fast-Forward create-and-generate path is reused by Propose), `WorkflowInvocation` (propose skill name), `WorkflowProfileService` (listens for CLI detection), `OpenSpecContinueAction` (hint text), `ArtifactOrchestrationService` (a run that stops at the apply-ready set), `GettingStartedPanel` (copy), `plugin.xml`.
- Tests: the workflow-invocation contract fixtures are re-captured from every captured CLI release, 1.15.0 to 2.1.1, with `propose` added; new tests for the Propose handoff, the Direct API path, New Change, the empty-state card and the profile refresh. `WorkflowParityContractTest` records `new` as surfaced and drops the Propose exception.
- Docs: `README.md`, `docs/feature-reference.md`, `docs/feature-comparison-matrix.md`, the getting-started guides and `docs/openspec-support.md` where they describe Propose or the Fast-Forward link, and `CHANGELOG.md`. A new UI smoke journey covers the Propose dialog; no existing journey or screenshot drives the old one.
- Platform: no new platform APIs. It uses the project message bus and existing actions, so IntelliJ IDEA 2024.2+ support is unchanged.
