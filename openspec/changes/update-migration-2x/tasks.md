# Tasks

## 1. Captures of the real migration

- [x] 1.1 Add `scripts/capture-update-migration-fixtures.py`, written in the style of the other 2.x scripts (`scripts/lib/openspec_capture.py`, isolated HOME and `XDG_*`, `OPEN_SPEC_INTERACTIVE=0`, stdin closed, paths sanitized to `/fixture/demo-project` and `/fixture/home`, links as data).
  - Each case seeds a project with CLI 1.17.0 (plain `init --tools …`, the default core profile), then runs the release under test.
  - Per case it writes to `<v>/update-migration/<case>/`:
    - `plan.json` from `update --dry-run --json`;
    - `plan-force.json` from `update --dry-run --force --json`;
    - `applied.txt` (stdout), `applied.stderr` and `applied.exit` from `update`;
    - `before.json` and `after.json`, the layouts in the detect format.
  - Cases on 2.0.0, 2.1.0 and 2.1.1:
    - `unedited`: Claude and Cursor;
    - `edited`: Claude and Cursor, with one line appended to `.claude/commands/opsx/explore.md`, `.claude/skills/openspec-explore/SKILL.md` and `.cursor/skills/openspec-explore/SKILL.md`;
    - `notes-beside-skill`: Claude and Cursor, with a `notes.md` and a `.DS_Store` in both `openspec-explore` folders;
    - `shared-folder`: Claude and Codex, so `.agents/skills/.openspec-target` already exists, with the shared `openspec-explore/SKILL.md` edited;
    - `nested-commands`: Gemini and Claude, unedited;
    - `narrow-profile`: Claude, seeded with the workflows `explore` and `apply` only (`config set`).
  - On 2.1.0 and 2.1.1 also `after-2.0.0-kept`: 2.0.0's `edited` result, updated again.
  - On 2.1.1 also `eve-edited`: an Eve project set up by 2.1.1, with its `agent/skills/openspec-explore/SKILL.md` edited.
  - On 2.1.0 also `no-git`: the applied update with Git off `PATH`.
  - The script refuses to write a file holding a scratch path or the real HOME.
  - Document the family in `fixtures/cli/README.md`, with design.md's table and the further losses.
  - Verify:
    - the captures reproduce the design's table: 2.0.0's `edited/after.json` still holds the edited `.claude` and `.cursor` skill files, and 2.1.x's don't;
    - 2.1.x `after-2.0.0-kept/plan.json` deletes both kept copies (`.claude` and `.cursor`);
    - `no-git/applied.exit` is 1;
    - `FixtureNoSymlinkHygieneTest` and `DocumentationHygieneTest` pass.

## 2. Plan reading: migration, links and at-risk files

- [x] 2.1 Add the migration shape to `UpdatePreviewModel`, per design decision 4:
  - the banner, on a marker add or update;
  - the groups for shared skills, replaced copies and command files, with command paths derived from `EXPLORE_COMMAND_PATHS` over the captured workflow ids;
  - link rows.
  - Verify with `UpdatePreviewModelTest`, against every release's `update-migration/*/plan.json`:
    - the banner shows, including `shared-folder`, where the marker is updated;
    - every tool-folder copy delete lands in the replaced-copies group, including 2.1's `openspec`-tagged Cursor ones;
    - every command-file delete lands in the command-files group, including `nested-commands`' `.claude/commands/opsx/*.md` and `.gemini/commands/opsx/*.toml`;
    - each `.claude/skills/openspec-*` add reads as a link to its shared skill.
    - Negative controls: the existing 1.15–1.17 and 2.x `update-dry-run` plans, and 2.1.x `after-2.0.0-kept`, keep today's grouping with no banner.
    - Mutation: deriving command paths from file names only turns `nested-commands` red.
- [x] 2.2 Add `UpdateMigration.atRiskFiles(projectRoot, plan)` (design decision 2). Verify with a plain test that recreates each capture's `before.json` through `FixtureLayout`:
  - `edited` on 2.1.0 and 2.1.1: exactly the edited `.claude` and `.cursor` `openspec-explore/SKILL.md`; on 2.0.0, nothing;
  - `after-2.0.0-kept` on 2.1.x: both kept copies;
  - `notes-beside-skill` on 2.1.x: each `notes.md` and no `.DS_Store`;
  - `eve-edited`: the overwritten Eve copy; an unedited Eve copy, equal to the shared copy apart from `allowed-tools`, is not at risk;
  - `narrow-profile` on 2.1.x: the unreleased `openspec-apply-change/SKILL.md`, the known false warning recorded in design Risks;
  - `shared-folder` on 2.x: the edited `.agents/skills/openspec-explore/SKILL.md` (a 1.x copy the plan updates);
  - nothing for any `unedited` case, and never the marker or a link;
  - an unreadable file is returned.
  - Mutations, each turning its case red:
    - dropping the released check (`unedited`);
    - dropping `update` actions (`eve-edited`);
    - dropping non-`SKILL.md` files (`notes-beside-skill`);
    - dropping the `allowed-tools` normalization (the unedited Eve copy).
- [x] 2.3 Exclude every path under `*/skills/openspec-*/`, and `.agents/skills/.openspec-target`, from `UpdatePreviewService.legacyDeletes` (design decision 6). Verify with `UpdatePreviewServiceTest`:
  - on 2.1.x `update-migration/*/plan-force.json`, no skill-folder file or marker is offered;
  - the existing 1.17.0 and 2.x `legacy-*-force` captures offer exactly the files they offer today.

## 3. The preview dialog and the apply gate

