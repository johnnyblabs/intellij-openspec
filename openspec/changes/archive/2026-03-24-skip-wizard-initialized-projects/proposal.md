## Why

When a user opens the plugin on a project that already has OpenSpec initialized (e.g., cloned repo with `openspec/` directory, specs, and changes), the setup wizard launches anyway because the `setupCompleted` flag defaults to `false`. This blocks access to the Browse tree view and forces users through an unnecessary wizard flow. The plugin should detect that the project is already set up and go straight to the tree.

## What Changes

- Skip the setup wizard when the project is already initialized (any state except NOT_INITIALIZED)
- Show the tree view for all initialized projects regardless of changes or AI configuration
- Auto-set `setupCompleted = true` for already-initialized projects so the wizard never re-triggers
- Preserve existing wizard behavior for genuinely uninitialized projects
- Promote Apply to the icon bar as a first-class action alongside Verify and Archive

## Capabilities

### New Capabilities

_(none)_

### Modified Capabilities

- `onboarding`: The setup wizard auto-launch condition changes — wizard is skipped when the project is already initialized. Tree view shown for all initialized states.
- `pipeline-interaction`: Apply action promoted from overflow menu to the icon bar, positioned before Verify and Archive as the primary post-completion action.

## Impact

- `OpenSpecToolWindowFactory.java` — conditional logic around wizard launch and content display
- `WorkflowActionPanel.java` — Apply button added to icon bar
- No API changes, no dependency changes, no breaking changes
