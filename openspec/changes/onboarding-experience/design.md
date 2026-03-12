## Context

New users face a fragmented first-run experience. The current welcome panel (`OpenSpecToolWindowFactory`, lines 52-105) shows a centered "Initialize OpenSpec" button with a one-line description. After initialization, users land in the tree view with no guidance on CLI setup, AI configuration, or next steps. Empty states are minimal gray HINT nodes ("No specs found", "No active changes") that tell users what's missing but not what to do about it. The AI setup card only appears when users click Generate for the first time — they may never discover it.

Detection services (`CliDetectionService`, `AiToolDetectionService`) already run at startup but their results are only surfaced as a notification (CLI missing) and status bar text. Settings (`OpenSpecSettings`) has no concept of "first run" — it infers setup state from empty `preferredDeliveryMethod`.

## Goals

- A user who installs the plugin SHALL reach their first "Propose a Change" action within 60 seconds
- Every empty state SHALL offer an actionable next step (button or link), never just text
- The wizard SHALL be skippable and re-launchable — never block the user
- All setup steps (CLI, AI, init) SHALL remain independently accessible outside the wizard

**Non-Goals:**
- Changing the settings panel layout or adding new settings tabs
- Tutorial/walkthrough overlays or coach marks
- Changing CLI detection or AI tool detection logic
- Adding new AI providers or delivery methods

## Decisions

### 1. Setup Wizard as a DialogWrapper

**Choice:** Implement the wizard as a multi-step `DialogWrapper` with a card-based layout (one card per step, with back/next/skip navigation).

**Rationale:** `DialogWrapper` is the standard IntelliJ pattern for multi-step setup flows (e.g., New Project wizard). It's modal, so it captures attention on first run without competing with the tool window. Cards are simpler than a full Wizard framework and easier to maintain.

**Steps:**
1. **Welcome** — Brief intro, what OpenSpec does, "Let's get set up" CTA
2. **CLI Detection** — Auto-detect CLI, show result (found/not found), offer manual path entry or "Install later" skip
3. **AI Provider** — Detect AI tools, let user pick preferred tool and delivery method. If Direct API desired, configure provider + key + test connection
4. **Initialize Project** — If `openspec/` doesn't exist, offer to create it. If it exists, show "Already initialized" and skip
5. **Done** — Summary of what was configured, "Create your first change" button that opens the Propose dialog

**Alternatives considered:**
- *Inline setup in tool window*: Too cramped, competes with tree. Good for individual empty states but not a cohesive flow.
- *Notification-based setup*: Easy to dismiss and forget. No sequential flow.

### 2. State-Aware Getting Started Panel

**Choice:** Replace the current welcome panel in `OpenSpecToolWindowFactory` with a `GettingStartedPanel` that adapts based on project state.

**States and their content:**

| State | Detection | Panel Content |
|---|---|---|
| No openspec/ dir | `!OpenSpecFileUtil.isOpenSpecProject()` | "Initialize your project" card + "Run Setup Wizard" link |
| Initialized, no AI configured | `isOpenSpecProject() && preferredDeliveryMethod.isEmpty()` | "Configure your AI tool" card + "Run Setup Wizard" link |
| Initialized, AI configured, no changes | `isOpenSpecProject() && changesEmpty` | "Create your first change" card with Propose button |
| Has changes | Normal state | Standard tool window (no getting-started panel) |

**Rationale:** Users at different stages need different guidance. Showing "Initialize" to a user who already has `openspec/` is confusing.

### 3. Rich Empty States with EmptyStateFactory

**Choice:** Create an `EmptyStateFactory` utility that produces consistent empty-state panels with: icon, title, description, and action button. Use these throughout the tool window.

**Locations:**
- **Tree: Specs section empty** — "No specs yet" + "Specs are created when you propose a change" (informational, no button)
- **Tree: Changes section empty** — "No active changes" + "Propose" button
- **Tree: Root when not initialized** — "Not an OpenSpec project" + "Initialize" button
- **Workflow panel: No change selected** — "Select a change to see its workflow" + change selector prompt
- **Console: Empty** — "CLI output will appear here when you run OpenSpec commands"

**Rationale:** Consistent visual treatment (same font, icon style, spacing) makes the plugin feel polished. A factory avoids duplicating layout code across 5+ locations.

### 4. Wizard Trigger Logic

**Choice:** The wizard launches automatically when ALL of these are true:
- `OpenSpecSettings.setupCompleted` is `false` (new flag, defaults to `false`)
- The tool window is opened for the first time in this session

After completion or skip, `setupCompleted` is set to `true`. Users can re-launch from: toolbar button (wrench icon) or Settings > Tools > OpenSpec > "Run Setup Wizard" button.

**Rationale:** Using a persisted flag avoids re-showing the wizard on every project open. Tying it to tool window open (not project open) means it doesn't pop up for users who haven't engaged with the plugin yet.

### 5. SetupWizardModel for State Management

**Choice:** A plain data class `SetupWizardModel` holds the wizard's state across steps (CLI path, detected tools, selected provider, API key, etc.). Each step reads/writes to the model. On completion, the model writes to `OpenSpecSettings`, `AiCredentialStore`, and optionally calls `ScaffoldingService.initOpenSpec()`.

**Rationale:** Decouples UI from persistence. Makes it testable — we can unit-test the model's state transitions without UI.

## Risks / Trade-offs

- **[Wizard feels heavy for experienced users]** → Mitigated by skip button on every step and one-click "Skip All" on welcome screen. `setupCompleted` flag prevents repeat showing.
- **[State-aware panel adds complexity to tool window init]** → Mitigated by isolating state detection into `GettingStartedPanel` — `OpenSpecToolWindowFactory` just delegates to it.
- **[Empty state buttons duplicate toolbar actions]** → Intentional. Discoverability is more important than DRY for onboarding UX. The buttons call the same actions.
- **[CLI not found during wizard]** → Wizard shows "Not found — built-in features will be used" with option to enter path manually. Not a blocker.
