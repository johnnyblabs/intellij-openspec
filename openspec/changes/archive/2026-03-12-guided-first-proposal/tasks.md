## 1. Settings: First-Run Flag

- [x] 1.1 Add `firstProposalCompleted` boolean field to `OpenSpecSettings` with default `false`, getter, and setter

## 2. ProposeChangeDialog: Placeholder Text

- [x] 2.1 Add placeholder/empty text to `nameField` (e.g., "add-user-auth, fix-login-redirect")
- [x] 2.2 Add placeholder/empty text to `whyField` (e.g., "Users can't reset passwords without contacting support")
- [x] 2.3 Add placeholder/empty text to `whatChangesField` (e.g., "Add password reset endpoint and email notification")

## 3. ProposeChangeDialog: First-Run Workflow Banner

- [x] 3.1 Add an HTML `JBLabel` banner above the form fields explaining the propose → generate → implement → archive lifecycle
- [x] 3.2 Conditionally show the banner only when `firstProposalCompleted` is false

## 4. GettingStartedPanel: Enhanced NO_CHANGES State

- [x] 4.1 Update the NO_CHANGES card description to explain what a change is and include a scoping tip

## 5. Post-Proposal Next-Steps Notification

- [x] 5.1 In `OpenSpecProposeAction`, after successful proposal, check `firstProposalCompleted` flag
- [x] 5.2 If first proposal, fire "What's Next" notification via `OpenSpecNotifier` (GROUP_WORKFLOW) explaining next steps
- [x] 5.3 Set `firstProposalCompleted` to true after firing the notification

## 6. Verification

- [x] 6.1 Build compiles with no errors
- [x] 6.2 All existing tests pass
- [x] 6.3 Verify placeholder text appears in empty ProposeChangeDialog fields
- [x] 6.4 Verify workflow banner appears on first proposal and not on subsequent ones
- [x] 6.5 Verify "What's Next" notification fires after first proposal only
