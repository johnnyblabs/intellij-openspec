# Proposal

## Why

The plugin runs `openspec update` without a terminal, from three places: the Update action, the post-profile-switch prompt, and the per-tool Update button in Manage AI Tools. Run that way, the CLI takes its non-interactive path:
- **Consent-gated moves happen without asking.** It moves a legacy `.windsurf/` directory to `.devin/`: 12 deletes and 12 adds, with no prompt.
- **Legacy cleanup is skipped,** with only a warning.
- **The scope is invisible.** The user never sees what will change before it does, and the Manage AI Tools button looks per-tool but always updates every configured tool, because `update` has no per-tool option.

OpenSpec CLI 1.15.0 adds `openspec update --dry-run --json`. It reports the exact plan the non-interactive run will carry out, writes nothing, and is a first-class upstream flag. That lets the plugin show the plan and apply it only after the user agrees. Tracked by the linked tracker entry.

## What Changes

- **Preview, then apply (CLI 1.15.0 and later).**
  - **When:** every plugin-started `update` first runs `openspec update --dry-run --json`.
  - **Preview dialog** shows:
    - a summary line of changes by action;
    - upstream's notes, prominently, with the legacy tool-directory move flagged as a warning;
    - any per-tool failures;
    - the changes grouped by tool, deletes first;
    - a separate "Outside this project" group for entries under your home directory or otherwise outside the project, such as `~/.config/openspec/config.json`.
  - **Apply** runs the plain `openspec update` once. **Cancel** changes nothing.
  - **Defaults and blocks:** Cancel is the default button when the plan deletes anything. When the plan reports failures, Apply is disabled and the dialog explains why: the real run stops at the first failing tool.
- **An empty plan isn't run.** When the plan has no changes, no notes and no failures, the plugin reports "Already up to date" and doesn't run the update.
- **A failed dry run is a failure, never "no changes".** Each of these shows the error and offers nothing to apply:
  - exit code 1;
  - a `status[]` error;
  - output that isn't valid JSON.

  The plugin never falls back to an unseen plain update.
- **All three entry points use the preview.**
  - **Update action:** opens the preview.
  - **Profile-switch prompt:** on 1.15 and later, the preview replaces its Yes/No question.
  - **Manage AI Tools Update button:** opens the same project-wide preview, with the clicked tool's group shown first and expanded.
- **Legacy cleanup reads the CLI's plan, not its text (1.15.0 and later).** When the plan's notes report legacy artifacts:
  - after Apply, the plugin takes the files to remove from the read-only `openspec update --dry-run --force --json`, as `delete` entries inside the project;
  - the existing consented cleanup dialog then shows those files, instead of files scraped from the text output;
  - the plugin still never runs `--force` itself.

  On older CLIs the text parser stays.
- **The apply run matches the plan.** Both the dry run and the apply run are started with `OPEN_SPEC_INTERACTIVE=0`, so the CLI takes the same non-interactive path the preview showed.
- **Older CLIs are unchanged.** Below 1.15.0, where `--dry-run` doesn't exist, every entry point keeps today's behaviour.
- **Not persisted.** The plugin keeps no copy of the plan and no "outdated skills" state, and it never diffs generated files itself.
- **Docs and demo:**
  - The feature reference and the README describe the preview.
  - The Update action is renamed "Update OpenSpec…", because it now opens a dialog.
  - The lifecycle walkthrough gets a preview stop.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `cli-update`:
  - "Update action triggers CLI update": on 1.15.0+, the action previews the plan first and applies it only on confirmation.
  - "Legacy-cleanup outcome detection": on 1.15.0+, the files come from the forced dry-run plan, and the text parser stays for older CLIs.
  - "Update action registration": the action is renamed "Update OpenSpec…".
  - New requirements:
    - the preview dialog's content and behaviour;
    - a failed dry run is never read as "no changes";
    - every plugin-started update goes through the preview.

## Impact

- **Code:**
  - A new `UpdatePreviewService` holds one `previewAndApply(project, origin)` path for all three entry points. It owns the `DRY_RUN_FLOOR = 1.15.0` gate, which isn't in `VersionSupport`.
  - A new parser, `UpdatePlanParser`, reads the dry-run JSON.
  - A new `UpdatePreviewDialog` shows the plan.
  - Callers:
    - `OpenSpecUpdateAction`;
    - `WorkflowProfileSwitchService.promptAndRunUpdateIfConfirmed`;
    - `ManageAiToolsDialog`'s Update button;
    - `UpdateLegacyCleanupService`, which takes a second source for the file list.
  - `plugin.xml` gets the action text.
- **Fixtures:** `src/test/resources/fixtures/cli/1.15.0/update-dry-run/`, captured from the real CLI under an isolated HOME and sanitized. Cases:
  - up to date;
  - first run, with the global config add;
  - stale skills;
  - legacy pending, with and without `--force`;
  - the `.windsurf` move;
  - a read-only file;
  - no `openspec/`;
  - `--json` without `--dry-run`.

  Plus the 1.14.3 "unknown option" output.
- **Tests:**
  - parser contract tests against each fixture;
  - service tests on both sides of the floor, through a CLI seam;
  - dialog-model tests for grouping, outside-project entries and Apply gating;
  - the UI smoke journey for the Update action, updated to go through the preview, plus a Cancel check.
- **Docs:** `docs/feature-reference.md`, `README.md`, the lifecycle walkthrough template, and a `CHANGELOG.md` `## Unreleased` Added entry.
- **Platform:** a standard `DialogWrapper`, shown on the EDT after the dry run finishes on a background task. No new platform API. The plugin still supports IntelliJ IDEA 2024.2 and later.
