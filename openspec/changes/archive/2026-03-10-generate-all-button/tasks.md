## 1. Hero button styling

- [x] 1.1 Style `generateAllButton` with gradient button type (`putClientProperty("JButton.buttonType", "gradient")`), bold font, and `AllIcons.Actions.Execute` icon
- [x] 1.2 Update `updateGenerateAllVisibility()` to set button text to "Generate All (N)" where N is the count of remaining non-done artifacts
- [x] 1.3 Ensure hero button visibility logic is unchanged: only visible when Direct API configured AND 2+ artifacts remain

## 2. Progress bar

- [x] 2.1 Add a `JProgressBar` field to WorkflowActionPanel, positioned below the pipeline panel in the info column layout
- [x] 2.2 Show progress bar on Generate All start: set maximum to total artifacts, value to 0, string painting enabled with "0 of N artifacts"
- [x] 2.3 Increment progress bar in the `onArtifactCompleted` listener callback, update string to "X of N artifacts"
- [x] 2.4 Hide progress bar when not generating (initial state, after completion/cancel/error auto-dismiss)

## 3. Elapsed time display

- [x] 3.1 Add an elapsed time `JBLabel` field next to the progress bar, and a `javax.swing.Timer` field for 1-second interval updates
- [x] 3.2 Start the elapsed timer on Generate All begin, recording `System.nanoTime()` as start time
- [x] 3.3 Format elapsed time as "Ns elapsed" (under 60s) or "Nm Ns elapsed" (60s+) and update the label each tick
- [x] 3.4 Stop the timer on completion/cancellation/error, show final time for 3 seconds before hiding

## 4. Pipeline chip generating state

- [x] 4.1 Add `GENERATING` value to the `ArtifactStatus` enum
- [x] 4.2 In `createPipelineChip()`, handle `GENERATING` status: pulsing blue border via Swing Timer (600ms toggle), use `AllIcons.Process.Step_1` through `Step_12` rotating icons, blue text color
- [x] 4.3 In the `onArtifactStarted` listener, track the generating artifact ID and call `refreshPipelineChips()` with a locally-modified DAG that sets that artifact to GENERATING status

## 5. Completion celebration

- [x] 5.1 In `onAllComplete()`, set progress bar foreground to green and display string "All complete"
- [x] 5.2 Flash all pipeline chips with a bright green border for 300ms using a one-shot Swing Timer, then restore normal done state
- [x] 5.3 Show inline success message "All artifacts generated" below pipeline with guidance text ("ready to review or apply")
- [x] 5.4 Auto-hide progress bar and elapsed time label after 3 seconds via Timer, leaving the success message and normal pipeline state

## 6. Error recovery UX

- [x] 6.1 In `onError()`, set the failed artifact chip to red state: red border, red text, `AllIcons.General.Error` icon
- [x] 6.2 Set progress bar foreground to red and stop advancement
- [x] 6.3 Show inline error message below pipeline: "[artifact] failed: [message]" with a "Retry" button
- [x] 6.4 Wire Retry button to call `onGenerateAll()` which re-runs Generate All (orchestration already skips done artifacts)

## 7. Animation cleanup

- [x] 7.1 Create a `disposeAnimations()` method that stops and nulls all active Swing Timers (pulse timer, elapsed timer, flash timers)
- [x] 7.2 Call `disposeAnimations()` from `onAllComplete()`, `onCancelled()`, `onError()`, and when the panel is removed from the hierarchy (override `removeNotify()`)
