# Tasks

## 1. Release ranges

- [x] 1.1 Add `untilCli` to `AiTool` (`2.0.0` for Amazon Q), and make `acceptedBy` honour it; an unknown version still accepts every tool. Verify:
  - `acceptedBy(amazon-q, …)` is false for `"2.0.0"` and `"2.1.1"` and true for `"1.17.0"`, with the refusal read from the captured `errors/init-amazon-q.*` (2.0.0 and 2.1.1: exit 1, "no longer supported");
  - `sinceCliIsTheFirstCapturedVersionAcceptingThePassedId` reads every captured dump, not only those up to the target: `sinceCli` is the first release accepting the id, and `untilCli` the first refusing it;
  - `getAllToolsWithStatus` on 2.0.0 and 2.1.1 lists no Amazon Q, with or without the captured `amazon-q-from-1.17` layout (set up by 1.17.0, new in `scripts/capture-ai-tools-fixtures.py`) in the project, and lists it on 1.17.0 (`AiToolServiceBehaviourTest`).

## 2. The 2.1 tools

- [x] 2.1 Add the 38 tools after `agents`, in 2.1.1's order, with upstream's names, folders, detection paths and `sinceCli` `2.1.0`. Make the registry contract filter the list by `acceptedBy(v)` for each captured release, and compare ids, order and fields, reading the folders through `DetectionRules.forVersion(v)`. Delete the eight `tool-registry-2x` entries from `KnownGaps` and the name from `KnownGapsTest.CLOSERS`. Verify:
  - the order and field contract passes on the target and every later release (1.17.0, 2.0.0, 2.1.0, 2.1.1; before 1.17 names, folders and the `windsurf` id changed in ways `DetectionRules` doesn't model), the release-range test reads every dump from 1.3.0, and `KnownGapsTest` passes, exercising its mechanism on a sample gap now that none is open;
  - `DetectionRules.knows` follows the release ranges (2.0 lacks the 2.1 tools); a tool CLI 2.1 added can't be added without the CLI (`BuiltInAddContractTest`), and 1.x shared roots ignore the 2.1 tools, so Trae and Qoder keep their built-in add;
  - mutations: moving one new tool before `agents` turns the 2.1.x order red; dropping MiniMax Code's 2.1 shape from `DetectionRules` turns the 2.1.x fields red.
- [x] 2.2 Classify each new tool `CLI` or `IDE_PANEL` from its vendor's documentation, recording tool, type and source URL in `src/test/resources/tool-types-2.1.tsv` (a committed file, so it survives the archive); leave a tool `UNCLASSIFIED` when the docs don't settle it (12 CLI, 14 IDE_PANEL, 12 unclassified). Add `ToolType.UNCLASSIFIED` with `ToolType.badge()`: no badge in Manage AI Tools or the workflow panel's tool label, not a CLI tool for save-path hints, default guidance. Verify: `ToolTypes21Test` checks one row per 2.1 tool and no other, each registry type equal to its row's, a vendor URL for every classified tool; badge and guidance tests for an unclassified tool.
- [x] 2.3 Add the 29 new link folders to `SkillLinkDirs.V2_1`, and remove the "tools only 2.1 knows" skips from `SkillLinkDirsContractTest` and `WorkflowInvocationContractTest` (both places). Verify:
  - both contracts check every 2.1.1 tool: an unknown tool now fails instead of being skipped, the invocation contract checks every captured row, and the link-folder contract compares upstream's and the plugin's tool sets both ways;
  - the invocation contract passes for the 38 new tools (all `/openspec-<skill>` in the capture);
  - the machine-marker contract from the skills-scope change finds every marker tool in the registry, and now fails on any it can't.
- [x] 2.4 Detection of the new tools and the regional-variant rule. `scripts/capture-ai-tools-fixtures.py` now writes `configured.json` (upstream's `getConfiguredTools`) beside each `detected.json`, adds `agentsByProjectFolder` to the 2.1 dumps, and captures on 2.1.0 and 2.1.1: Goose (link folder), fx (`.agents/skills`), OpenClaw (`.`), AstrBot (`data`), MiniMax Code's project `.minimax`, Trae, Trae CN, Qoder and Qoder CN each alone, and an unlisted `.trae` and `.qoder` set up by 1.17.0 (no tool list). The rule (design decision 8) is in `AiToolDetectionService.statusOfDetected`, with groups from `AiToolRegistry.folderGroup`. Verify:
  - `AiToolDetectionContractTest` replays each new layout and the plugin's detection equals each release's;
  - `ConfiguredToolsContractTest` holds the plugin's set-up tools to `configured.json` for every 2.1.x case; three older divergences are pinned as known (a Codex recorded only by the list, and 1.6-era Kimi and Windsurf folders, which 2.1 no longer counts), and fail if they start matching;
  - `theFolderGroupsAreUpstreams` equals the captured groups; dropping the rule turns all 12 regional cases red.

## 3. Wording, notes and the shared-folder line

- [x] 3.1 Make the subtitle and `toolSetUpNotice` say "skills" on CLI 2.x and "/opsx commands" on 1.x; with no CLI, the project's layout decides (the subtitle is reworded when the background scan reads it). Verify: `ManageAiToolsSetUpNoticeTest` covers both wordings of each, and `cli2x` for 1.17.0, 2.0.0, 2.1.1 and no CLI with an empty project and a multi-tool list.
- [x] 3.2 Add `AiToolRegistry.setupNote(tool, version)` and `setupNoteAfter(tool, version, args)`, appended to the set-up notice ("Setup required for <tool>: <note>", the CLI's wording), except for a tool outside the universal folder after a machine-scoped add, as upstream prints it. Verify:
  - `SetupNotesContractTest`: `setupNote(tool, v)` equals each tool's `setupNote` in every captured dump (Hermes from 1.7.0, its 2.x text from 2.0.0; the nine universal-folder tools on 2.1.x; nothing for any other); the universal set equals the installer's (the skills-scope capture's `skipped`); and the note passed on equals what `init` printed, captured in `setup-notes.json` for Hermes and GSD with each scope (and Hermes on 1.17.0); dropping the machine-scope exception turns the Hermes default case red;
  - notice tests: Hermes on 1.17.0 and 2.1.1, GSD on 2.1.1 and 2.0.0, Hermes after a machine-scoped add (no note), and Cursor (none).
- [x] 3.3 Move `loadTools()`'s scan to a pooled thread (`scanThenPost`), filling the list with `invokeLater(dialogModality, expired)`, "Loading…" until then, with a generation counter so a late older scan can't replace a newer one. Add `ToolInfo.viaShared` and the "via shared .agents skills" line. Verify:
  - `ViaSharedFolderTest` on the captured 2.1.1 `scope-project` layout: the line for Cursor (skills only), not for Claude (linked, also when the links are checked out as text) or Codex (its own folder is `.agents`), and none on 1.x;
  - `ManageAiToolsDialogModalityTest`: the scan reaches the open modal dialog on the EDT, and a non-modal post is held (negative control); `ManageAiToolsRefreshHygieneTest` passes with the new hop. The generation counter is not exercised by a test, because no test constructs the dialog;
  - the intellij-code-reviewer pass finds no EDT disk reads in the dialog: its findings are fixed (the Add tooltips read a decision the scan made off the EDT, and a failing scan posts an empty list instead of leaving "Loading…", tested in `ManageAiToolsDialogModalityTest`). The workflow panel's tool selector still detects on the EDT, which predates this change.

## 4. Documentation and checks

- [ ] 4.1 Update `docs/feature-reference.md` (the tool list and its count, Amazon Q on 2.x, the shared-folder line, the 2.x wording), `README.md`, `docs/marketplace/getting-started.html` and `docs/feature-comparison-matrix.md` (the tool count), and `CHANGELOG.md` (Unreleased). Verify: `DocumentationHygieneTest`, `./gradlew build`, `openspec validate --all --strict`, the new plain test classes run alone, and `./gradlew verifyPlugin` (new `com.intellij` uses).

## Workflow follow-up

- Run `caffeinate -dimsu ./gradlew uiSmoke` locally (Manage AI Tools changed).
- Archive the change with the spec sync, and close the tracker entry.
