## 1. Interactive Pipeline Chips

- [x] 1.1 Refactor `createPipelineChip()` to return a `JPanel`-based chip with mouse listeners for click, right-click, and hover tooltip support
- [x] 1.2 Add static artifact description map and set tooltips on chips ("proposal: Why this change is needed", etc.)
- [x] 1.3 Implement left-click on completed chips to open the artifact file via `FileEditorManager.openFile()`
- [x] 1.4 Implement right-click context menu on completed chips with "Open" and "Regenerate" actions
- [x] 1.5 Visually highlight the current READY chip with a distinct border or background color
- [x] 1.6 Increase chip font size from 11pt to 12pt and use bolder status icons

## 2. Regeneration Support

- [x] 2.1 Add `getCompletedDownstream(changeName, artifactId)` method to `ArtifactOrchestrationService` that checks for completed downstream artifacts
- [x] 2.2 Add confirmation dialog before regeneration when the artifact has completed downstream dependents
- [x] 2.3 Wire the "Regenerate" context menu action to call `onRegenerateArtifact()` and execute the generation flow

## 3. Delivery-Aware Generate Button

- [x] 3.1 Update `updatePipelineAndButton()` to resolve the delivery method via `DeliveryMethodResolver` and include it in the button label (e.g., "Generate design → clipboard")
- [x] 3.2 After the user selects a method from the dropdown menu, update the button label immediately to reflect the new method
- [x] 3.3 Map delivery modes to short suffixes: CLIPBOARD → "clipboard", DIRECT_API → "API", EDITOR_TAB → "editor tab"

## 4. Artifact File Watcher

- [x] 4.1 Create `ArtifactFileWatcher` utility class that registers a `BulkFileListener` on the change directory and fires a callback when files are created or modified
- [x] 4.2 Add periodic VFS refresh (every 5 seconds) on the change directory as a fallback for external file changes
- [x] 4.3 Add 10-minute timeout that unregisters the watcher and shows a manual refresh hint
- [x] 4.4 Add cleanup: unregister watcher on panel disposal and on change switch

## 5. Inline Guidance (Replace Full-Card)

- [x] 5.1 Replace the guidance card `CardLayout` swap with an inline guidance panel beneath the pipeline that keeps chips visible
- [x] 5.2 Show watching status line with tool-specific instructions beneath the pipeline
- [x] 5.3 Wire file watcher callback to dismiss inline guidance and refresh to next ready artifact
- [x] 5.4 Keep "Copy again" and "Check for updates" buttons in the inline guidance
- [x] 5.5 Show "Next: Generate {artifactId}" indicator in the inline guidance when applicable

## 6. Verification

- [x] 6.1 Verify pipeline chips show tooltips on hover with correct artifact descriptions
- [x] 6.2 Verify clicking a completed chip opens the artifact file in the editor
- [x] 6.3 Verify right-click on completed chip shows "Open" and "Regenerate" menu
- [x] 6.4 Verify Generate button shows delivery method suffix (e.g., "→ clipboard")
- [x] 6.5 Verify file watcher auto-refreshes after artifact file is created externally
- [x] 6.6 Verify inline guidance keeps pipeline visible and shows watching status
- [x] 6.7 Verify regeneration confirmation dialog appears when downstream artifacts are complete