- [x] 3.1 In `UpdatePreviewService`, compute the at-risk files on the dry run's background thread and pass them into the model. Add the model's `applyEnabled` rule: no failures, and either no at-risk files or the backed-up confirmation ticked.
  - Verify with `UpdatePreviewServiceTest`, through the existing seams and the captured plans:
    - the decider receives the at-risk paths;
    - the files are read on the background seam;
    - Cancel stays the default.
  - Verify with `UpdatePreviewModelTest`: Apply is disabled with at-risk files until the confirmation, and still disabled with failures.
- [ ] 3.2 Render the migration in `UpdatePreviewDialog`:
  - the banner;
  - the red at-risk block (path and action, with design decision 2's wording);
  - the "I've backed up these files" checkbox, which toggles Apply;
  - link rows with a folder icon;
  - `global-skills`, `removed-skills` and `edited-skills` highlighted like `legacy-tool-directories`.
  - Verify with `UpdatePreviewDialogTest`:
    - a link row's text is `<path> → .agents/skills/<skill>`;
    - Apply follows the checkbox;
    - the messages from `2.1.1/update-dry-run/global-skills-removed.json` render as warning rows, verbatim.
  - Run the full `uiSmoke` under `caffeinate -dimsu`, a regression run on the installed CLI 1.17.0.

## 4. After the update: kept files and the 2.1.0 hint

- [x] 4.1 Add `UpdateOutputParser.parseKept(stdout)` for each report in design decision 7.
  - Verify with `UpdateOutputParserContractTest`:
    - every `update-migration/edited/applied.txt` yields `.claude/commands/opsx/explore.md` with the CLI's guidance line;
    - 2.0.0's also yields both skill folders;
    - 2.0.0 `shared-folder` yields the replaced-edits report;
    - every `unedited/applied.txt` and every 1.x update capture yields nothing.
  - Verify the reports without a capture (home prompt files, left-in-place command files, retired-tool files, kept shared skills) against text built from the format strings in the 2.1.1 and 2.0.0 sources. Name the source file for each in the test.
  - A truncated header yields nothing.
- [ ] 4.2 After an applied update with any report, show one notification listing each path, a link for paths in the project, with the CLI's guidance; a replaced-edits report shows as a warning.
  - Verify with `UpdatePreviewServiceTest`: an applied run whose stdout is `edited/applied.txt` raises one notification carrying those paths, and an `unedited` one raises none.
- [ ] 4.3 Add the 2.1.0 Git and network sentence to the applied-update failure report (design decision 9).
  - Verify with `UpdatePreviewServiceTest`:
    - the captured `2.1.0/update-migration/no-git` run gets the sentence;
    - the same output with the detected version 2.1.1 or 2.0.0 doesn't;
    - a 2.1.0 failure without the Git text (`update-dry-run/read-only-failure`'s message as the applied output) doesn't.

## 5. The one-time migration notice

- [ ] 5.1 Add the startup notice (design decision 8). It checks off the UI thread after detection, and offers Update…, which calls `previewAndApply` with origin `ACTION`. Showing it records it for the project in `PropertiesComponent`.
  - Verify with a light test over the captured layouts:
    - `update-migration/{unedited,edited,nested-commands}/before.json` with CLI 2.0.0 and 2.1.1 shows it once;
    - a second check doesn't show it again;
    - every `after.json` doesn't show it, including 2.0.0 `edited`, which keeps edited 1.x files;
    - CLI 1.17.0, or no CLI, doesn't show it;
    - the 2.1.1 `skills-scope/machine-only` layout doesn't show it.
  - Ask jetbrains-platform-guru which startup hook to use (`ProjectActivity` versus detection's listener), and run `verifyPlugin`.

## 6. Non-interactive runs

- [ ] 6.1 Add `OPEN_SPEC_INTERACTIVE=0` to `CliRunner.applyPlainOutputEnvironment`, and close the process's stdin right after it starts.
  - Verify:
    - `CliRunnerTest` checks the variable on every command line `prepare` builds;
    - a run of a stub executable that reads stdin returns at once with end-of-input instead of timing out;
    - `CliCommandsTest` still pins `updateWithoutPrompts`' environment;
    - the full `./gradlew test` passes.
- [ ] 6.2 On CLI 2.1.0+, add to the cleanup dialog's terminal-handoff hint that the CLI may ask where to keep the project's skills, or whether to set skills up for the machine.
  - Verify with `LegacyCleanupDialogTest`: the hint has the sentence for 2.1.0 and 2.1.1, and not for 2.0.0 or 1.17.0.

## 7. Documentation and finish

- [ ] 7.1 Update the docs:
  - CHANGELOG `## Unreleased`: the migration preview, the at-risk warning, the kept-files report, and the notice;
  - `docs/feature-reference.md`: the update preview section;
  - the README and the marketplace getting-started page, where they describe Update.
  - Record internally (not in public files) the two upstream defects: 2.1 removes unconfirmed skill-folder files with no note, and the released-file set lacks narrow-profile renderings.
  - Verify: `DocumentationHygieneTest`, and the vendor-neutral grep over the staged files.
- [ ] 7.2 Final checks:
  - `./gradlew build`;
  - `./gradlew verifyPlugin`;
  - `openspec validate --all --strict`;
  - test-engineer AUDIT and intellij-code-reviewer on the diff, with each finding fixed or recorded.
  - Verify: all four pass, and the review outcome is recorded here.

## Workflow follow-up

- Merge by rebase, archive with spec sync, close the tracker entries.
- CLI 2.x as the installed target, and a migration uiSmoke journey on it, come with the CLI bump change.
