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
- [ ] 2.4 Detection of the new tools and the regional-variant rule. Capture with `scripts/capture-ai-tools-fixtures.py` on 2.1.1: `init --tools` layouts for a tool with a link folder, a tool reading `.agents/skills`, OpenClaw (`.`), AstrBot (`data`) and MiniMax Code's project `.minimax`, plus Trae alone, Trae CN alone, and an unlisted `.trae` holding skills, and the same for Qoder. Implement the rule (design decision 8). Verify: `AiToolDetectionContractTest` replays each layout and the plugin's verdict equals 2.1.1's; a test that the registry's groups are exactly upstream's three pairs; a mutation dropping the rule turns the unlisted-`.trae` case red.

## 3. Wording, notes and the shared-folder line

- [ ] 3.1 Make `SUBTITLE` and `toolSetUpNotice` say "skills" on CLI 2.x and "/opsx commands" on 1.x (no CLI: the project's layout decides). Verify: tests for 1.17.0, 2.0.0, 2.1.1 and no CLI with a 2.x and a 1.x layout; `ManageAiToolsSetUpNoticeTest` updated.
- [ ] 3.2 Add `AiToolRegistry.setupNote(tool, version)` and append it to the set-up notice, except for a tool outside the universal folder after a machine-scoped add, as upstream prints it. Verify:
  - a contract test that `setupNote(tool, v)` equals each tool's `setupNote` in every captured dump (Hermes from 1.7.0, its 2.x text from 2.0.0; the nine universal-folder tools on 2.1.x; nothing for any other tool);
  - notice tests: Hermes on 1.17.0 and on 2.1.1, GSD on 2.1.1, Hermes after a machine-scoped add (no note), and Cursor (none).
- [ ] 3.3 Move `loadTools()`'s scan to a pooled thread, filling the list with `invokeLater(dialogModality, expired)` ("Loading…" until then). Add `ToolInfo.viaShared` and the "via shared .agents skills" line. Verify:
  - a heavy test on the captured 2.1.1 `scope-project` layout shows the line for Cursor (skills only) and not for Claude (linked) or Codex (its own folder is `.agents`);
  - `ManageAiToolsRefreshHygieneTest` covers the new hop, and existing dialog tests wait for the list;
  - the intellij-code-reviewer pass finds no EDT disk reads in the dialog.

## 4. Documentation and checks

- [ ] 4.1 Update `docs/feature-reference.md` (the tool list and its count, Amazon Q on 2.x, the shared-folder line, the 2.x wording), `README.md`, `docs/marketplace/getting-started.html` and `docs/feature-comparison-matrix.md` (the tool count), and `CHANGELOG.md` (Unreleased). Verify: `DocumentationHygieneTest`, `./gradlew build`, `openspec validate --all --strict`, the new plain test classes run alone, and `./gradlew verifyPlugin` (new `com.intellij` uses).

## Workflow follow-up

- Run `caffeinate -dimsu ./gradlew uiSmoke` locally (Manage AI Tools changed).
- Archive the change with the spec sync, and close the tracker entry.
