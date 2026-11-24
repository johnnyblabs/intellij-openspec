# Design

## Context

- **Registries by release** (captured `ai-tools.json`):
  - 1.17.0 has 50 tools.
  - 2.0.0 has 49: 1.17's list minus `amazon-q`, same order.
  - 2.1.0 and 2.1.1 have 87, identical to each other. They keep 2.0's order and append 38 tools after `agents`, from the `skills` package, pinned at exactly 1.8.1 in both releases.
  - The only alias is `windsurf` → `devin`.
- **Field changes:**
  - MiniMax Code is the only existing tool whose location changed: `globalSkillsDir: .minimax` up to 2.0, `skillsDir: .minimax` from 2.1.
  - Hermes has carried a `setupNote` since 1.7.0; its text changed at 2.0.
  - 2.1 adds a note to the nine existing tools whose installer agent is `universal` (AtomCode, DeepSeek Harness, CoStrict, EasyCode, GigaCode, GSD, Oh My Pi, SourceCraft, Veai). None of the 38 new tools has one, nor do Amp, Codex, Zed or Cursor, which also read `.agents/skills`.
  - The CLI prints a note at the end of `init` and `update` ("Setup required for <tool>: <note>"), but not for a tool outside the universal folder when the skills went to the machine.
- **Amazon Q on 2.x** (2.0.0 and 2.1.1 dists, checked by openspec-guru):
  - `amazon-q` is in `RETIRED_TOOLS`, not `AI_TOOLS`, so detection never reports it.
  - `init --tools amazon-q` exits 1 with `Amazon Q Developer is no longer supported: it can't load skills, and AWS is retiring it in favor of Kiro. Set up Kiro instead: openspec init --tools kiro` (captured).
  - `init` and `update` run `cleanUpRetiredTools`, which deletes generated `.amazonq/` files, keeps edited ones, and prints the Kiro advice unless Kiro is set up. Once only edited files remain, it stops reporting them.
- **Invocation on 2.x:**
  - There are no command files. Every workflow is a skill: `/openspec-<skill>`; Codex `$openspec-<skill>`; Kimi `/skill:openspec-<skill>`; GSD, Rovo Dev and SourceCraft get plain words.
  - `WorkflowInvocation.of` already decides from the files on disk and matches these forms, so a leftover 1.x command file still gets its working `/opsx` form.
  - `ToolGuidance.promptPrefix` is read by no production code.
- **Picker order:** upstream shows configured tools, then detected, then the rest, with a stable sort, so each group keeps `AI_TOOLS` order, which is not alphabetical. `getAllToolsWithStatus` already does exactly that.
- **Link folders:** `SkillLinkDirs` records 2.0's and 2.1's link folders for the tools it knows. Its javadoc leaves the 2.1-only tools to this change. 29 of the 38 new tools get one.
- **`KnownGaps`:** eight `tool-registry-2x` entries (`tools-in-order`, `tool-fields` and `passed-ids-accepted` on 2.x) mark where the plugin doesn't yet match, and `KnownGapsTest.CLOSERS` names the change.
- **Skipped tools in other contracts:** `WorkflowInvocationContractTest` (two places) and `SkillLinkDirsContractTest` skip tools the registry doesn't know, waiting for this change. The 76 captured 2.1.1 invocation rows for the new tools all use the plain `/openspec-<skill>` form.
- **Per-release folders already exist:** `DetectionRules.skillsDir(tool)` and `globalSkillsDir(tool)` give MiniMax Code its 2.1 shape on the 2.1 lines, and `Detector2x` reads through them; `DetectionRules.knows(tool)` already drops Amazon Q on 2.x.
- **Regional variants (2.1.0 and later, not 2.0):** upstream groups tools by their installer's project folder (`agentsByProjectFolder`). When a shared folder's tools aren't in the tool list, only the first counts as configured (`shared/tool-detection.js:302-305`): Trae and Trae CN, Qoder and Qoder CN, Zencoder and Zenflow.
- **Unusual folders among the new tools:** OpenClaw's `skillsDir` is `.` (detection path `skills`), AstrBot's is `data`, Eve's is `agent`, and two are nested (`.posit/assistant`, `.tabnine/agent`).

## Goals / Non-Goals

**Goals:**
- One list whose accepted tools equal each captured release's registry, in order, from 1.3.0 to 2.1.1.
- No tool offered to a CLI that refuses it.
- The new tools work everywhere the old ones do: detection, lookup, Add, the install list's ticks.
- Wording true to the CLI line.

**Non-Goals:**
- Removing 1.x command files (the update-migration change).
- Cleaning up Amazon Q files; the CLI's `update` does that.
- A new ordering.

## Decisions

