## Context

The setup wizard and getting-started panel bring users from plugin install through initialization and AI configuration. However, once users reach the "Propose a Change" step, they face a bare `ProposeChangeDialog` with three generic text fields and no guidance. The Plane roadmap item calls for pre-filled defaults, contextual hints, and tips during the first workflow cycle.

Current state:
- `ProposeChangeDialog` has `nameField`, `whyField` (3-row), `whatChangesField` (4-row) — no placeholder text, no examples
- `GettingStartedPanel` NO_CHANGES state shows a single card: "Create your first change" with "Propose a Change" button
- No concept of "first-run" vs "experienced" in the proposal flow
- After successful propose, user sees tree view but no notification explaining next steps

## Goals / Non-Goals

**Goals:**
- First-time users understand what a "change" is before filling in the form
- Placeholder text in ProposeChangeDialog shows realistic examples
- A brief workflow banner appears in the dialog for first-time users
- After first successful proposal, a "What's Next" notification guides the user to generate artifacts
- Experienced users see a clean, uncluttered dialog

**Non-Goals:**
- Interactive tutorial or walkthrough overlay
- Template gallery or change-type picker
- Modifying the setup wizard itself
- Auto-generating proposal content from code analysis

## Decisions

### 1. Placeholder text via `setEmptyText()` on text fields
Add `emptyText` (IntelliJ's grayed-out hint) to each field in `ProposeChangeDialog`. This disappears when the user types and is a standard IntelliJ convention.
- Name: `"e.g., add-user-auth, fix-login-redirect"`
- Why: `"e.g., Users can't reset passwords without contacting support"`
- What Changes: `"e.g., Add password reset endpoint and email notification"`

**Alternative considered:** Tooltip on hover — less discoverable; users may never see it.

### 2. Contextual banner as a `JBLabel` in the dialog header
Add an HTML-formatted `JBLabel` above the form fields (only on first run) explaining the OpenSpec lifecycle: propose → generate → implement → archive. Controlled by a `firstProposalCompleted` flag in `OpenSpecSettings`.

**Alternative considered:** `EditorNotificationPanel` banner — only works in editors, not dialogs.

### 3. First-run flag in `OpenSpecSettings` via `firstProposalCompleted`
A simple boolean persistent setting. Defaults to `false`. Set to `true` after the first successful proposal. The banner and post-proposal notification check this flag.

**Alternative considered:** Count-based (show for first N proposals) — over-engineered for this use case.

### 4. Enhanced NO_CHANGES card in GettingStartedPanel
Expand the description text in the NO_CHANGES state to explain what a change is and how to scope one. Keep it to 2-3 sentences — enough to unblock, not a documentation page.

### 5. Post-proposal "What's Next" notification via OpenSpecNotifier
After the first successful proposal, fire a `GROUP_WORKFLOW` notification titled "What's Next" with content explaining that the user should now generate artifacts from their change. Only fires when `firstProposalCompleted` was previously `false`.

## Risks / Trade-offs

- [Risk] Placeholder text may not match every project's domain → Mitigation: Use generic software examples that translate across domains
- [Risk] Banner adds visual noise → Mitigation: Only shown on first run; disappears permanently after first proposal
- [Risk] "What's Next" notification may be missed if dismissed quickly → Mitigation: The getting-started panel already shows the tree view after propose; notification is supplementary
