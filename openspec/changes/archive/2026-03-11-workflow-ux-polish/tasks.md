## 1. Color Constants Extraction

- [x] 1.1 Define named `JBColor` constants at the top of `WorkflowActionPanel` for all chip state colors (DONE green, READY blue, GENERATING blue, ERROR red, BLOCKED gray) with tuned dark-mode values per design decision 6.
- [x] 1.2 Define named `JBColor` constants for guidance text colors (success green, error red, watching gray, next-tip blue).
- [x] 1.3 Define named `JBColor` constants for chip background and border colors (READY background/border, GENERATING background/border bright/dim, ERROR background/border).
- [x] 1.4 Replace all inline `new JBColor(new Color(...), new Color(...))` calls in `createPipelineChip` with the named constants.
- [x] 1.5 Replace all inline color calls in `showInlineGuidance`, `showApplyGuidance`, `showSyncOutcome`, and Generate All listener callbacks with the named constants.
- [x] 1.6 Replace the success green used in `flashPipelineChipsGreen` and progress bar completion with the named constant.

## 2. Font Size Hierarchy

- [x] 2.1 Update the change name label (`singleChangeLabel`) and combo box font to use 13f Bold (primary tier).
- [x] 2.2 Update `guidanceMessageLabel` in `createWrappingLabel` call to use 13f Bold (primary tier) for result messages.
- [x] 2.3 Verify pipeline chip name labels use 12f Plain (secondary tier — already set, confirm no changes needed).
- [x] 2.4 Update `taskProgressLabel` font to 12f Plain (secondary tier, currently 11f).
- [x] 2.5 Verify guidance watching/next labels and `elapsedTimeLabel` use 11f (tertiary tier — already set, confirm no changes needed).

## 3. HiDPI-Aware Spacing

- [x] 3.1 Wrap the outer panel border padding values (`6, 8`) in `JBUI.scale()`.
- [x] 3.2 Wrap `FlowLayout` gap values in `pipelinePanel` (`6, 0`), `actionRow` (`4, 0`), `guidanceButtons` (`8, 0`), `toolRow` (`4, 0`), and `progressRow` (`8, 0`) in `JBUI.scale()`.
- [x] 3.3 Wrap `Box.createVerticalStrut()` values (`2`, `4`) inside `guidancePanel` and the content panel in `JBUI.scale()`.
- [x] 3.4 Wrap `BorderLayout` gap values in `headerRow` (`8, 0`) in `JBUI.scale()`.
- [x] 3.5 Wrap the progress bar preferred size width (`200`) and height (`18`) in `JBUI.scale()`.

## 4. Section Separators and Compound Borders

- [x] 4.1 Add a compound border to the pipeline panel combining a 1px top line (`JBColor.border()`) with `JBUI.Borders.empty` top/bottom padding.
- [x] 4.2 Add a compound border to the action button row combining a 1px top line with empty top/bottom padding.
- [x] 4.3 Update the existing `guidancePanel` border to a compound border combining a 1px top line with empty top padding.
- [x] 4.4 Remove the standalone `Box.createVerticalStrut(4)` between headerRow and pipelinePanel in the content panel (replaced by section compound borders).

## 5. Compile and Verify

- [x] 5.1 Run `./gradlew compileJava` and confirm clean compilation with no errors.
- [x] 5.2 Run `./gradlew clean test` and confirm all existing tests still pass with no regressions.
