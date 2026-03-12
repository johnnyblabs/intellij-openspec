## Why

New users who install the plugin face a cold start — the current welcome panel has a single "Initialize" button with no guidance on CLI setup, AI configuration, or what to do next. Empty states in the tree are minimal gray hints that don't guide users toward their first productive action. There's no cohesive first-run flow that takes a user from install to their first spec-driven change.

## What Changes

- Add a **first-run setup wizard** dialog that launches on first open (when no settings have been configured), guiding users through CLI detection, AI provider setup, and project initialization in a single flow
- Replace the current minimal welcome panel with a **rich getting-started panel** that shows contextual next-step guidance based on project state (not initialized, initialized but no changes, has changes but no AI configured, etc.)
- Enhance **empty state guidance** throughout the tool window — tree sections, workflow panel, and console — with actionable prompts and links instead of passive gray text
- Add a **re-launchable setup wizard** accessible from Settings or the toolbar for users who skip initial setup

## Capabilities

### New Capabilities
- `first-run-wizard`: Multi-step setup dialog that detects CLI, configures AI provider, and optionally initializes the project
- `empty-state-guidance`: Contextual empty state panels throughout the tool window with actionable next steps

### Modified Capabilities
- `tool-window`: Welcome panel replaced with state-aware getting-started panel
- `ai-setup`: First-run AI configuration integrated into wizard flow (currently only triggered implicitly by empty delivery method)

## Impact

- New classes: `SetupWizardDialog`, `SetupWizardModel`, `GettingStartedPanel`, `EmptyStateFactory`
- Modified: `OpenSpecToolWindowFactory` (welcome panel → getting-started panel), `OpenSpecToolWindowPanel` (empty states), `SpecTreeModel` (richer HINT nodes), `WorkflowActionPanel` (empty state when no change selected), `OpenSpecSettings` (add `setupCompleted` flag)
- New service interaction: wizard calls `CliDetectionService`, `AiToolDetectionService`, `ScaffoldingService`, and `AiCredentialStore` in sequence
- No API or dependency changes
