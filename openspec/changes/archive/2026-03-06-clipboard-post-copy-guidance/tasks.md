## 1. Guidance Card UI

- [x] 1.1 Add a "guidance" card to WorkflowActionPanel's CardLayout that shows: confirmation label, tool-aware paste instruction, output path hint, and action buttons
- [x] 1.2 Build the guidance card content dynamically using `AiToolDetectionService.getPrimaryToolLabel()` for the tool name and `ArtifactInstruction.getOutputPath()` for the save path
- [x] 1.3 Add a "Copy again" button that re-copies the last prompt to clipboard
- [x] 1.4 Add a "Check for updates" button that dismisses the card, invalidates the DAG cache, and refreshes the panel

## 2. Wire Into Generation Flow

- [x] 2.1 In `executeGeneration()`, after CLIPBOARD delivery, switch CardLayout to the guidance card instead of showing the status link
- [x] 2.2 In `executeGeneration()`, after EDITOR_TAB delivery, switch CardLayout to the guidance card (without "Copy again" since the prompt is in an editor tab)
- [x] 2.3 Store the last generated prompt and artifact output path so "Copy again" and the output path label can reference them

## 3. Cleanup

- [x] 3.1 Remove the old `statusLink` field and its mouse listener (replaced by the guidance card)
- [x] 3.2 Verify the plugin builds and all tests pass
