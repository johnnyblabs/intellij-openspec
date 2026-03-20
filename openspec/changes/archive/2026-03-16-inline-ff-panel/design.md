## Context

The Fast-Forward (FF) feature currently lives in `FfDialog.java`, a modal `DialogWrapper` popup. It collects a description and name, then creates a change via CLI and auto-generates all artifacts using `DirectApiService` exclusively. The `WorkflowActionPanel` (the main plugin panel below the Browse tree) already has a tool selector dropdown, delivery method resolution, and per-artifact generation for all three delivery modes (Clipboard, Editor Tab, Direct API). These two code paths are completely disconnected — FF ignores the panel's tool selection.

The WorkflowActionPanel has an implicit state machine managing its view: no-changes, pipeline-with-generate, generating, complete. It uses manual `setVisible()` calls to toggle component groups rather than a formal layout manager for state switching.

## Goals / Non-Goals

**Goals:**
- Embed the FF input form directly in WorkflowActionPanel, replacing the popup dialog
- FF inherits the panel's already-selected delivery method and tool
- After change creation, FF hands off to the existing generation flow (GenerateAll for Direct API, per-artifact for Clipboard/Editor Tab)
- All three FF entry points (toolbar button, menu action, "Fast-Forward" hyperlink) activate the inline form
- Clean separation of the FF input card from the existing pipeline card via CardLayout

**Non-Goals:**
- Redesigning the tool selector or delivery method resolution — these already work correctly
- Adding new delivery modes or AI providers
- Changing the artifact generation orchestration logic
- Refactoring the entire WorkflowActionPanel state machine (incremental improvement only)

## Decisions

### Decision 1: CardLayout for view switching

Use `java.awt.CardLayout` to manage the content area of WorkflowActionPanel with three cards:
- `NO_CHANGES` — existing "no changes" view with Propose + FF links
- `FF_INPUT` — new form (description textarea, name override, schema combo, Go/Cancel buttons)
- `PIPELINE` — existing pipeline chips, generate buttons, progress bar

**Why CardLayout over manual visibility toggling:** The panel already struggles with manual `setVisible()` on 40+ components. CardLayout gives clean, atomic view switching without visibility bugs. It's the standard Swing pattern for wizard-like flows.

**Alternative considered:** JPanel swapping with `removeAll()`/`add()`. Rejected because it requires rebuilding components on each switch and loses form state if the user cancels and re-enters FF.

### Decision 2: FF form is minimal — description + name + schema

The FF input card contains only:
- `JBTextArea` for description (4 rows, same as current dialog)
- `JBTextField` for optional name override
- `JComboBox` for schema (visible only when multiple schemas exist, same logic as current dialog)
- Go and Cancel buttons

No tool selector in the FF form — it uses the existing panel-level tool selector that's always visible above the card area. This is the key insight: the tool choice is already made.

**Why not duplicate the tool selector in FF:** It would be redundant and could desync with the panel's selector. The whole point of inlining FF is to inherit the existing selection.

### Decision 3: After change creation, transition to PIPELINE card and trigger generation

When the user clicks "Go":
1. CLI creates the change (`openspec new change <name> [--schema <schema>]`)
2. Panel sets `activeChangeName` to the new change name
3. CardLayout switches from FF_INPUT to PIPELINE
4. Change selector updates to include the new change (auto-selected)
5. Generation starts based on the selected delivery mode:
   - **Direct API**: Trigger `onGenerateAll()` automatically (existing flow with progress bar, pipeline chips, elapsed timer)
   - **Clipboard/Editor Tab**: Trigger `onGenerate()` for the first ready artifact (existing flow with clipboard copy or editor tab, plus guidance panel)

**Why auto-trigger instead of just showing the pipeline:** FF's promise is speed. If the user has Direct API configured, they expect one-click generation. If they're on Clipboard, they at least expect the first prompt to be ready immediately.

### Decision 4: Extract FfDialog generation logic, delete the dialog

Move the change-creation logic (CLI call, DAG loading) into a private method within WorkflowActionPanel. The generation itself is already handled by existing `onGenerateAll()` and `onGenerate()` methods. `FfDialog.java` is deleted rather than deprecated — there's no reason to maintain two code paths.

**Why delete not deprecate:** The dialog offers nothing that the inline form doesn't. Keeping it invites confusion and maintenance burden. The `OpenSpecFfAction` menu action is rewired to focus the tool window and activate the FF_INPUT card.

### Decision 5: Menu action focuses tool window instead of opening dialog

`OpenSpecFfAction.actionPerformed()` changes to:
1. Get the OpenSpec tool window via `ToolWindowManager`
2. Activate/show it
3. Call a new public method `WorkflowActionPanel.activateFfInput()` to switch to the FF_INPUT card

This keeps the menu action as a convenience entry point without duplicating UI.

## Risks / Trade-offs

- **[Risk] WorkflowActionPanel size increases** → Mitigation: The FF form components add ~80 lines. The deleted `FfDialog.java` removes ~330 lines. Net reduction in total code. The CardLayout actually simplifies the existing visibility management.
- **[Risk] CardLayout retrofit into existing manual visibility code** → Mitigation: Only the main content area uses CardLayout. The toolbar, tool selector, and change selector remain outside the card area unchanged. Incremental adoption, not full rewrite.
- **[Risk] FF input state lost if user switches changes** → Mitigation: Cancel the FF input (return to PIPELINE/NO_CHANGES) when the change selector changes. The form fields are lightweight — no expensive state to preserve.
- **[Trade-off] FF no longer works without the tool window open** → Acceptable because FF is a tool-window-centric feature. The menu action simply ensures the tool window is visible.
