# Design

## Context

- Upstream 2.1.0 and 2.1.1 decide an init's scope in the same order (`InitCommand.resolveSkillScope`, identical in both packages):
  1. `--tools none` → project;
  2. `--scope`;
  3. the project already holds skills of its own → project;
  4. `OPENSPEC_SKILLS_SCOPE` (an invalid value is an error);
  5. the machine config's `skillsScope`;
  6. a requested tool that can't use machine-wide skills → project;
  7. otherwise machine-wide (global).
- Step 5's machine setting is the user's own: `openspec config set skillsScope project` writes it, and no command writes it otherwise. Upstream's "own skills" test (step 3) is "a configured tool has OpenSpec skills or commands in the project". In 2.1.x, only `eve` and `promptscript` can't use machine-wide skills (step 6); Cursor is a universal agent whose machine skills are `~/.agents/skills`.
- `skillsScope` in `openspec/config.yaml` takes `project` or `global`. Only `project` is acted on (`keepsProjectSkills`); `global` is accepted and ignored. For any other value, 2.1 warns `Invalid 'skillsScope' field in config (must be "project" or "global")` and ignores it. Only `update` writes the key (`rememberProjectSkills`), and only when the team chooses to keep project skills; `forgetProjectSkills` removes it. `init` never writes it.
- Every command that reads the project config prints that warning (checked with `new change` and `instructions` on 2.1.1), and the config's other fields load.
- 2.1 refuses `init` in the home folder only on the machine-wide scope (`This is your home folder, where the global skills live. Run openspec init inside a project.`, exit 1). `init --scope project` succeeds there.
- A machine-wide install records no tool list. The home folder gets `.agents/skills/`, `.agents/.skill-lock.json` (skills, not tools), and a link folder only for tools that need one (`~/.claude/skills`; Cursor reads `~/.agents/skills` itself). Upstream's `getConfiguredTools` returns nothing for such a project, and a non-interactive `init` without `--tools` exits 1 with `Use --tools all, --tools none, or --tools claude,cursor,...`.
- Checked on the real CLIs, with a throwaway home folder:

  | Case | Result |
  |---|---|
  | 2.1.1 `init --tools cursor` | skills in home only |
  | the same with `--scope project`, or with `OPENSPEC_SKILLS_SCOPE=project` | skills in the project only |
  | 2.1.0 with the environment value | skills in the project |
  | 2.0.0 with the environment value | same layout as without it |
  | machine-scoped project + `init --tools claude` | stays on the machine |
  | the same with `--scope project` | skills added to the project; the home copy stays |
  | project-scoped project + `init --tools claude` with no scope | stays in the project |

- 2.0.0 has no `skillsScope` and no `--scope`. Its `init` rejects the flag (captured: `error: unknown option '--scope'`, exit 1). 1.x has neither.
- The plugin already has the pieces:
  - `CliArgs.optional` leaves out a flag the detected release lacks, and `CliFlags` records `init --scope` as available from 2.1.0;
  - `CliArgs` carries environment values;
  - `SkillLocator` and `GlobalSkillsCache` (skills-layout-2x) find project and home skills, with the home read kept off the UI thread.
- `ScaffoldingService.initOpenSpec` logs a failed CLI init and quietly runs the built-in init.

## Goals / Non-Goals

**Goals:**
- A project set up from the plugin on 2.1+ holds its own skills, as on every earlier release.
- A project using the machine's skills keeps them there when the user adds a tool.
- The user can see where the skills are, and can move machine-scoped skills into the project.
- Byte-identical arguments for 1.3.0 to 2.0.x.

**Non-Goals:**
- Moving project skills to machine scope. The CLI's `update` prompt owns this.
- Writing `skillsScope`.
- A wizard question about scope.

## Decisions

1. **The scope is part of the init command, built in one place.** Two builders replace `CliCommands.init(tools)`, so every caller names its scope:
   - `initInProject(tools)` (the PROJECT scope below) adds `.optional("--scope", "project")` and the environment value `OPENSPEC_SKILLS_SCOPE=project`;
   - `initKeepingScope(tools)` (KEEP) adds neither.

   They are two builders, not one taking an enum, because catalog builders take only `String`, `boolean` or `List<String>` parameters, which the version matrix test samples. There is no other path to `init`, so no caller can hand-assemble the flag (`CliFlagLiteralHygieneTest` already fails a stray `--scope` literal). The environment value only matters when the flag is dropped: an undetected version, where `CliArgs` resolves as the target release (1.17.0) and leaves the flag out, but a 2.1 binary still honours the variable. 2.0 and 1.x never read it.

2. **Which scope each caller uses.**
   - The wizard always uses `PROJECT`: a project being set up has no skills yet, and project scope is what every earlier release did.
   - Manage AI Tools' Add and Configure use `PROJECT` unless the project is machine-scoped, which then gets `KEEP`. A machine-scoped project has `openspec/` and no OpenSpec skills of its own, while the home `.agents/skills/` holds some.
   - With `KEEP`, upstream's own order (step 5 onward) keeps those projects on the machine scope. Passing `PROJECT` there would silently turn a team's machine-wide setup into committed project files.

3. **"Holds skills of its own" is decided by the plugin's existing lookup.** It means any OpenSpec skill under the project's `.agents/skills/` or a tool's skills folder (`SkillLocator`'s project spots). That is the same evidence workflow resolution uses, so the scope line and workflow lookup never disagree.

