## 1. Chip Interaction (foundation — replaces generate button)

- [x] 1.1 Add hand cursor to READY and DONE chips; default cursor for BLOCKED and GENERATING
- [x] 1.2 Add hover effect to READY chips: subtle background highlight on mouse enter, restore on exit
- [x] 1.3 Add state-aware tooltips: READY → "Click to generate", DONE → "Click to open · Right-click for options", BLOCKED → "Waiting on: [deps]", GENERATING → "Generating..."
- [x] 1.4 Wire READY chip click to trigger `executeGeneration()` for that artifact using current delivery method
- [x] 1.5 Wire DONE chip click to open the artifact file in the editor (reuse existing `openArtifactFile()` logic)
- [x] 1.6 Add right-click context menu to all chips: DONE → Open/Regenerate/Copy prompt, READY → Generate/Copy prompt, GENERATING → Cancel
- [x] 1.7 Add "Generate All Remaining" item to READY chip context menu when 2+ artifacts are ready and Direct API is configured

## 2. Remove Button Row

- [x] 2.1 Remove `generateButton`, `generateAllButton`, `cancelButton`, `retryButton` from the panel — their functionality is now in chip clicks and context menus
- [x] 2.2 Remove `applyButton`, `verifyButton`, `syncSpecsButton`, `archiveButton`, `bulkArchiveButton`, `startNewChangeButton` from the button row — move to icon bar and overflow menu
- [x] 2.3 Remove the `actionRow` FlowLayout panel entirely
- [x] 2.4 Remove all `setVisible()` calls for the removed buttons and the button-state management logic

## 3. Compact Icon Action Bar

- [x] 3.1 Create icon bar panel with FlowLayout.RIGHT below the pipeline chips: 16x16 ActionButton-style icons for FF (lightning), Verify (checkmark), Archive (box), and overflow (⋯)
- [x] 3.2 Wire FF icon to call `activateFfInput()`
- [x] 3.3 Wire Verify icon to call `onVerify()` — disabled (grayed) when artifacts not all complete
- [x] 3.4 Wire Archive icon to call `onArchive()` — disabled when artifacts not all complete
- [x] 3.5 Create overflow popup menu with: Apply Tasks, Sync Specs, Bulk Archive, Compliance Check — each item disabled when not applicable
- [x] 3.6 Update icon enabled/disabled state in `updatePipelineAndButton()` based on artifact DAG status

## 4. Status Strip

- [x] 4.1 Create a single-line status strip JPanel below the icon bar: FlowLayout.LEFT with compliance label, task progress label, and delivery mode label separated by " · "
- [x] 4.2 Move compliance chip text into the status strip (remove standalone `complianceChip` row)
- [x] 4.3 Move task progress display into the status strip (remove standalone `taskProgressLabel` and `taskHintLabel`)
- [x] 4.4 Show delivery mode indicator in the status strip (e.g., "Clipboard: Claude Code" or "Direct API")
- [x] 4.5 During Generate All, replace status strip content with "Generating N/M... Xs · Direct API" progress text
- [x] 4.6 Remove `generateAllProgressBar` and `elapsedTimeLabel` as separate components — their info is now in the status strip text

## 5. Guidance Popover

- [x] 5.1 Create a `JBPopupFactory` lightweight popup method that takes delivery confirmation text, save-path hint, and optional "Copy again" action
- [x] 5.2 Wire generation completion to show the popover anchored near the just-generated chip (use chip component as anchor)
- [x] 5.3 Add auto-dismiss timer (8 seconds) and click-outside-to-dismiss behavior
- [x] 5.4 Remove the inline `guidancePanel`, `guidanceMessageLabel`, `guidanceWatchingLabel`, `guidanceNextLabel`, `copyAgainButton`, `checkUpdatesButton`, and `guidanceDetailsPanel`
- [x] 5.5 Remove `showInlineGuidance()`, `showApplyGuidance()`, and guidance visibility logic

## 6. Layout Cleanup

- [x] 6.1 Remove `progressRow` panel (progress bar + elapsed time) from pipeline card
- [x] 6.2 Update pipeline card BoxLayout to: pipelinePanel → icon bar → status strip (3 rows total in steady state)
- [x] 6.3 Verify panel maximum height is ~100px in steady state (pipeline chips + icon bar + status strip)
- [x] 6.4 Verify FF input card and no-changes card are unaffected by the changes
- [x] 6.5 Remove horizontal separator border from pipelinePanel (no customLine top border)
- [x] 6.6 Fix pipeline card to use GridBagLayout with HORIZONTAL fill so all child panels span full width
- [x] 6.7 Remove excessive vertical padding between pipelinePanel and iconBar (removed border insets from iconBar and statusStrip)
- [x] 6.8 Switch outer contentPanel from BoxLayout to BorderLayout so headerRow and contentCards fill width

## 7. Testing & Verification

- [x] 7.1 Add tests for chip click routing: READY triggers generation, DONE opens file, BLOCKED/GENERATING do nothing
- [x] 7.2 Add tests for context menu items per chip state
- [x] 7.3 Add tests for icon bar enabled/disabled states based on artifact completion
- [x] 7.4 Add tests for status strip content in different states (steady, generating, tasks in progress)
- [x] 7.5 Verify all existing WorkflowActionPanel tests still pass
- [x] 7.6 Run full test suite — zero regressions
