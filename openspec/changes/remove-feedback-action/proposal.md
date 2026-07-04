# Remove the Send OpenSpec Feedback action (decision: not implement)

## Why

The Send OpenSpec Feedback action (added in `feedback-action-and-support-doc-fixes`, still unreleased) creates a channel-confusion trap: `openspec feedback` submits to the **upstream OpenSpec framework maintainers**, but a button inside a JetBrains plugin reads as "send feedback about this plugin." Users would route plugin complaints to the framework team and framework feedback to nobody — a support burden for both sides with no offsetting value, since terminal users already have `openspec feedback` directly. Product decision: **do not implement**; remove before first release so no user ever sees it.

## What Changes

- **Remove the action end-to-end**: `OpenSpecFeedbackAction`, `FeedbackDialog`, their `plugin.xml` registrations (OpenSpec menu + tool-window toolbar), and their tests.
- **Remove the unreleased CHANGELOG entry** (the feature never shipped, so it simply disappears from Unreleased rather than gaining a removal notice).
- **Docs record the decision, not a gap**: the support matrix's `feedback` row is marked *deliberately not surfaced* with the channel-confusion rationale (so it never reads as unfinished coverage), the feature reference and roadmap mentions are removed, and the walkthrough template drops its feedback stop.
- **The `feedback-action` capability spec is REMOVED** with reason and migration recorded.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

(the removal is expressed as a REMOVED delta on `feedback-action`; the coverage-matrix accuracy requirement in `plugin-documentation` is unaffected)

## Impact

- **Code removed:** `actions/OpenSpecFeedbackAction.java`, `dialogs/FeedbackDialog.java`, `plugin.xml` entries, `OpenSpecFeedbackActionTest`, `FeedbackDialogValidationTest`.
- **Docs:** `docs/openspec-support.md` (feedback row + roadmap mention), `docs/feature-reference.md` (action row), CHANGELOG (delete the unreleased bullet), walkthrough template.
- **Related in-flight work:** the `expand-ui-smoke-walkthroughs` change drops its feedback-dialog journey (suite lands at five journeys).
- **Compatibility:** removal of never-released code; no user-facing migration.