4. **`skillsScope` is a read-only field on `OpenSpecConfig`.** `fromMap` reads `project` or `global`; anything else leaves the field empty.
   - The plugin warns about any other value in `ConfigValidationInspection`, which already checks `config.yaml` in the editor, as a WARNING with 2.1's exact text.
   - It is not a `ConfigService` load problem: those kinds (UNREADABLE, UNPARSEABLE) mean the config failed and defaults are used, while 2.1 keeps the other fields.
   - Nothing in the plugin serializes the key. The only config writer, the built-in init, writes `schema:` alone.

5. **The scope line.** It is one `JBLabel` in Manage AI Tools (under the CLI hint) and one in the Settings panel. Both are filled from a background read of the project and `GlobalSkillsCache` (never on the EDT), and both show "Checking…" until the read finishes.
   - **Where it appears:** only when the CLI is 2.1.0 or later, when `DetectionRules.fromDisk(project)` shows a 2.x layout (it reads the project only), or when the home folder's `.agents/skills/` holds OpenSpec skills. On a pure 1.x project, scope isn't a concept, so the line is hidden.
   - **The action:** "Install into this project" is a link on the line, shown only in Manage AI Tools, only for a machine-scoped project, and only on 2.1.0+. It opens a tool list; confirming runs `init --tools <ticked ids> --scope project` through the existing `runCliAction`. Cancelling runs nothing.
   - **The tool list mirrors the CLI's own picker.** Upstream has no other way to move machine skills into a project: `update` only offers project → machine, and an interactive `init --scope project` on a machine-scoped project shows its tool picker. That project has no configured tools (a machine install records none, see Context), so upstream ticks every detected tool (`shouldPreselectDetected`). Detected means a tool found in the project (`getAvailableTools`), or a tool its installer reports as installed on the machine (`detectAgents`, mostly a home folder such as `~/.claude` or `~/.cursor`). The only tool skipped is the "Other / Universal" entry, which maps to the installer's `universal` agent.
   - **The plugin's ticks:**
     - the tools `AiToolDetector` finds in the project;
     - the tools whose machine marker exists, from a table captured from the installer (task 1.1);
     - the preferred AI tool from Settings, which upstream doesn't tick. It is added because the user chose it in the plugin.
     - A tool whose installer check isn't a fixed home path (an environment variable, say) is listed but not ticked, and the captured table records why.
   - **The list is a small `DialogWrapper` with one checkbox per tool,** in registry order with the ticked tools first, as upstream sorts them. OK is disabled with nothing ticked, matching upstream's "Select at least one tool". The ticks are computed off the EDT before the dialog opens. Since there is always a list to pick from, the link is never hidden for lack of a known tool.
   - **Why a link and not a balloon:** since skills-layout-2x, workflows resolve from home skills, so a machine-scoped project has no broken empty state left to hang a notice on. A balloon on every project open would nag teams that chose machine scope on purpose.

6. **A failed CLI init warns, then falls back as today.**
   - `initOpenSpec` adds an `OpenSpecNotifier.warn` with the CLI's stderr plus the `Failed:` lines of its stdout summary (2.1 puts the reason there and only the tool name on stderr, as the 2.1.0 no-Git capture shows), or its stdout when stderr is empty.
   - 2.1.0 creates `openspec/` before it fetches the skills, so a failed init can leave the CLI's tree behind. Today's fallback then fails, because the built-in init can't create `openspec/` again. The plugin keeps that tree as the CLI wrote it; the built-in init runs only when there is none.
   - 2.1.0 already says when Git is missing (`Git is needed to install the OpenSpec skills, and it is not on your PATH…`), but not when the clone fails for lack of network. So when the detected version is exactly 2.1.0, the warning appends one sentence covering both: "OpenSpec CLI 2.1.0 downloads its skills with Git during init; check that Git is installed and the network is reachable, or use CLI 2.1.1 or later."
   - The home-folder refusal can't arise: the wizard always passes the project scope, which 2.1 accepts in the home folder. If the IDE project is the home folder, its `.agents/skills/` is the machine's, so the verdict is "in this project" (`PROJECT`), and Add never passes `KEEP` there. The warning still shows any CLI error unchanged.

## Risks / Trade-offs

- **The CLI might treat an existing project as machine-scoped while the plugin doesn't.** The plugin's own-skills test (any project skill) is broader than upstream's step 3, which looks at configured tools. Where they differ, the plugin chooses `PROJECT`, which only ever adds project files, never removes them. The `init` captures on a machine-scoped project pin both outcomes.
- **2.1.0's network requirement can't be captured offline.** The hint keys off the detected version, not off error text. The fixture for the message is the plugin's own string, so the test checks only that the hint appears for 2.1.0 and not for 2.1.1.
- **A commands-only project is "own skills" upstream but not to the plugin.** The plugin's verdict looks at skills, so a project with only command files and machine skills reads as `MACHINE`. Add then passes `KEEP`, and upstream's step 3 still installs into the project, so nothing is lost. The line may say "on this machine" for such a project, which is true of its skills.
- **The plugin's ticks can drift from the installer's detection.** The marker table is captured from the CLI's own installer and checked by running its `detectAgents` against each marker, so a CLI bump that changes a marker turns the capture diff red. A tool the table misses is still in the list, just not ticked.
- **Settings adds a background read.** It reuses `GlobalSkillsCache`'s snapshot, so it's cheap after the first read.
