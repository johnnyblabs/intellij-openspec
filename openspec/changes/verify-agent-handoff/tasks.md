# Tasks

## 1. Hand-off

- [ ] 1.1 `WorkflowInvocation.VERIFY` / `VERIFY_SKILL` (`verify`, `openspec-verify-change`).
- [ ] 1.2 `VerifyHandoff`: the button label per mode and lookup result, the hand-off text, and delivery (or the not-installed notice with Update) (D1, D2).
- [ ] 1.3 `VerifyDialog.report` takes an optional hand-off label; its button closes with `HANDOFF_EXIT_CODE`.

## 2. Callers

- [ ] 2.1 `OpenSpecVerifyAction` and `WorkflowActionPanel.onVerify`: resolve the mode and the verify invocation in the background task, pass the label, and deliver on the hand-off exit code (D3).

## 3. Tests

- [ ] 3.1 Label per mode: none for Direct API; "Verify with <tool>…" with a tool; "Verify with your AI tool…" without.
- [ ] 3.2 Hand-off text per tool from the captured invocations (slash, Codex `$`, 1.x command, plain words), with the change name.
- [ ] 3.3 Delivery per mode: Clipboard copies, Editor Tab opens a tab, not installed notifies with Update.
- [ ] 3.4 The dialog's exit codes are distinct, and both Verify entry points deliver only on the hand-off exit code (source scan).
- [ ] 3.5 Mutations, each turning a test red: a button in Direct API mode; the hand-off without the change name; delivering on Close.

## 4. Docs and verification

- [ ] 4.1 Feature reference (Verify) and a CHANGELOG entry under Unreleased.
- [ ] 4.2 `./gradlew build`, `openspec validate --all --strict`.
