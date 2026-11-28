# Proposal

## Why

OpenSpec CLI 2.2's headline is the `/openspec` skill router. You type a request in plain words, and the router picks the right OpenSpec skill and runs it. It answers questions such as "where does add-auth stand?" and runs several steps in order, such as "verify add-auth, then archive it". The plugin recognizes and installs the router skill, but the IDE offers no way to reach it: a user who wants to ask has to leave for the agent's own terminal. Tracked in the linked tracker entry.

## What Changes

- **Ask the Agent (OpenSpec router)…** sits in the OpenSpec menu after Explore, and in the tool window's toolbar beside Explore.
  - It opens a one-line request field, with upstream's own examples as its placeholder.
  - It hands the text, unchanged and unquoted, to the router skill through the existing workflow handoff, spelled for the user's AI tool: `/openspec`, `$openspec` for Codex, `/skill:openspec` for Kimi, or "the openspec skill" for tools with no slash commands. Every spelling comes from upstream, captured per tool.
- **Only where the router exists.**
  - The action is hidden on a CLI older than 2.2.0, which has no router.
  - On 2.2.0+ it's disabled when no AI tool has the `openspec` skill installed, with a tooltip saying how to install it: run `openspec update`, or set up a tool.
- **The plugin doesn't interpret the request.** It picks no skill, runs no command for it and stores nothing: the router decides. Direct API can't run an agent skill, so in that mode the handoff is copied with a note, as other handoffs do.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `workflow`: a free-text handoff to the OpenSpec router skill.

## Impact

- **Code:**
  - a new `OpenSpecAskAgentAction` and `AskAgentDialog`;
  - `plugin.xml`: the OpenSpec menu and the tool-window toolbar;
  - the existing `WorkflowInvocation` and `WorkflowHandoffDelivery`.
- **Fixtures:** `scripts/capture-workflow-invocations.py` gains the router, captured on 2.2.0 as 87 tools, from upstream's own reference transformer.
- **Tests:**
  - a contract test of every tool's router spelling against the capture;
  - availability states: below 2.2, no router skill, available;
  - the handoff text passed through unchanged;
  - uiSmoke for the menu position and the dialog.
- **Docs:** feature reference, Marketplace page, CHANGELOG.
- **Platform:** existing action and `DialogWrapper` APIs; IntelliJ 2024.2+ support is unaffected.
