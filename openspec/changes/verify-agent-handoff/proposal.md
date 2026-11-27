# Proposal

## Why

OpenSpec's `verify` workflow is an agent workflow: it checks a change's completeness, correctness and coherence. Outside Direct API, the plugin's Verify checks completeness only and reports correctness and coherence as "not assessed". It never hands the agent its verify skill, as Apply and Continue hand off theirs. So in Clipboard and Editor Tab modes, the full verify isn't reachable from the IDE. Tracked in the linked tracker entry.

## What Changes

- **The Verify report offers "Verify with <tool>…" outside Direct API.** It hands the tool's verify invocation and the change name (for example `/openspec-verify-change my-change`) to the AI tool, by clipboard or editor tab, as Continue does.
- **No verify skill installed:** the button reads "Verify with your AI tool…", and clicking it says the skill is missing and offers Update.
- **The built-in report stays** in every mode: completeness, validation and spec sync, the in-IDE correctness check with Direct API, and the Archive… hand-off.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `verify-workflow`: outside Direct API, the Verify report hands off the agent's verify skill.

## Impact

- **Code:** `WorkflowInvocation` (verify constants), a `VerifyHandoff` helper, `VerifyDialog` (an optional hand-off button), `OpenSpecVerifyAction`, `WorkflowActionPanel.onVerify`.
- **Tests:** the button per delivery mode, the hand-off text per tool, delivery per mode, the not-installed notice.
- **Docs:** feature reference (Verify), CHANGELOG.
- **Platform:** no new platform APIs; IntelliJ 2024.2+ support is unaffected.
