## Why

The Fast-Forward (FF) dialog is a modal popup that hardcodes Direct API as the only generation path, ignoring the user's delivery method selection in the WorkflowActionPanel. Users who use Clipboard or Editor Tab delivery (e.g., Claude Code, Copilot) get a "No AI provider configured" error even when their AI tool is properly set up. Moving FF inline into the panel lets it inherit the already-selected tool and delivery method, eliminating this mismatch and providing a more cohesive UX.

## What Changes

- **Remove FfDialog popup** — the modal dialog (`FfDialog.java`) is replaced by an inline card within WorkflowActionPanel
- **Add FF input card to WorkflowActionPanel** — a CardLayout swaps between the existing pipeline view and a new FF input form (description, name override, schema selector) when the user clicks the FF button
- **Delivery-aware FF generation** — after creating a change via CLI, FF routes through the user's selected delivery method: Direct API auto-generates all artifacts; Clipboard/Editor Tab creates the change then hands off to the normal per-artifact generation flow
- **Rewire FF entry points** — the menu action (`OpenSpec.Ff`), toolbar lightning button, and "Fast-Forward" hyperlink all activate the inline FF input state instead of opening a dialog
- **Deprecate/remove FfDialog** — the popup dialog class is no longer needed

## Capabilities

### New Capabilities
- `ff-panel`: Inline Fast-Forward input form within WorkflowActionPanel with CardLayout state management and delivery-aware generation routing

### Modified Capabilities
- `workflow`: WorkflowActionPanel gains FF_INPUT state, CardLayout for view switching, and delivery-aware FF generation flow
- `ai-integration`: FF generation respects the delivery method resolution chain instead of hardcoding Direct API

## Impact

- **Code**: `WorkflowActionPanel.java` (major — new state + CardLayout + FF form), `FfDialog.java` (removed or deprecated), `OpenSpecFfAction.java` (rewired to activate panel state instead of dialog)
- **UI**: FF input form replaces the pipeline area temporarily; no new tool window tabs or dialogs
- **Dependencies**: No new dependencies; reuses existing `DeliveryMethodResolver`, `ArtifactOrchestrationService`, `CliRunner`, and generation listener infrastructure
