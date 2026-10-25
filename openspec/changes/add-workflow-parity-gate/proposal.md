# Proposal

## Why

The OpenSpec menu drifted from upstream's workflows, and nothing caught it:
- **Propose** scaffolds a change the way upstream `new` does, instead of what upstream `propose` does;
- on the default core profile, no menu action does upstream `propose`'s job;
- `new` and `onboard` have no action;
- the core `update` workflow has none either, while the plugin's **Update OpenSpec…** means something else.

The plugin's tests, release gates and audits all missed it, because none of them compares the plugin's actions with upstream's workflow set. A user question found it. Tracked by the linked tracker entry.

Two kinds of check are needed:
- **A structural one, which a test can do:** every upstream workflow has an action gated on it, or a recorded reason why not; no action claims a workflow upstream doesn't have; and the plugin's core set matches upstream's.
- **A behavioural one, which only a review can do:** does the action do what the workflow does, under the right name, and can users find it?

## What Changes

- **A workflow parity contract test.**
  - **Fixture:** capture upstream's `CORE_WORKFLOWS` and `ALL_WORKFLOWS` from the installed CLI into `src/test/resources/fixtures/cli/<version>/workflows.json`, with a re-capture script. The CLI has no command that prints them, so the script loads the package's own exported constants, never a hand-written list.
  - **Test:** for every upstream workflow, exactly one of these holds:
    - a registered OpenSpec action is gated on it (its workflow id);
    - an entry in one commented "not surfaced" list gives the reason and the tracker reference.
  - **It also fails when:**
    - an action is gated on a workflow upstream doesn't have;
    - a "not surfaced" entry names a workflow that now has an action, so the list can't go stale;
    - the plugin's core set for the captured CLI version differs from upstream's.
  - **Today's not-surfaced entries:**
    - `new`: no scaffold-only action yet;
    - `update`: Revise Change… is planned;
    - `onboard`: undecided;
    - `sync`: the built-in Sync Specs, deliberately not gated on the workflow.
- **A standing parity audit.** On every CLI bump and before every release, a workflow ↔ UI parity review checks:
  - each workflow's behaviour against the plugin action that claims it, using upstream's workflow templates;
  - where each new or changed workflow appears in the UI.

  It's added to the project's agent-routing guidance, the CLI-bump playbook and the release-prep checklist. In release prep, a core workflow that no UI action reaches is a blocker. Today's known gap (Propose ≈ `new`) is the recorded exception, scheduled for the next release.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `ci`: adds **Upstream workflow parity**. The test suite checks the plugin's workflow-gated actions against upstream's captured workflow set.

## Impact

- **Tests:**
  - a new `WorkflowParityContractTest`;
  - a new fixture `fixtures/cli/1.15.0/workflows.json` and the capture script `scripts/capture-workflow-ids.py`;
  - a fixtures README entry.
- **Docs and process:**
  - `CLAUDE.md` agent routing;
  - the CLI-bump playbook;
  - the release-prep checklist, which is local to the maintainer's clone.
- **Code:** none. The test reads the existing workflow ids and the profile resolver.
- **Platform compatibility:** IntelliJ IDEA 2024.2 and later stays supported; no platform APIs change.
