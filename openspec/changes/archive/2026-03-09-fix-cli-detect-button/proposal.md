## Why

The Detect button in Settings > Tools > OpenSpec shows "Detecting..." but never updates to show the result. The CLI path field stays empty even when `openspec` is installed and working. This is the first thing a new user interacts with, so a broken detect flow kills confidence immediately.

Root cause: `detection.detect()` runs on a pooled thread with no try/catch — if it throws any runtime exception, the `invokeLater` callback that updates the UI is never reached. The label stays stuck at "Detecting..." forever. Additionally, there's no timeout feedback, so users can't tell if detection is still running or has failed silently.

## What Changes

- Wrap the pooled-thread detection call in try/catch so the UI always updates (success or failure)
- Add error state to the status label when detection throws unexpectedly
- Add LOG.warn for detection failures so they're visible in idea.log without enabling debug logging
- Ensure `cliPathField` and `cliStatusLabel` repaint reliably after `setText()`

## Capabilities

### New Capabilities

### Modified Capabilities
- `settings-panel-sections`: CLI detection feedback must always resolve — never leave UI in "Detecting..." state

## Impact

- **OpenSpecSettingsPanel.java**: `detectCli()` method — add try/catch, error state handling
- **No new dependencies, no API changes, no other files affected**
