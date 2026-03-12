## Why

First-time users who complete the setup wizard land on a bare "Propose a Change" card with no guidance on what a good change name looks like, what to write for "why" and "what changes", or what happens next. The ProposeChangeDialog has three blank fields with minimal labels. Users unfamiliar with spec-driven development stall here — they don't know what a change represents, how to scope it, or that artifacts will be generated from their input. This is the single biggest drop-off point in the onboarding funnel and the last gap before v0.1.0 "Ship It Clean."

## What Changes

- Add placeholder/hint text to all ProposeChangeDialog fields (name, why, what changes) showing examples of good input
- Add a brief contextual banner at the top of the ProposeChangeDialog explaining the OpenSpec workflow (propose → generate → implement → archive)
- Enhance the NO_CHANGES state in GettingStartedPanel to include a "What is a change?" explanation and example scoping tips
- After a successful first proposal, show a "What's Next" notification with a summary of next steps (generate artifacts, implement, archive)
- Add a first-run flag to prevent the extra guidance from cluttering the UI for experienced users

## Capabilities

### New Capabilities
- `guided-first-proposal`: Contextual guidance for first-time proposal creation, including placeholder text, workflow explanation, and post-proposal next-steps notification

### Modified Capabilities
- `getting-started-guide`: Add requirement for the NO_CHANGES state to include educational content about what a change is

## Impact

- `ProposeChangeDialog.java` — placeholder text, optional banner component
- `GettingStartedPanel.java` — enhanced NO_CHANGES card content
- `OpenSpecProposeAction.java` — first-run detection, post-proposal notification
- `OpenSpecSettings.java` — new `firstProposalCompleted` boolean setting
- `OpenSpecNotifier.java` — new "What's Next" notification (uses existing GROUP_WORKFLOW)
