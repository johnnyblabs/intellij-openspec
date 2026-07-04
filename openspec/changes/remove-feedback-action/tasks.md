# Tasks — Remove the Send OpenSpec Feedback action

## 1. Code removal

- [x] 1.1 Delete `actions/OpenSpecFeedbackAction.java`, `dialogs/FeedbackDialog.java`, and their `plugin.xml` registrations (OpenSpec menu + tool-window toolbar)
- [x] 1.2 Delete `OpenSpecFeedbackActionTest` and `FeedbackDialogValidationTest`; full suite green

## 2. Docs & decision record

- [x] 2.1 CHANGELOG: delete the unreleased Send OpenSpec Feedback bullet
- [x] 2.2 `docs/openspec-support.md`: mark the `feedback` row deliberately-not-surfaced with the channel-confusion rationale; remove the roadmap "fill remaining workflow gaps (`feedback`, …)" mention
- [x] 2.3 `docs/feature-reference.md`: remove the action row
- [x] 2.4 Walkthrough template: drop the feedback stop (renumber)

## 3. Coordination with in-flight work

- [x] 3.1 `expand-ui-smoke-walkthroughs`: remove the feedback-dialog journey from code and revise its artifacts to a five-journey suite
