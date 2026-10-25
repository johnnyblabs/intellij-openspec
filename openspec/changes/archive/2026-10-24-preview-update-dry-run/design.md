## Context

Three code paths run a plain `openspec update` today:
- `OpenSpecUpdateAction`, through `OpenSpecCliAction`'s background task and the console. Its result goes to `UpdateLegacyCleanupService.handleUpdateResult(stdout)`, which scrapes the "Files to remove" block.
- `WorkflowProfileSwitchService.promptAndRunUpdateIfConfirmed`, which asks a Yes/No question and then calls `runUpdate()`.
- The per-tool Update button in `ManageAiToolsDialog`, which calls `runCliAction("update", …)`.

Upstream 1.15.0 behaviour, checked against `dist/` and live runs under an isolated HOME on 2026-10-24:
- **Shape:** `update --dry-run --json` prints `{changes:[{path,action:"add"|"update"|"delete",tool}], notes:[{code,message}], failures:[{tool,error}]}` (`core/update-plan.d.ts`). The note codes are a closed union today, `legacy-tool-directories` and `legacy-artifacts`. It writes nothing.
- **Paths:** project-relative POSIX; `~/…` under HOME; otherwise absolute (`update-plan.js:289-303`). Entries outside the project come from:
  - the global config (`~/.config/openspec/config.json`, or the APPDATA path on Windows);
  - tools with a global skills directory (today `minimax-code`, `~/.minimax`);
  - the Codex legacy prompt cleanup.
- **The plan isn't always empty when up to date.** It still lists `add ~/.config/openspec/config.json` until the global config exists. A fully current project gives three empty arrays, and so does a project with no tools configured; the two can't be told apart.
- **Failure:** with no `openspec/`, the command exits 1 and prints empty arrays plus `status:[{severity:"error",code:"update_error",message}]`. `status` appears only on failure. `--json` without `--dry-run` exits 1 with plain stderr. On 1.14.3, `--dry-run` exits 1 as an unknown option.
- **Determinism:** the dry run plans the non-interactive path. Without a terminal, the real run performs the `needsConsent` `.windsurf` → `.devin` move and skips legacy cleanup with a warning. `isInteractive()` is false when stdin isn't a TTY, when `CI` is set, or when `OPEN_SPEC_INTERACTIVE=0`. A real run stops at the first tool failure by throwing.
- **Forced dry run:** `--dry-run --force --json` turns the `legacy-artifacts` note into `delete` entries with `tool:"openspec"`, and stays read-only. On an up-to-date project it is empty, because rewrites with identical bytes are dropped.
- **No per-tool scoping:** `update` takes only `[path]`, `--force`, `--dry-run` and `--json`.

## Goals / Non-Goals

**Goals:**
- Nothing changes until the user has seen the plan and agreed to it, on every entry point.
- A failed or unreadable dry run can never look like "up to date".
- The legacy cleanup list comes from the CLI's plan on 1.15.0+.
- Exactly today's behaviour below 1.15.0.

**Non-Goals:**
- Applying part of a plan, or choosing tools. Upstream has no such option.
- A "Force refresh" preview. A forced dry run of an up-to-date project is empty, so there's nothing to show.
- Persisting the plan, keeping an "outdated" state, or diffing generated files in the plugin.
- Running `--force` for the user.
- Showing the CLI's self-upgrade offer, which only appears on a TTY and is suppressed by `OPENSPEC_NO_UPDATE_CHECK=1`.

## Decisions

1. **One service, `UpdatePreviewService`, with an origin.**
   - **Entry:** `previewAndApply(Origin origin, @Nullable String focusTool)`, where the origin is `ACTION`, `PROFILE_SWITCH` or `MANAGE_TOOLS`.
   - **Floor:** it holds `DRY_RUN_FLOOR = "1.15.0"`, checked with `CliVersion.atLeast` against the detected version. This is a CLI-capability gate, not the config-format axis, so it stays out of `VersionSupport`.
   - **Below the floor:** each origin's existing code path runs unchanged.
   - **Seam:** CLI calls go through an `UpdateCli` seam like `UpdateLegacyCleanupService`'s, so tests can feed captured output.
   - *Alternative:* add the preview to each caller. Rejected: three copies of the failure rules would drift, which is the risk the UI review flagged.
