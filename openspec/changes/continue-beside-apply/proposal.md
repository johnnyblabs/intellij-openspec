# Proposal

## Why

OpenSpec's `continue` workflow creates a change's next ready artifact. On a profile that includes it, the plugin offers Continue only in the OpenSpec menu: not on the change's right-click menu beside Revise, Apply and Archive, and not in the workflow panel. A user building artifacts one at a time has to know the menu path. A custom-profile user also gets no pointer to the workflows beyond the core flow. Tracked in the linked tracker entry.

## What Changes

- **Continue is on the active change's right-click menu**, after Revise Change, gated by the profile like Apply and Archive.
- **The workflow panel has a Continue button**, after Revise and before Apply. It is shown only when the profile includes `continue`, and it is enabled while the change has a ready artifact.
- **Continue acts on the change it was launched for.** From the right-click menu or the panel button it uses that change, and asks only when launched without one.
- **The workflow panel's "No changes yet" card points custom-profile users at the OpenSpec menu** when their profile has workflows beyond the core ones.
- **The icon bar requirement matches the panel**: Revise, Continue, Apply, Verify, Sync Specs and Archive, with no Compliance button.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `workflow`: where Continue is offered, and the custom-profile hint on the "No changes yet" card.
- `pipeline-interaction`: the icon bar requirement is replaced by one listing its real buttons, including Continue.

## Impact

- **Code:** `OpenSpecToolWindowPanel.contextMenuFor`, `WorkflowActionPanel` (Continue button, no-changes hint), `OpenSpecContinueAction` (the change from the data context), `WorkflowProfileService` (the core set, read for the hint).
- **Tests:** the context menu's order, the button per profile and artifact state, the hint per profile, Continue's change resolution.
- **Docs:** feature reference, CHANGELOG.
- **Platform:** no new platform APIs; IntelliJ 2024.2+ support is unaffected.
