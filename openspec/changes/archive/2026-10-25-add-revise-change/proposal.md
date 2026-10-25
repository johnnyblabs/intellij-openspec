# Proposal

## Why

`update` is a **core** OpenSpec workflow: it's installed by default. Its `openspec-update-change` skill and `/opsx:update` command revise a change's planning artifacts. The agent takes a specific request, or runs a coherence review when there isn't one; checks every other artifact still agrees; and writes each revision only after the user confirms it. That's exactly the "send feedback back to the agent and review each step" loop users ask for, yet the plugin never surfaces it. The workflow parity gate records `update` as the one core workflow without an action. Tracked by the linked tracker entry.

## What Changes

- **A Revise Change... action**, gated on the `update` workflow and offered only for active changes:
  - **The change's right-click menu:** after Explore This Change..., before Apply.
  - **The workflow panel's change header:** an icon button next to Apply.
  - **The OpenSpec menu:** after Continue, using the selected change, or a chooser when several are active.
- **A small dialog** asks "What should change?". Leaving it blank is valid, and the dialog says the agent will then review the change's artifacts for coherence. A note is collapsed to one line.
- **A hand-off only.** Clipboard and editor-tab delivery copy what the user types to run their tool's own update workflow, the change name and the note. For example: `/opsx:update add-auth drop the export feature`, `/opsx-update …` in Cursor, `$openspec-update-change …` in Codex.
  - **The invocation:** it follows upstream's rules, the same ones Explore's handoff uses, generalized from `explore` to any workflow and contract-tested against a matrix captured from upstream for `update` as well.
  - **Never inlined:** the skill is never copied into the prompt.
- **When the tool has neither the update skill nor the update command,** nothing is delivered. The plugin says to run **Update OpenSpec…**, or to add `update` to the profile, then retry.
- **Direct API redirects** to clipboard or editor tab with a one-line note. A single completion can't run the skill's status, instructions and confirm-each-write loop, so it isn't offered there.
- **Names that can't be confused.** It's called **Revise Change...**, never "Update". **Update OpenSpec…**'s description now says it refreshes generated skills and commands from the installed CLI.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `workflow`: adds **Revise change action**.

## Impact

- **Code:**
  - `ExploreInvocation` generalizes to `WorkflowInvocation` (any workflow id and its skill name), and Explore keeps using it;
  - a new `ReviseChangeAction` and `ReviseChangeDialog`;
  - `plugin.xml` registration and descriptions;
  - the change right-click menu and the workflow panel header button.
- **Tests:**
  - the capture script records `update` alongside `explore`, and the contract test checks both workflows against every tool and delivery;
  - action gating and placement;
  - the handoff with and without a note;
  - the missing-skill and Direct API paths;
  - `WorkflowParityContractTest`'s not-surfaced list loses `update`, and the test enforces that.
- **Docs:** the feature reference (the propose → generate → revise → apply loop), the CHANGELOG, and the walkthrough's Revise stop.
- **Platform compatibility:** IntelliJ IDEA 2024.2 and later stays supported; no new platform APIs.
- **Out of scope:** changes in a store, and offering Revise from Regenerate's dialog. Both are tracked separately.
