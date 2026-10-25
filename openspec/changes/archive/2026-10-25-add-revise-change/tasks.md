# Tasks

## 1. Invocation for any workflow

- [x] 1.1 Extend the capture script to record `explore` and `update` rows with a `workflow` field, written to `fixtures/cli/1.15.0/workflow-invocations.json` (design D3). Update the explore fixtures README. Verify: 294 rows, and the `update` rows include `/opsx:update` (claude/both), `/opsx-update` (cursor/commands), `@opsx-update` (amazon-q/commands), `$openspec-update-change` (codex/commands) and `/openspec-update-change` (devin/both).
- [x] 1.2 Generalize `ExploreInvocation` to `WorkflowInvocation` (design D1) and add `AiToolRegistry.commandPath(tool, workflowId)` and `skillPath(tool, skillName)` (design D2). Move Explore onto them. Verify:
  - `WorkflowInvocationContractTest` passes for every row of both workflows, seeding each row's files and checking the invocation and both paths;
  - the existing Explore tests still pass.

## 2. The action

- [x] 2.1 Add `ReviseChangeAction` (`OpenSpec.ReviseChange`, gated on `update`, `DumbAware`) and `ReviseChangeDialog` (design D4). Register them in the OpenSpec menu after Continue, and in the change right-click menu after Explore This Change... (design D6). Verify:
  - the action is shown and enabled for a change and disabled when the profile excludes `update`;
  - it's absent on delta-spec menus;
  - the menu order and change-menu order are right;
  - the dialog collapses a multi-line note to one line.
- [x] 2.2 Add the workflow panel's header button, launched through `ActionLauncher` (design D6). Verify: with a change selected, the panel's header has the Revise button, and activating it runs `OpenSpec.ReviseChange`.
- [x] 2.3 Add the delivery (design D5). Verify:
  - with Claude's update command installed, the clipboard holds `/opsx:update add-auth drop the export feature`, and with an empty note `/opsx:update add-auth`;
  - with Codex's skill, `$openspec-update-change add-auth`;
  - with neither file, nothing is copied and the notification offers Update OpenSpec…;
  - with Direct API, no request is made and the handoff is copied with the redirect note;
  - the editor tab holds the same text.

## 3. Gate, names and docs

- [x] 3.1 Remove `update` from `WorkflowParityContractTest.NOT_SURFACED`, and change Update OpenSpec…'s description (design D6, D7). Verify: the parity tests pass, and with the entry left in, the stale-entry test fails.
- [x] 3.2 Update the feature reference: the Revise row, and the propose → generate → revise → apply loop. Add the walkthrough's Revise stop (`.claude/skills/lifecycle-testdrive/walkthrough-template.md`) and a CHANGELOG `### Added` line. Verify: `DocumentationHygieneTest` passes.
- [x] 3.3 Mutation-check: drop the gating, inline the skill text, call the provider on Direct API, and keep `update` in NOT_SURFACED. Each must fail a test, then be reverted. Run `./gradlew build` and `./gradlew verifyPlugin`, since the diff adds `com.intellij.*` references. Run the full uiSmoke suite (`caffeinate -dimsu`). Verify: all green, 5/5 IDEs Compatible.
