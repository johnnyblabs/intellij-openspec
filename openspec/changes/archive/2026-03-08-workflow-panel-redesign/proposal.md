## Why

The WorkflowActionPanel guides users through artifact generation but lacks intuitiveness. Pipeline chips are tiny text characters that don't convey meaning to new users. There's no clear "where am I" indicator, no way to go back and edit or regenerate completed artifacts, and the delivery method is invisible until you click the dropdown chevron. After clipboard delivery the guidance card feels like a dead end — the user must manually click "Check for updates" with no awareness of whether the artifact file has appeared. The panel needs to feel like a guided workflow, not a status dashboard.

## What Changes

- Replace tiny text pipeline chips (✓ ● ○) with larger, interactive step indicators that show descriptions on hover and support click-to-open for completed artifacts
- Add a right-click context menu on completed pipeline chips with "Open" and "Regenerate" actions
- Show the current delivery method label on the Generate button itself (e.g., "Generate design → clipboard") so users know what will happen before clicking
- Replace the post-clipboard guidance card dead end with a file-watching "Waiting for design.md..." state that auto-detects artifact file changes and refreshes
- Add an inline "Next: Generate specs" prompt after artifact completion for a smoother flow
- Display a brief description tooltip on each pipeline chip explaining the artifact's role (e.g., "proposal: why this change is needed")

## Capabilities

### New Capabilities
- `interactive-pipeline`: Clickable pipeline chips with tooltips, open-on-click for completed artifacts, and right-click regenerate
- `delivery-aware-button`: Generate button that displays the active delivery method and updates dynamically
- `artifact-file-watcher`: File watcher that detects when artifact files appear or change after clipboard/editor delivery and auto-refreshes the panel

### Modified Capabilities
- `workflow-panel`: Pipeline visualization and post-generation guidance card behavior are changing
- `change-selector`: No requirement changes, but layout adjustments may be needed to accommodate new pipeline design

## Impact

- `WorkflowActionPanel.java` — Major restructure of pipeline rendering, generate button, and guidance card
- New `ArtifactFileWatcher` utility class for VFS-based file watching
- `ArtifactOrchestrationService.java` — May need a `regenerateArtifact()` method for the regenerate action
- No API changes, no dependency changes
