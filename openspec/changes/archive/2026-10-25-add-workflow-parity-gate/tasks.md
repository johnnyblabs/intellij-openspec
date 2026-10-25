# Tasks

## 1. Fixture

- [x] 1.1 Add `scripts/capture-workflow-ids.py` (design D1). It loads the installed CLI package's `dist/core/profiles.js` with `node` and writes `{cliVersion, core, all}` to `src/test/resources/fixtures/cli/<version>/workflows.json`. It fails loudly when the constants are missing. Capture 1.15.0 and add a fixtures README entry with the recipe. Verify: the fixture holds 6 core and 12 workflows in upstream's order, written by the script, not by hand.

## 2. Tests

- [x] 2.1 Add `WorkflowParityContractTest` (design D2–D3). It covers registered `OpenSpec.*` actions via `ActionManager`, their `getWorkflowId()`, and the `NOT_SURFACED` map with reasons and tracker references for `new`, `update`, `onboard` and `sync`. Verify it fails, with a message naming the id, for each case:
  - an upstream workflow is neither gated nor listed;
  - a gated id isn't upstream's;
  - a listed id is now gated, or isn't upstream's.

  It also asserts at least the eight known gated actions are found.
- [x] 2.2 Add `CoreWorkflowsContractTest` (design D4): `WorkflowProfileService.coreWorkflows(fixture.cliVersion)` equals the fixture's `core`, in order. Verify: it passes on 1.15.0.
- [x] 2.3 Mutation-check both tests. Each of these must fail at least one test, then be reverted:
  - remove Explore's `getWorkflowId()`;
  - misspell Verify's id;
  - drop `sync` from `NOT_SURFACED`;
  - add a gated workflow that's also listed;
  - drop `update` from the plugin's current core set.

## 3. Audit step and docs

- [x] 3.1 Add the workflow ↔ UI parity audit trigger to `CLAUDE.md` "Agent routing" (design D5), in vendor-neutral wording. Verify: the leak grep on the staged file is clean.
- [x] 3.2 Add the audit step to the CLI-bump playbook and to the release-prep checklist. Both are local to the maintainer's clone, so neither is committed. The release-prep step makes an unreached core workflow a blocker, with the Propose ≈ `new` exception recorded. Verify: both files carry the step.
- [x] 3.3 Run `./gradlew build`. Verify: it passes, including the coverage floor. No `com.intellij.*` change is made in `src/main`, so `verifyPlugin` isn't needed.

## Workflow follow-up

- First audit run: reconcile its findings with the tracked Explore-placement, menu-alignment and Revise-change work.