2. **A pure parser that returns a sealed result.**
   - **Input and output:** `UpdatePlanParser.parse(exitCode, stdout)` returns either `Plan(changes, notes, failures)` or `Failed(message)`.
   - **Failed** when any of these holds:
     - the exit code is non-zero, with the message taken from `status[].message`, or else from stderr;
     - a `status[]` entry has severity `error`;
     - the output isn't a JSON object;
     - any of the three arrays is missing or isn't an array.
   - **Unknown values:** unknown `action` values and unknown note codes are kept as-is and shown generically. A change is never dropped: the preview must not show less than the CLI will do.
   - **Location:** `Change.outsideProject()` is true when the path starts with `~/` or is absolute, judged on POSIX or Windows rules.
   - **Rule:** the parser never returns an empty plan for a failure.
3. **Background task → modal dialog → background task.**
   - **Order:** the dry run runs in a `Task.Backgroundable` ("Previewing openspec update"). The result is shown on the EDT:
     - a failure goes to the console and a warning;
     - an empty plan gives an info notification;
     - anything else opens `UpdatePreviewDialog`.
   - **Apply:** for the Update action, Apply runs the existing `OpenSpecCliAction` update path, so console output, progress and the cleanup hand-off stay as they are.
   - **Other origins:** the profile switch and Manage AI Tools keep their own result handling after Apply.
   - **Profile switch:** on 1.15+, `promptAndRunUpdateIfConfirmed` calls the service instead of asking Yes/No. Applying is the consent and Cancel is "Later".
4. **Dialog content comes from a pure model.**
   - **Model:** `UpdatePreviewModel.of(plan, focusTool)` builds:
     - the summary line, for example "3 updates, 12 deletes, 12 adds";
     - the note rows, with `legacy-tool-directories` flagged;
     - the failure rows;
     - in-project groups by tool, sorted by name, with the focus tool first and deletes first within each group;
     - one "Outside this project" group last;
     - `applyEnabled`, false when there are failures;
     - `cancelIsDefault`, true when anything is deleted.
   - **Dialog:** `UpdatePreviewDialog` only renders the model, as a tree, a notes banner and a failure list, so every rule is testable without Swing.
5. **The cleanup list from the forced dry run.**
   - **Source:** when the applied plan's notes included `legacy-artifacts`, the 1.15+ path skips `handleUpdateResult`'s text scrape. It runs `update --dry-run --force --json` off the EDT and passes the in-project `delete` paths, with the note's message as guidance, to the existing `LegacyCleanupDialog` notification.
   - **Unchanged:** the regeneration-loop and dismissal state, and the verification re-run.
   - **Failure:** a failed forced dry run reports that the list couldn't be read, and offers nothing.
   - **Older CLIs:** they keep the text parser.
6. **Non-interactive by declaration.** The dry run, the applied run and the forced dry run get `OPEN_SPEC_INTERACTIVE=0`. Stdin is already a pipe, but declaring it guarantees the applied run takes the planned path even if a future runner gives the CLI a TTY.
7. **Fixtures are captured, never written by hand.**
   - **Capture:** capture into `fixtures/cli/1.15.0/update-dry-run/<case>.{json,exit,stderr}` from the real CLI. Use an isolated HOME, `XDG_CONFIG_HOME` unset, and `CI=1`.
   - **Sanitize:** replace the project root with `/fixture/demo-project`, and the home in absolute paths with `/fixture/home`.
   - **Cases:**
     - `up-to-date`;
     - `first-run-global-config`;
     - `stale-skills`;
     - `legacy-pending` and `legacy-pending-force`;
     - `windsurf-move`;
     - `read-only-failure`;
     - `no-openspec-dir`;
     - `json-without-dry-run`.

     Also `1.14.3/update-dry-run-unknown-option`.
   - **Recipes:** the legacy recipe is the cursor recipe from the `1.14.3/update-legacy-pending.txt` README entry; the windsurf one is `init --tools devin`, then `mv .devin .windsurf`.
   - **Documentation:** every case is documented in `fixtures/cli/README.md`.

## Risks / Trade-offs

- **The plan can go stale between preview and Apply.** For example, the user edits a file while the dialog is open. → Apply runs the real update, which works on the files as they are then. The dialog is modal and the window is short. Running a second dry run on Apply was rejected, because it doubles the cost for a race that rarely happens.
- **"Up to date" and "no tools configured" look the same** (an upstream limitation). → The notification says "nothing to update", which is true in both cases.
- **The profile-switch prompt changes shape** on 1.15+, from a Yes/No question to the preview. → `promptAndRunUpdateIfConfirmed` keeps its name and contract (it offers the update, and applies it on consent), so `config-profile` needs no spec change.
- **UI smoke Journey 5 (`updateActionRaisesCleanupNotice`)** runs on a 1.15 CLI, so it now meets the preview. → Its steps are updated to Apply the preview. A Cancel step checks that the project tree is unchanged.
