# Tasks

## 1. Captures from the real CLI

- [x] 1.1 Add `scripts/capture-skills-scope.py` to record the following, with paths sanitized. Verify: the script runs clean twice with no diff, and the fixtures README lists each new file.
  - On 2.1.0 and 2.1.1, under `skills-scope/`:
    - a `config.yaml` written by upstream's own `rememberProjectSkills`;
    - the warning 2.1 prints for `skillsScope: team` (from `instructions`, which reads the config), with the config's other fields still applied;
    - `OPENSPEC_SKILLS_SCOPE=project init --tools claude` (project files present);
    - `init --tools claude` on a machine-scoped project, with and without `--scope project` (where the skills land, and the home link folders a machine install leaves);
    - `init --scope project` with no `--tools`, not interactive (the captured error);
    - init in the home folder: refused by default, accepted with `--scope project`;
    - on 2.1.0 only, `init --tools claude --scope project` with no Git on `PATH` (the CLI's own Git message, which the 2.1.0 warning sentence must not contradict);
    - the machine marker table: for each tool id, the path the installer's `detectAgents` checks under a throwaway home. Confirm each entry by creating only that path and checking that `detectAgents` then reports exactly that tool. Record a tool whose check isn't a fixed home path as not ticked, with the reason, and record that "Other / Universal" is skipped.
  - On 2.0.0: `OPENSPEC_SKILLS_SCOPE=project init --tools claude`, to show it is ignored and gives the same layout as without it.

## 2. Init arguments

- [x] 2.1 Replace `CliCommands.init(tools)` with `initInProject(tools)` (PROJECT: the optional `--scope project` and `OPENSPEC_SKILLS_SCOPE=project`) and `initKeepingScope(tools)` (KEEP: neither). Verify with tests:
  - `CliArgsVersionMatrixTest` rows for init on 1.17.0, 2.0.0, 2.1.0 and 2.1.1, plus an unknown version (flag dropped, environment kept);
  - `CliCommandsTest` covers `KEEP`, which passes neither;
  - a mutation check: making `--scope` required, or ungating it, turns the 2.0.0 row red (by the captured rejection).
- [x] 2.2 `ScaffoldingService` uses `initInProject`. A failed CLI init raises an `OpenSpecNotifier.warn` with the CLI's text (stderr plus the summary's `Failed:` lines), plus the 2.1.0 Git/network sentence when the detected version is 2.1.0. An `openspec/` the failing CLI already wrote is kept; otherwise the built-in init runs as before. Verify:
  - `ScaffoldingInitArgsTest` pins the argv, and the version matrix (task 2.1) checks it per release against captured help;
  - `FailedInitNoticeContractTest` builds the warning from the captured 2.1.0 no-Git and 2.1.1 home-folder errors;
  - `ScaffoldingFailedInitTest` drives a failing init on a real project: the CLI's tree is kept (a mutation disabling that turns it red), a failure that wrote nothing runs the built-in init, and a success raises nothing.

## 3. Reading `skillsScope`

- [x] 3.1 `OpenSpecConfig` reads `skillsScope` (`project` or `global`); any other value leaves it empty, and the config still loads. Verify: `SkillsScopeConfigContractTest` loads, through `ConfigService.load`, the captured `remembered-config.yaml` (project), a config without the key, and the captured `team` config (no recorded choice; schema and context still read).
- [x] 3.2 `ConfigValidationInspection` flags any other `skillsScope` value (an empty one included, as upstream does) as a WARNING with the captured 2.1 text, and nothing for `project`, `global` or a missing key. Nothing in the plugin writes the key. Verify:
  - `ConfigValidationInspectionTest` cases, with the message compared to the captured warning;
  - a source check in `SkillsScopeConfigContractTest` (with a positive control) that no production code writes `skillsScope`, and that the built-in init's config has only `schema:`.

## 4. Scope per project and the scope line

- [x] 4.1 Add `ProjectSkillsScope.of(project, home)` → `IN_PROJECT`, `ON_MACHINE` or `NONE` (the recorded choice comes from `OpenSpecConfig`), read off the EDT. It uses the same project skill spots as `SkillLocator` (shared `.agents/skills/` and tool skill folders) and `HomeSkills`; a project that is the home folder counts as `PROJECT`. Verify: `ProjectSkillsScopeContractTest` over the captured `detect/scope-project`, `detect/scope-default-global` and task 1.1's layouts asserts each verdict, including the home-folder case; ignoring the home folder turns 7 cases red.
- [x] 4.2 Manage AI Tools' Add and Configure use `initInProject`, or `initKeepingScope` for an `ON_MACHINE` project, decided on the background thread when clicked; Install always uses `initInProject`. Verify: `ManageAiToolsScopeArgsTest` (heavy) recreates the captured layouts on the project directory and asserts the argv for a project-scoped, a machine-scoped and an empty project on 2.1.1, and for 2.0.0; swapping the scopes turns 4 tests red.
- [ ] 4.3 Add the scope line to Manage AI Tools and the Settings panel. It is hidden unless the CLI is 2.1+, the project shows a 2.x layout, or the home `.agents/skills/` holds OpenSpec skills. The "Install into this project" link appears only for `MACHINE` on 2.1.0+. Below 2.1, the text says the skills are installed for this machine. Verify:
  - a test asserts the line text for each verdict, with and without a recorded choice;
  - it asserts the link appears only on 2.1.0+ `MACHINE`.
- [ ] 4.4 Add the install tool list: a `DialogWrapper` with one checkbox per tool, ticked from project detection, the captured machine marker table and the preferred tool, computed off the EDT. OK is disabled with nothing ticked, and runs `init --tools <ticked> --scope project`. Verify:
  - a plain test of the tick computation over task 1.1's throwaway homes: `~/.claude` ticks Claude, `~/.cursor` ticks Cursor, a project `.cursor/` ticks Cursor, the preferred tool is ticked, and "Other / Universal" is never ticked from the home;
  - a contract test that the plugin's marker table equals the captured one;
  - a test that the confirmed argv names the ticked ids in registry order with `--scope project`, that cancelling runs nothing, and that OK is disabled with no tick;
  - the intellij-code-reviewer pass finds no EDT reads.
- [ ] 4.5 Add a uiSmoke journey, "install tool list opens from a machine-scoped project". Seed a project with `openspec/` and no skills of its own, and start the sandbox IDE with `HOME` set to a throwaway folder holding `.agents/skills/` (OpenSpec skills) and `.claude/`, so the run never touches the real home. The journey opens Manage AI Tools, checks the scope line reads "Skills: on this machine (~/.agents/skills)" and the link is shown, clicks "Install into this project", checks that the list opens with Claude ticked and that OK is disabled once every tick is cleared, then cancels. It stops before `init` runs: the argv is covered by 2.1 and 4.4. Verify:
  - after cancelling, the project and the throwaway home are byte-identical to the seed;
  - the journey passes under `caffeinate -dimsu ./gradlew uiSmoke`, and fails when the link is removed or the dialog's ticks are left empty.

## 5. Documentation and checks

- [ ] 5.1 Update `CHANGELOG.md` (Unreleased), `docs/feature-reference.md` (scope line, install action, init scope) and the user-facing docs that describe init or Manage AI Tools. Verify: `DocumentationHygieneTest` passes, `./gradlew build` passes, `openspec validate --all --strict` passes, the new plain test classes pass run alone, and each mutation check in 2.1, 3.1 and 3.2 turns at least one test red.

## Workflow follow-up

- Archive the change with the spec sync, and close the tracker entry.
