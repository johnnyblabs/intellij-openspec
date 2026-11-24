# Proposal

## Why

From CLI 2.1.0, `openspec init --tools <ids>` installs the skills for the whole machine (`~/.agents/skills`) by default, and writes no skills into the project. The plugin runs exactly that command from the setup wizard and from Manage AI Tools. On a user's first 2.1 setup, the project gets `openspec/` but no skills of its own, so teammates who clone the repository get none. Users don't see it happen, because the plugin never mentions where the skills went. This is broken for users on CLI 2.1 and later today. It is tracked in the linked tracker entry for the init skills scope.

## What Changes

- **New projects keep their skills in the project.** The wizard's init passes `--scope project` on CLI 2.1.0 and later, through the CLI argument catalog, which leaves the flag out below 2.1.0 (2.0 rejects it: `error: unknown option '--scope'`). The run also sets `OPENSPEC_SKILLS_SCOPE=project`, which 2.1 honours and older releases ignore, so an undetected version still gets a project install. There is no new wizard question.
- **Adding a tool keeps the project's current scope.** Manage AI Tools' Add and Configure pass the same project scope on a project that keeps its own skills. On a project whose skills are installed only for the machine, they pass no scope, so the CLI keeps the skills where they are.
- **The scope is shown, read-only.** Manage AI Tools and Settings show one line saying where the project's skills are: in this project (`.agents/skills`), on this machine (`~/.agents/skills`), or not installed. The line names the team's recorded choice when `openspec/config.yaml` has `skillsScope`.
- **Install into this project.** On a project whose skills are only on the machine, the scope line offers "Install into this project" on CLI 2.1+. It opens a tool list ticked the way the CLI's own picker ticks it for such a project: the tools found in the project and the tools installed on the machine (such as `~/.claude` or `~/.cursor`), plus the preferred AI tool. Confirming runs `init --tools <ticked> --scope project`. This mirrors the CLI, whose only way to move machine skills into a project is that picker. A machine-wide install records no tool list, so the plugin can't run it without asking. Below 2.1 (a project set up by 2.1, then opened with an older CLI), the line says the skills are installed for this machine and offers no action, and the plugin doesn't ask to configure tools.
- **`skillsScope` is read, never written.** The plugin reads it from `openspec/config.yaml` (`project` or `global`). A missing key means no recorded choice. Any other value is flagged in the `config.yaml` editor with the warning 2.1 prints, and the rest of the config loads as usual, as in 2.1. Only the CLI writes it (`openspec update` records it when the team keeps project skills).
- **A failed CLI init is no longer silent.** Today the wizard quietly falls back to the built-in init. It still falls back, and now also shows the CLI's error. On 2.1.0 the message adds that init needs Git and network access to fetch the skills (2.1.1 doesn't). (2.1 refuses a machine-wide init in the home folder, but the project scope the plugin passes is accepted there, so that refusal doesn't arise.)

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `plugin-core`: project initialization passes the project skills scope on CLI 2.1+ and reports a failed CLI init; new requirements for reading `skillsScope` and showing the skills scope.

## Impact

- **Production code:** `CliCommands.init` (scope as an optional flag, plus the environment value), `ScaffoldingService` (scope, failure notice), `ManageAiToolsDialog` (scope per project, scope line and action), a new install tool-list dialog with a captured table of the CLI's machine tool markers, `OpenSpecConfig` (`skillsScope`), `ConfigValidationInspection` (the invalid-value warning), the Settings panel (scope line).
- **Tests and captures:** new captures from the real 2.0.0, 2.1.0 and 2.1.1 CLIs for `skillsScope` in `config.yaml` (written by upstream's own function) and its invalid-value warning, the environment backstop, init on a machine-scoped project with and without `--scope` (and without `--tools`), init in the home folder with and without `--scope project`, and the table of paths the CLI checks to detect tools installed on the machine. Contract tests replay them; version-matrix rows cover init on 1.17.0, 2.0.0, 2.1.0 and 2.1.1. A uiSmoke journey opens the install tool list from a machine-scoped project and cancels it.
- **Corrections to the tracker entry, from the 2.0.0 and 2.1.x packages:** 2.0.0 has no `skillsScope` at all, and 2.1 treats a missing key as "no choice recorded", not as project scope. 2.1 refuses only a machine-wide init in the home folder; `--scope project` succeeds there.
- **Compatibility:** IntelliJ 2024.2+ unaffected; no new platform APIs. CLI 1.3.0 to 2.0.0 run exactly the arguments they run today.
- **Out of scope:** switching a project from project to machine scope (the CLI's `update` prompt owns that), and writing `skillsScope`.
