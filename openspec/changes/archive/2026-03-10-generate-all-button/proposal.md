## Why

The Generate All button is the plugin's hero feature — one click to go from empty change to fully populated artifacts. The core orchestration works, but the current UI undersells it: plain text buttons, no progress bar, unicode symbols instead of real icons, and no visual feedback during generation. Users should feel confident and delighted when they click Generate All. Right now it feels like a utility; it should feel like magic.

## What Changes

- **Hero button treatment**: Replace the plain "Generate All" JButton with a visually prominent primary-action button using IntelliJ's `JBUI` styling, a rocket/sparkle icon, and a distinct background color that makes it the obvious call-to-action
- **Animated progress bar**: Add a `JProgressBar` beneath the pipeline that shows determinate progress (1/4, 2/4...) during Generate All, with a smooth fill animation as each artifact completes
- **Pipeline chip animation**: During Generate All, the currently-generating chip gets a pulsing highlight effect (animated border/background), and completed chips transition smoothly to their done state with a brief flash
- **Generating state chip**: Add a new GENERATING visual state for chips — spinning/pulsing indicator distinct from READY and DONE, so users see exactly which artifact is being worked on
- **Elapsed time display**: Show a running timer ("12s elapsed") during generation so users know the system is working and can gauge pace
- **Completion celebration**: When all artifacts complete, briefly flash all chips green with a "All artifacts generated" inline success message and guidance to next step (review or apply)
- **Error recovery**: On failure, show the failed artifact chip in red/error state with an inline "Retry" button, instead of just resetting to normal state
- **Confirmation tooltip**: On first Generate All click (per session), show a brief tooltip-style confirmation: "Generate 4 artifacts via [API provider]?" with a proceed button

## Capabilities

### New Capabilities
- `generate-all-ux`: Visual polish for the Generate All experience — hero button styling, animated progress, chip animations, completion celebration, and error recovery UX

### Modified Capabilities
- `workflow-panel`: Update pipeline chip rendering to support generating/error states, animated transitions, progress bar, and hero button styling
- `generate-all`: Add GENERATING artifact status support and elapsed time tracking to the orchestration listener callbacks

## Impact

- **WorkflowActionPanel**: Major UI changes — hero button, progress bar, chip animations, timer, error/completion states
- **ArtifactStatus enum**: Add GENERATING state
- **GenerateAllListener**: Add timing callback or elapsed time reporting
- **createPipelineChip()**: Support animated generating state and error state rendering
- **No breaking changes**: Single-generate flow, clipboard/editor delivery unchanged
- **No new dependencies**: Uses only IntelliJ Platform SDK (Swing Timer for animation, JBUI for styling)
