# Design

## Context

- **Workflow gating:** `OpenSpecBaseAction.getWorkflowId()` (protected, `null` for ungated actions) names the workflow an action is gated on. Eight registered actions return one today:

| Action | Workflow |
|---|---|
| Propose | `propose` |
| Explore | `explore` |
| Continue | `continue` |
| Apply | `apply` |
| Fast-Forward | `ff` |
| Archive | `archive` |
| Bulk Archive | `bulk-archive` |
| Verify | `verify` |

- **Core set:** `WorkflowProfileService.coreWorkflows(cliVersion)` (package-private, static) is the plugin's version-aware core set: four workflows before 1.4.0, five before 1.6.0, then six.
- **Upstream's lists:** `CORE_WORKFLOWS` and `ALL_WORKFLOWS` are exported from the CLI package's `dist/core/profiles.js`. No CLI command prints them: `openspec config set workflows` accepts any value without validating, and `config profile` is interactive.

## Goals / Non-Goals

**Goals:**

- A failing test whenever the plugin's workflow-gated actions and upstream's workflow set disagree structurally, or the plugin's core set diverges.
- A recorded, reviewable reason for every upstream workflow the plugin doesn't surface.
- A standing human audit for what a test can't judge: whether the action behaves like the workflow, and whether users can find it.

**Non-Goals:**

- Fixing the gaps the test records; each has its own tracker entry. This change makes them visible and stops new ones landing silently.
- Asserting behaviour; that's the audit's job. For example, Propose maps to `propose` structurally even though it behaves like `new`.

## Decisions

### D1. Capture by loading upstream's own constants
- **The script:** `scripts/capture-workflow-ids.py` resolves the installed CLI package (from `openspec` on PATH, or `$OPENSPEC_BIN`'s package dir). It loads `dist/core/profiles.js` with `node`, and writes `{"cliVersion", "core", "all"}` in upstream's order to `fixtures/cli/<version>/workflows.json`.
- **Why this counts as captured output:** the data is upstream's exported values, read the way upstream reads them, so it isn't a hand-written shape.
- **If upstream moves the constants**, the script fails loudly rather than writing a guess.

### D2. Enumerate registered actions through `ActionManager`
- **Light test:** `WorkflowParityContractTest` is a `BasePlatformTestCase` in the `actions` package, so it can call the protected `getWorkflowId()`.
- **Which actions:** every id from `ActionManager` with the `OpenSpec.` prefix whose action is an `OpenSpecBaseAction`. That's exactly what `plugin.xml` registers, so an action added without a matching workflow, or with a misspelled one, is seen.
- **Sanity check:** at least the eight known gated actions are found, so the test can't pass vacuously on an empty list.

### D3. One `NOT_SURFACED` map, checked both ways
- **What it is:** a `Map<String, String>` in the test, from workflow id to reason plus tracker reference:
  - `new`: no scaffold-only action yet; Propose currently behaves like it (tracked as the menu-alignment work);
  - `update`: Revise Change… (tracked as the Revise change action);
  - `onboard`: undecided (tracked with the menu-alignment work);
  - `sync`: the built-in Sync Specs is a utility, deliberately not gated on the workflow.
- **The test fails if:**
  - an upstream workflow is neither gated nor listed;
  - a gated id isn't upstream's;
  - a listed id is gated, or isn't upstream's.
- **Failure messages** name the ids and say what to do: add an action, add a `NOT_SURFACED` entry, or remove a stale entry.

### D4. Core-set parity as its own plain test
- **Test:** `CoreWorkflowsContractTest`, in the `services` package, asserts `coreWorkflows(fixture.cliVersion)` equals the fixture's `core` as an **ordered** list.
- **Why ordered:** the plugin's sets are `LinkedHashSet`s, and the order is what the profile UI shows.
- **Why it matters:** the core set is what the default profile enables. An earlier bug was exactly this set diverging.

### D5. The audit step
- **Where it goes:**
  - **CLAUDE.md "Agent routing":** a trigger, on every CLI bump and before every release, to run a workflow ↔ UI parity audit. openspec-guru checks behaviour against `dist/core/templates/workflows/*.js`; plugin-ui-specialist checks discoverability.
  - **The CLI-bump playbook** (maintainer notes) gets the same step.
  - **The release-prep checklist** (local to the maintainer's clone) gets a step. A core workflow with no UI action reaching it is a blocker unless the user records an exception. Today's recorded exception is Propose ≈ `new`, scheduled for the next release.
- **The test doesn't encode behavioural gaps.** It would need a judgment it can't make. The audit and the tracker carry them.

## Risks / Trade-offs

- **Upstream renames or moves `profiles.js`.** The capture script fails, and the fixture keeps the last good capture. Re-capturing on the next CLI bump surfaces it.
- **An action whose workflow depends on context.** Today every `getWorkflowId()` is a constant. If one ever becomes dynamic, the test reads only its default. Accepted; it's documented in the test.
- **The light test boots the platform** (a few seconds). Cheap next to the bugs it catches.
