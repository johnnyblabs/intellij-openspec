## 1. Capture and parse

- [x] 1.1 Commit the real `openspec update` captures from CLI 1.13.2 and 1.14.3 (project with `init --tools cursor` plus three legacy `.cursor/commands/openspec-*.md`, isolated `HOME`/`XDG_*`), sanitized to `/fixture`, as `fixtures/cli/<version>/update-legacy-pending.txt`, with provenance in the fixtures README. Verify: no machine paths leak; the two files differ only in the guidance line (and the CLI version on the closing "up to date" line).
- [x] 1.2 Rework `UpdateOutputParser` per D1: return paths plus guidance line, tolerating any non-bullet lines before the first bullet; keep `parseLegacyCleanup` as a wrapper. Verify: `UpdateOutputParserContractTest` asserts paths and guidance line for the 1.6.0, 1.7.0, 1.13.2 and 1.14.3 captures, with expected values read from the captures, and keeps the no-block and partial-block cases. Mutation check: restoring the old "No user content" whitelist fails the 1.14.3 case.

## 2. Dialog and notification

- [x] 2.1 `LegacyCleanupDialog` quotes the CLI's guidance line and starts boxes ticked only for the "No user content to preserve" certification (D2); `UpdateLegacyCleanupService` passes the line through and its notification drops "reports safe to remove". Verify: a headless test of the dialog's initial selection and quoted text for both wordings, and for a missing line; `UpdateLegacyCleanupFlowTest` drives the 1.14.3 capture end to end to the notification. Mutation check: defaulting to ticked for the 1.14 line fails the test.

## 3. Exercise the flow again

- [x] 3.1 Seed legacy Cursor command files in `scripts/seed-lifecycle-demo.sh` (D3); regenerate the committed uiSmoke fixture from it; remove the uiSmoke journey's `< 1.8` skip and its incorrect comment. Verify: on CLI 1.14.3, running `openspec update` in a freshly seeded demo prints the cleanup block, and the fixture diff is only the added Cursor files.
- [x] 3.2 Correct the test-drive skill: the intro and walkthrough Stop 3 describe the full flow with the 1.14 wording (files start unticked when the CLI warns about custom content). Verify: no remaining "CLI < 1.8" statements in the skill.

## 4. Docs and verify

- [x] 4.1 In `docs/openspec-support.md`, replace the 1.14.x "no plugin code change" statement and its matching bottom-line sentence, noting that 1.14.0 reworded the legacy-cleanup notice and the plugin now recognizes it. Add a CHANGELOG `### Fixed` entry (user-facing: the legacy-cleanup review appears again on OpenSpec 1.14, and asks you to choose the files when the CLI warns about custom content). Verify: `DocumentationHygieneTest` is green and a leak grep over the diff is clean.
- [x] 4.2 Run `uiSmoke` locally under `caffeinate -dimsu` with CLI 1.14.3 and confirm the update-cleanup journey now runs and passes. Verify: the journey passes (not skipped). (2026-10-17: `updateActionRaisesCleanupNotice` ran and passed in 38 s against CLI 1.14.3.)
- [x] 4.3 Run `./gradlew build` and `openspec validate fix-update-legacy-cleanup-1-14 --strict`. Verify: both pass.
