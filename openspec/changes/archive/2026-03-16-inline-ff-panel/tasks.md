## 1. CardLayout Infrastructure

- [x] 1.1 Add CardLayout to WorkflowActionPanel content area with three card constants: `CARD_NO_CHANGES`, `CARD_FF_INPUT`, `CARD_PIPELINE`
- [x] 1.2 Wrap existing "no changes" components (propose link, FF hyperlink) into a dedicated `noChangesCard` JPanel
- [x] 1.3 Wrap existing pipeline components (chips, generate buttons, progress bar, guidance panel) into a dedicated `pipelineCard` JPanel
- [x] 1.4 Replace manual `setVisible()` calls for no-changes vs pipeline switching with `CardLayout.show()` calls
- [x] 1.5 Verify tool selector, change selector, and toolbar remain outside the card area and visible in all states

## 2. FF Input Form

- [x] 2.1 Create `ffInputCard` JPanel with FormBuilder layout: description JBTextArea (4 rows), name override JBTextField, schema JComboBox (conditional)
- [x] 2.2 Add "Go" and "Cancel" buttons to the FF input card with a status label for error display
- [x] 2.3 Add schema combo population logic (reuse from FfDialog: SchemaService.listSchemas(), visible only when > 1 schema)
- [x] 2.4 Add `deriveKebabName()` utility (move from FfDialog to WorkflowActionPanel or a shared util)
- [x] 2.5 Add input validation: description must not be blank before "Go" is enabled

## 3. FF Activation and State Management

- [x] 3.1 Add `activateFfInput()` public method that switches CardLayout to FF_INPUT and focuses the description field
- [x] 3.2 Wire FF toolbar button (lightning icon) to call `activateFfInput()` instead of `onFastForward()` dialog flow
- [x] 3.3 Wire "Fast-Forward" hyperlink in no-changes card to call `activateFfInput()`
- [x] 3.4 Add cancel logic: "Cancel" button returns to previous card (NO_CHANGES or PIPELINE based on whether changes exist)
- [x] 3.5 Add change selector listener: if user selects a different change while FF_INPUT is showing, cancel FF and show PIPELINE

## 4. FF Change Creation and Generation Trigger

- [x] 4.1 Implement "Go" button handler: disable form, show "Creating change..." status, call `CliRunner.run(project, "new", "change", name)` in background task
- [x] 4.2 On CLI success: update change selector with new change, set `activeChangeName`, switch to PIPELINE card, refresh pipeline chips
- [x] 4.3 On CLI failure: re-enable form, show error in status label, allow retry
- [x] 4.4 After switching to PIPELINE, check `getSelectedDeliveryMode()`: if DIRECT_API, auto-trigger `onGenerateAll()`; if CLIPBOARD or EDITOR_TAB, auto-trigger `onGenerate()` for the first ready artifact
- [x] 4.5 Pass selected schema to CLI command when schema combo is visible and has a selection

## 5. Rewire Entry Points

- [x] 5.1 Update `OpenSpecFfAction.actionPerformed()` to focus the OpenSpec tool window and call `activateFfInput()` on the WorkflowActionPanel instead of opening FfDialog
- [x] 5.2 Add helper method in `OpenSpecToolWindowFactory` or a service to retrieve the active WorkflowActionPanel instance from the tool window
- [x] 5.3 Remove or delete `FfDialog.java`
- [x] 5.4 Remove FfDialog import references from any remaining code

## 6. Testing

- [x] 6.1 Add unit test for `deriveKebabName()` (move existing FfDialog tests if present)
- [x] 6.2 Add unit test verifying CardLayout card switching logic (FF_INPUT → PIPELINE → NO_CHANGES)
- [x] 6.3 Add integration test: FF input → Go → change created → pipeline visible with correct change name
- [x] 6.4 Test delivery-aware trigger: verify Direct API triggers GenerateAll, Clipboard triggers single generate
- [x] 6.5 Test cancel: verify FF_INPUT → Cancel returns to correct previous card
- [x] 6.6 Test validation: verify Go button is disabled when description is empty
