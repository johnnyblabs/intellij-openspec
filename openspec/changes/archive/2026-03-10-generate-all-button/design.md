## Context

The Generate All feature exists and works: `ArtifactOrchestrationService.generateAllRemaining()` loops through the DAG, calls `DirectApiService.generate()` for each artifact, fires `GenerateAllListener` callbacks, and supports cancellation. The `WorkflowActionPanel` shows pipeline chips (unicode ✓/●/○), a plain "Generate All" JButton, and a text-only progress label ("Generating design... 2/4").

The UI works but feels utilitarian. This design covers the visual enhancements to make Generate All feel like the plugin's signature experience.

## Goals / Non-Goals

**Goals:**
- Make the Generate All button visually prominent — the obvious primary action
- Provide clear, animated progress feedback during generation
- Show which artifact is actively generating with a distinct visual state
- Deliver a satisfying completion experience
- Offer inline error recovery (retry) instead of silent reset
- Keep all changes within `WorkflowActionPanel` and its supporting enums — no new services

**Non-Goals:**
- Changing the orchestration logic (DAG ordering, API calls, cancellation)
- Adding new dependencies beyond IntelliJ Platform SDK
- Modifying clipboard or editor-tab delivery flows
- Redesigning the overall panel layout

## Decisions

### 1. Hero button: JBUI styling with AllIcons

Use IntelliJ's built-in styling to make Generate All visually distinct:
- `generateAllButton.putClientProperty("JButton.buttonType", "gradient")` — IntelliJ's gradient button style
- Set icon to `AllIcons.Actions.Execute` (play triangle) — universally understood "go" icon
- Button text: "Generate All (4)" showing artifact count
- Slightly larger font via `deriveFont(Font.BOLD)`

**Why not a custom icon?** AllIcons are already theme-aware (light/dark) and render crisply at all DPI scales. Custom SVGs add maintenance burden.

**Alternative considered:** Using `DarculaButtonUI` painted button — rejected because it's Darcula-specific and doesn't adapt to other themes.

### 2. Progress bar: JProgressBar integrated into pipeline area

Add a `JProgressBar` directly below the pipeline chips:
- Determinate mode: value = completed artifacts, max = total artifacts
- Uses IntelliJ's default `JProgressBar` which is already theme-aware
- Hidden when not generating; fades in when Generate All starts
- String painting enabled: shows "2 of 4 artifacts" text on the bar

**Why below pipeline, not above buttons?** The pipeline and progress bar form a visual unit — "here's the plan, here's how far along we are." Buttons are actions, separate from status.

### 3. Chip animation: Swing Timer pulse for GENERATING state

During Generate All, the currently-generating chip gets a pulsing border:
- `javax.swing.Timer` at 600ms interval toggles between bright and dim border color
- Use JBColor for theme awareness: `new JBColor(new Color(60, 130, 230), new Color(80, 150, 250))`
- Timer disposed when artifact completes or generation cancelled
- Completed chips get a brief green flash (300ms bright green border, then normal done state)

**Why Swing Timer over `Thread.sleep`?** Swing Timer fires on EDT, avoiding threading issues. It's the standard Swing animation pattern.

**Alternative considered:** CSS-like animation via `FlatLaf` properties — rejected because it's LAF-specific and not all IntelliJ themes use FlatLaf.

### 4. GENERATING status: extend ArtifactStatus enum

Add `GENERATING` to the `ArtifactStatus` enum. During Generate All, when `onArtifactStarted` fires, the chip for that artifact switches to GENERATING state with:
- Pulsing blue border (animated)
- Spinner-style icon: rotating `AllIcons.Process.Step_1` through `Step_12` (IntelliJ's built-in animated spinner icons)
- Text color: blue (matching READY but with animation to distinguish)

The orchestration service doesn't need to change — the GENERATING state is purely a UI concept set by the listener callback in WorkflowActionPanel.

### 5. Elapsed time: simple System.nanoTime delta

Track `generateAllStartTime` in WorkflowActionPanel. A Swing Timer at 1-second intervals updates a small label next to the progress bar showing elapsed time in "Xs" or "Xm Ys" format. Disposed on completion/cancellation/error.

No changes needed to GenerateAllListener — timing is entirely client-side.

### 6. Completion celebration: inline success with all-green flash

On `onAllComplete()`:
- All pipeline chips briefly flash bright green (300ms via Timer)
- Progress bar fills to 100% with green color (`bar.setForeground(...)`)
- Inline success message: "All artifacts generated — ready to review or apply"
- Progress bar and timer label fade out after 3 seconds, leaving the normal all-done state

### 7. Error state: red chip with retry button

On `onError(artifactId, exception)`:
- Failed artifact chip turns red: red border, red text, error icon (`AllIcons.General.Error`)
- Progress bar turns red/stops
- Inline error message below pipeline: "[artifact] failed: [message]"
- "Retry" button appears that re-triggers `onGenerateAll()` (orchestration already skips completed artifacts)
- Error state persists until retry or manual refresh

### 8. First-click confirmation: skip it

**Decision: No confirmation dialog.** The button already requires Direct API to be configured and visible. Adding a confirmation adds friction to the hero experience. If users want to stop, they have the Cancel button. The button text "Generate All (4)" makes the scope clear.

**Alternative considered:** Tooltip-style confirmation on first use — rejected because it interrupts the "click and watch" magic. Users who click Generate All know what they want.

## Risks / Trade-offs

- **Swing Timer accumulation** → Mitigation: All timers stored in instance fields and explicitly disposed in a `disposeAnimations()` method called on completion, cancellation, error, and panel disposal
- **EDT performance with animations** → Mitigation: Animations are lightweight (border color changes, icon swaps) — no custom painting. Timer intervals (600ms pulse, 1s clock) are conservative
- **AllIcons.Process.Step_N availability** → Mitigation: These icons exist since IntelliJ 2020.x; our minimum is 2024.2. Fallback: use unicode ⟳ if icon loading fails
- **Theme inconsistency** → Mitigation: All colors use JBColor with explicit light/dark pairs; all icons from AllIcons are theme-aware by design
