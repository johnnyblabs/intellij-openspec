## 1. Fixtures and parser

- [x] 1.1 Capture the dry-run fixtures from the real 1.15.0 CLI per design decision 7: the ten `1.15.0/update-dry-run/` cases plus `1.14.3/update-dry-run-unknown-option`. Sanitize the paths, and document each case and its recipe in `fixtures/cli/README.md`. Check by hashing the scratch project and HOME before and after each dry run that none of the captures wrote a file.
- [x] 1.2 Add `UpdatePlanParser.parse(exitCode, stdout, stderr)`, returning a sealed `Plan` or `Failed`, and `Change.outsideProject()`. Add a contract test, `UpdatePlanContractTest`, with one test per fixture:
  - **Plans:** `up-to-date` gives an empty plan; `first-run-global-config` gives one outside-project add; `stale-skills` gives update entries per tool; `windsurf-move` gives the note plus the deletes and adds.
  - **Legacy:** `legacy-pending` gives the `legacy-artifacts` note and no deletes; `legacy-pending-force` gives in-project `delete` entries.
  - **Failures inside a plan:** `read-only-failure` gives failures and exit 0.
  - **Failed:** `no-openspec-dir`, `json-without-dry-run` and the 1.14.3 unknown option each give `Failed`, with the CLI's message.
- [x] 1.3 Add parser edge-case tests with minimal inputs. Each one must fail if the parser treats the case as an empty plan:
  - non-JSON output with exit 0;
  - a JSON array instead of an object;
  - a missing `failures` key;
  - a `status` error with exit 0;
  - an unknown action;
  - an unknown note code;
  - Windows drive and UNC paths counted as outside the project.

## 2. Model and dialog

- [x] 2.1 Add `UpdatePreviewModel.of(plan, focusTool)` per design decision 4. Test it on the captured plans:
  - the summary line;
  - the `legacy-tool-directories` note flagged;
  - groups sorted by name, with the focus tool first;
  - deletes first within each group;
  - the outside-project group last and separate;
  - `applyEnabled` false with failures;
  - `cancelIsDefault` true with deletes;
  - an unknown note's message still shown.
- [x] 2.2 Add `UpdatePreviewDialog`, a `DialogWrapper` that renders the model:
  - a notes banner;
  - a failure list with the "stops at the first failing tool" explanation;
  - the tree of groups;
  - Apply and Cancel, with Apply disabled and Cancel as the default per the model.

  Verify in a platform test that builds the dialog's panel from the `windsurf-move` and `read-only-failure` plans, checking the Apply enabled state, the default button and the visible group names.

## 3. Service and entry points

- [x] 3.1 Add `UpdatePreviewService.previewAndApply(origin, focusTool)` with `DRY_RUN_FLOOR`, the `UpdateCli` seam and `OPEN_SPEC_INTERACTIVE=0` on its runs. Test with captured output through the seam:
  - **Below the floor (1.14.3):** no `--dry-run` call is made and the origin's direct path runs.
  - **At the floor (1.15.0):** the dry run is called first with `--dry-run --json`.
  - **Empty plan:** an info notification, and no apply run.
  - **Failed:** a warning with the CLI message, and no apply run.
  - **Apply:** exactly one plain `update` call.
  - **Cancel:** no further call.
- [x] 3.2 `OpenSpecUpdateAction`: on 1.15+, go through the service, and keep the console output and cleanup hand-off after Apply. Change the action text in `plugin.xml` to "Update OpenSpec…". Update any test or doc that pins the old text.
- [x] 3.3 `WorkflowProfileSwitchService.promptAndRunUpdateIfConfirmed`: on 1.15+, call the service in place of the Yes/No question, keeping its success and failure notifications. Below 1.15 keep the question. Test both sides.
- [x] 3.4 `ManageAiToolsDialog`'s per-tool Update button: on 1.15+, call the service with the clicked tool as the focus, and keep the reload of the tool list after Apply. Below 1.15 keep `runCliAction("update", …)`.
- [x] 3.5 `UpdateLegacyCleanupService`: on 1.15+, when the applied plan's notes include `legacy-artifacts`, take the files from the forced dry run (design decision 5) and skip the text scrape. Test with the `legacy-pending-force` fixture:
  - the in-project deletes are offered;
  - an outside-project entry is never offered;
  - a failed forced dry run offers nothing and says why;
  - no call ever passes `--force` without `--dry-run`.

  Keep the existing text-path tests green for older CLIs.
- [x] 3.6 Mutation-check by hand. Each new test group must fail when one of these is reverted:
  - treat `Failed` as an empty plan;
  - drop the `status[]` check;
  - drop outside-project grouping;
  - enable Apply with failures;
  - run the apply on an empty plan;
  - skip the floor check;
  - pass `--force` to the real run;
  - drop `OPEN_SPEC_INTERACTIVE=0`.

## 4. UI smoke, demo and docs

- [x] 4.1 Update UI smoke Journey 5 (`updateActionRaisesCleanupNotice`): trigger Update, wait for the preview, then Cancel and check that the legacy files are still on disk. Trigger Update again, Apply, and wait for the cleanup notice as before. Check whether any other journey or the screenshot tour presses Update, and update it the same way.
- [x] 4.2 Lifecycle walkthrough template: add a stop for the preview: Cancel changes nothing, Apply runs the update, and a second Update reports nothing to update. If the seeded demo has no legacy files or `.windsurf` directory, describe the stop with the `first-run-global-config` case.
- [x] 4.3 `docs/feature-reference.md`:
  - describe the preview and its groups;
  - say that the Manage AI Tools Update button and the profile switch now go through it;
  - explain why there's no per-tool update;
  - cover the "already up to date" message, the failure handling, and the older-CLI behaviour.

  Rename the action in docs that name it.
- [x] 4.4 `README.md`: a short mention under the Update feature. `CHANGELOG.md` `## Unreleased` Added entry: Update now shows what it will change, and applies only when you agree (CLI 1.15+).

## 5. Verify

- [x] 5.1 `./gradlew build` is green, and `openspec validate preview-update-dry-run --strict` is clean.
- [x] 5.2 Run `./gradlew verifyPlugin`, because there's a new `DialogWrapper` and tree. Then run `caffeinate -dimsu ./gradlew uiSmoke`, where every journey must be green.
- [ ] 5.3 Manual check in the sandbox (`lifecycle-testdrive`) on CLI 1.15.0: Update shows the preview. Cancel leaves the project unchanged, and Apply runs and refreshes it.

## Workflow follow-up

- Archive the change, then close the tracker entry.