1. **One list, each tool with a release range.** `AiTool` gains `@Nullable String untilCli`, the first release that no longer accepts the tool (`2.0.0` for Amazon Q, null otherwise).
   - `acceptedBy(tool, v)` becomes `v` at or past `sinceCli` and, with `untilCli` set, before it. An unknown version still accepts everything, as today.
   - The 38 tools go after `agents`, in upstream's order, with `sinceCli` `2.1.0`.
   - The contract then filters the list by `acceptedBy(v)` for each captured release and compares ids, order and fields with that release's dump, which removes all eight `KnownGaps` entries.
2. **Per-release folders stay in `DetectionRules`.** No new accessor: MiniMax Code's 2.1 shape is already there, and lookup finds its project skills through the `.minimax/skills` link folder `SkillLinkDirs.V2_1` records. The registry contract compares each dump with `DetectionRules.forVersion(v).skillsDir(tool)` and `globalSkillsDir(tool)`, so the existing per-line rule is now contract-tested.
3. **Link folders for the new tools.** `SkillLinkDirs.V2_1` gains the 29 folders from 2.1.1's `effective` block, and its existing contract test over the `effective` blocks covers them.
4. **A type for every new tool, from its documentation.** `ToolType` gains `UNCLASSIFIED`.
   - Each new tool is marked `CLI` (runs in a terminal) or `IDE_PANEL` (a panel or app) from its vendor's own documentation, with the source recorded in the table below. A tool its docs don't settle stays `UNCLASSIFIED`.
   - `UNCLASSIFIED` shows no badge (Manage AI Tools and the workflow panel's tool label), isn't a CLI tool for save-path hints, and gets the default guidance. Upstream records no type; this is the plugin's own field, as today.
   - The evidence lives in `src/test/resources/tool-types-2.1.tsv` (tool id, type, the vendor's page), a committed file the type test reads, so it survives the archive. Of the 38 tools, 12 are `CLI`, 14 `IDE_PANEL` and 12 `UNCLASSIFIED` (terminal and desktop both primary, a framework or API platform, or no product found).
   - Tools without a guidance entry, as all 38 are, get the default guidance, as today's unlisted tools do.

5. **Amazon Q: not offered, no row of the plugin's own.** `acceptedBy` refuses it from 2.0, and 2.x detection already never reports it. The plugin adds no retired row: upstream reports a retired tool only while `update` has something to clean, and stops once only edited files remain, so a permanent row would disagree with the CLI. The cleanup and the Kiro advice come from `openspec update`, which **Update OpenSpec…** runs.
6. **Wording by CLI line.**
   - `SUBTITLE` and `toolSetUpNotice` take whether the CLI is 2.x. That is the detected version at 2.0.0 or later; with no CLI, `DetectionRules.fromDisk(project).is2x()`. On 2.x they say "skills", on 1.x "/opsx commands".
   - Setup notes: `AiToolRegistry.setupNote(tool, version)` returns that release's note: Hermes's 1.x text from 1.7.0, its 2.x text from 2.0.0, and from 2.1.0 the nine universal-folder tools' note.
   - The notice appends it, except, as upstream prints it, for a tool outside the universal folder after a run that kept machine scope (`initKeepingScope` on a machine-scoped project). A contract test compares `setupNote(tool, v)` with every captured dump.
   - `promptPrefix` stays as it is, since nothing reads it.
7. **"via .agents", and loading the list off the EDT.**
   - **The line:** a configured tool on a 2.x project shows "via shared .agents skills" (upstream's label for that folder) when its located skill is the shared `.agents/skills` copy and the tool's own skills folder isn't `.agents`.
   - **Where it's decided:** `statusOfDetected` already locates the skill, so `ToolInfo` gains a `viaShared` flag.
   - **Off the EDT:** this adds work to the tool scan, which today runs on the EDT from `loadTools()` (the earlier platform review noted it). So `loadTools()` moves `detect()` and `getAllToolsWithStatus()` to a pooled thread, then fills the list with `invokeLater(dialogModality, expired)`. The list shows "Loading…" until then.

8. **Regional variants follow upstream's rule.** On the 2.1 lines, `AiToolDetectionService`'s configured check applies upstream's grouping: a tool sharing its installer folder with others, none of them listed, counts only if it is the group's first in registry order. The groups come from the registry (tools whose `skillsDir` is the same non-`.agents` folder, 2.1 only), which a test checks against the three pairs upstream forms.

## Risks / Trade-offs

- **Classifications from vendor docs can go out of date.** Each row cites its source, so a later change can re-check it, and a wrong badge is cosmetic: delivery is the same paste, and only the wording differs.
- **38 more rows in Manage AI Tools.** The existing filter field stays the way to find one; the order is upstream's.
- **Moving `loadTools()` off the EDT** changes when the list fills. Tests that read the list right after construction must wait for it, as the scope line's tests do.
- **Unusual folders.** OpenClaw's `.` and `skills`, AstrBot's `data` and Eve's `agent` are generic names, so a project can hold them for other reasons. Detection follows upstream's rule exactly, false positives included, and the detection capture samples OpenClaw and AstrBot so the path joins are tested.
- **A newer CLI adds tools.** The contract test fails on the new release's dump, as it should; the fix is this same procedure.
