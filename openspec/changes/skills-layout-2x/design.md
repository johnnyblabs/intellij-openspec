# Design

## Context

The plugin has a faithful port of the 1.x `getAvailableTools` (`AiToolDetector`) and a lookup that assumes each tool's skills sit in `<skillsDir>/skills/` (`AiToolRegistry.skillPath`, used by `WorkflowInvocation.find` and `ExplorePromptService`). The earlier 2.x fixture change captured, for 2.0.0, 2.1.0 and 2.1.1:

- detection layouts, including links stored as a `links` manifest;
- the workflow-invocation matrix, including each row's `linkPath`;
- home-state.

It also recorded every mismatch as a `KnownGaps` entry. The compatibility-matrix change after it put every CLI call behind `CliCommands`/`CliFlags`.

Upstream's 2.x detection is spread over these modules (read from the published 2.0.0 and 2.1.1 packages):

- `available-tools.js`;
- `shared-skill-target.js` (list parsing, pre-list inference, `skills-lock.json` inference);
- `shared/tool-detection.js` (`isListedToolPresent`, `hasSkillLinks`);
- `shared/skill-paths.js` (shared vs own vs link folders);
- `shared/skill-links.js` (link-spot classification);
- `shared/generated-fingerprint.js` (released-content check).

## Goals / Non-Goals

**Goals:**

- Every captured detection case and invocation row passes for 1.14.3 through 2.1.1, and the 56 `skills-layout-2x` gaps are removed.
- Lookup finds skills in the shared folder, through links, in older folders and in the home directory.
- No disk access on the EDT; links never followed when classifying.

**Non-Goals:**

- The 2.x registry (87 tools, Amazon Q retired, per-tool link folders for tools new in 2.1). Tool records stay as they are; tools the plugin doesn't know are skipped, as unknown ids already are.
- Writing `.openspec-target`, 2.0 `keep` lines, `init --scope`, and the update migration banner.

## Decisions

### 1. Detection rules are chosen by release line; lookup is decided from disk

`DetectionRules` is an enum with `V1`, `V2_0` and `V2_1`. `AiToolDetector.detect(project, home, rules)` dispatches on it:

- `V1` is today's code, unchanged.
- `V2_0` and `V2_1` share one port, with two switches:
  - whether a tool with a global skills dir is still detected from home (2.0 yes, 2.1 no — in 2.1 MiniMax moved to a project skills dir);
  - whether `skills-lock.json` inference applies (2.1.1 only, so it gates on `CliVersion` ≥ 2.1.1 rather than on the enum).

`AiToolDetectionService` picks the rules from `CliDetectionService`. When no CLI is detected, it picks from disk: a tool list with more than one line or an `own-files` token, or a link from any registry tool folder into `.agents/skills`, means `V2_1`; anything else means `V1`.

*Alternative rejected:* one merged rule. The captures prove it can't exist. The same layout gets different verdicts from each line's CLI: `gsd`, `agents-unmarked-generic`, `agents-unmarked-codex-syntax`, and `minimax-global` (2.0 vs 2.1).

*Why lookup differs:* whether a skill file exists at a path doesn't depend on which CLI is installed. Lookup tries every location in a fixed order, so a 1.x layout still resolves to its 1.x copy under a 2.x CLI and the other way round. The previous decision for this work already settled this split: layout from disk, behaviour gated on version.

### 2. Link facts come from the captured invocation matrix

The 2.x port needs to know, per tool, whether OpenSpec links skills into the tool's own folder, and which folder. In upstream these are `SKILL_LINKED_TOOL_IDS` and `installerAgent(id).projectSkillsDir`. In 2.1 they derive from the bundled skills-installer agent table, which is a moving target.

The captures settle it, and it is **per line**. Each 2.x `ai-tools.json` carries an `effective` block, read from upstream's own `shared/skill-paths.js`: per tool, its skills folder and its link folder. 2.1.0 and 2.1.1 agree on every tool. 2.0.0 differs from 2.1 on 17 of the tools the plugin knows:
- Auggie, IBM Bob, Command Code, Devin, Crush, Grok, Junie, MiniMax, Vibe, Qoder, Qwen, Zoo Code, Trae and ZCode gain links in 2.1;
- EasyCode, GigaCode and Veai lose them.

So `AiTool` is left unchanged, which keeps the record free for the registry change. A separate `SkillLinkDirs` table maps each `DetectionRules` line (`V2_0`, `V2_1`) to its tool → link-folder map. Two contract tests check it:
- the table equals the `effective.linkDir` of every 2.x `ai-tools.json`, for every tool the plugin knows;
- the `linkPath` column of every 2.x `workflow-invocations.json` row equals `SkillLinkDirs.linkPath(line, tool, skill)`, cross-checking the table against what `init` actually wrote.

Tools that only 2.1 knows are skipped; the registry change adds them. Lookup (Decision 5) tries the link folder for both lines, because a project set up by 2.0 and opened under a 2.1 CLI keeps its 2.0 links.

### 3. The released-content check uses upstream's fingerprint list as data

On 2.x, `inferPreListTool` counts a skill's wording only when `sha256(normalize(content))[:16]` is in `RELEASED_GENERATED_FINGERPRINTS` (2,867 entries, the same set in 2.0.0 and 2.1.1).

- `scripts/capture-released-fingerprints.py` extracts the set from the package into `src/main/resources/openspec/released-skill-fingerprints.txt`. The file carries a header naming the CLI version it came from.
- `ReleasedSkillFingerprints` loads it lazily on a background thread, and ports `normalizeGeneratedContent`: strip the BOM, turn CRLF into LF, and replace a semver `generatedBy` in the front matter with `"<generated-version>"`.
- A contract test hashes three captured real skill files to known members and checks that a hand-edited copy is not a member. Another test checks that the resource equals the set in each captured 2.x package listing.

*Alternative rejected:* treat every skill as user-written on 2.x. That drops upstream's support for a pre-list generic `.agents` tree, which the `agents-universal`-style cases depend on.

### 4. `SkillToolList` parses the list file

`SkillToolList.read(project)` returns `List<Entry(id, ownFiles)>`:

- split lines on `\r?\n`, trim each, and split it on whitespace;
- token 0 is the id, mapped through upstream's alias table (`windsurf → devin`, matching `TOOL_ID_ALIASES`);
- `own-files` is token 1;
- unknown ids are dropped.

It is pure nio and needs no IDE APIs, so plain JUnit tests it. `OpenSpecTargetListContractTest` reads every captured `.openspec-target` in the 1.x and 2.x detect fixtures and checks the parse against the ids in the case's `detected.json`, where the case has no other evidence.

### 5. `SkillLocator` classifies with `NOFOLLOW_LINKS`

`classify(spot, sharedTarget)`:

- `Files.readAttributes(spot, BasicFileAttributes.class, NOFOLLOW_LINKS)`;
- `isSymbolicLink() || isOther()` → `LINK` when the link points to the shared skill folder, `USER` otherwise. Upstream never creates junctions for skills; `isOther()` is kept only because a user or another tool may have made one, and it costs one condition. **`BROKEN` is a plugin refinement of upstream's `link`.** It is a link that points to the shared skill folder whose target no longer exists. Upstream's `classifySkillLinkSpot` compares paths without checking the target exists, so it calls that `link` and the tool stays set up. The plugin keeps upstream's verdict for detection, and only needs the split so lookup knows there is no skill to read through it;
- a regular file of at most 1,024 bytes whose one-line content points to the shared skill folder → `RELEASED`;
- a directory holding exactly `SKILL.md`, ignoring OS junk exactly as upstream's `isOsJunk` does (its `OS_JUNK` names, ported from the package, plus any name starting with `._`) → `COPY`. **The plugin's `COPY` deliberately merges three upstream verdicts:** `copy` (content equals the shared copy), `released` (a released file's content) and a `user` folder holding only a `SKILL.md`. Detection (`hasOpenSpecSkillIn`) and lookup only ask whether `SKILL.md` exists, so the content check would change no outcome here. Telling them apart matters only for what `update` replaces, which is the update-migration change's job;
- absent, or an empty folder → `MISSING`.

**"Points to" is a port of upstream's `linkPointsTo`, so it holds on every host:**
- In the link text, `\` is replaced with `/` before resolving. A link committed on one OS and read on another resolves the same either way.
- The text is resolved against the link's folder and compared with the target twice: once literally, once with both sides through `toRealPath()` (falling back to the absolute path when it doesn't exist). This covers macOS's `/var` → `/private/var`, a project opened through a link, and Windows drive-letter case.
- A last check compares `toRealPath()` of the spot with `toRealPath()` of the target.

**What each host actually has (read from the 2.0.0 and 2.1.1 packages):**

| Host and scope | Tool-folder spot | Why |
|---|---|---|
| macOS / Linux, any scope | `LINK` (relative `dir` symlink) | `createSkillLink` / the installer in `auto` mode |
| Windows, project scope, 2.1.x | `COPY` | the installer is forced to `copy` on win32 project scope ("Git cannot carry a Windows junction into a teammate's checkout") |
| Windows, 2.0.0, or machine-wide 2.1.x | `LINK` with Developer Mode or admin rights, otherwise `COPY` | a `dir` symlink, with a copy fallback |
| Windows clone of a repo committed from macOS/Linux (`core.symlinks=false`) | `RELEASED` | Git checks the link out as a text file holding its target |
| Any host, `.agents` deleted or gitignored in this clone | `BROKEN` | the link's target is missing |

Lookup must resolve the skill in every row: through the link, the copy, or (for `RELEASED` and `BROKEN`) the shared folder when it exists.

`find(project, home, tool, skill)` returns the first existing `SKILL.md`, in this order:

1. the shared folder, only when `.openspec-target` lists the tool. Every 2.x tool's skills sit there, so the folder alone names no tool. Checking it for every tool that "reads" it would hand the skill to whichever tool comes first in registry order (Amp) instead of the one the project set up;
2. the tool's link folders, for both lines: `LINK` and `COPY` are read in place, and `RELEASED` leads to the shared copy;
3. the tool's own `<skillsDir>/skills/` (1.x copies). For a tool whose folder is `.agents`, this counts only when there is no list or the list names it, as upstream counts nothing under `.agents` as such a tool's evidence;
4. legacy folders;
5. home, through `GlobalSkillsCache`'s snapshot: the tool's link folders, then its 1.x home folder, then `~/.agents/skills` for an `.agents` tool or the tool preferred in Settings.

The callers read the list once per lookup, not once per tool. Project-side checks stay on the thread they ran on before (the existing callers do a handful of file checks on the EDT). Only the home directory goes through the cache, so it never reads the disk there.

`AiToolRegistry.skillPath` stays, documented as the path OpenSpec writes, for the registry contract test. `WorkflowInvocation.find` switches to `SkillLocator`.

### 6. `GlobalSkillsCache` is a project service

It holds an immutable snapshot of which home skill files exist, `Map<String, Set<String>>` from folder to skill names. It is filled by `ensureLoaded()` on a pooled thread.

- `snapshot()` never touches disk. On the EDT with nothing cached, it returns empty and schedules a load.
- `invalidate()` is called from `CliRunner`'s completion for `init`/`update`. It is also called when the project's window regains focus. For that, the service subscribes to `ApplicationActivationListener.TOPIC` on the **application** message bus, through a connection owned by the service, which is `Disposable`. This is the pattern `OpenSpecToolWindowFactory.subscribeActivation` already uses: the topic is application-level, and the cache is per project.
- The home path is injectable (`setHomeForTest`), the same seam `AiToolDetectionService` already uses, so tests never read the real `~`.
- **The home directory is resolved the way upstream resolves it:** the `USERPROFILE` environment variable, then `HOME`, then `System.getProperty("user.home")`. Blank values are skipped. This is upstream's `USERPROFILE ?? HOME ?? os.homedir()`. On Windows `USERPROFILE` and `user.home` usually agree, but they can differ under profile redirection or a `HOME` set by Git Bash or MSYS. Upstream writes where `USERPROFILE` points, so the plugin reads there too. `HomeDirectory.resolve(env, props)` takes both maps as arguments, so the order is unit-tested on every OS without touching the real environment. Detection's existing `home()` moves onto the same resolver, so detection and lookup never disagree on where home is.

### 7. Refresh and watcher

- After `init`/`update` complete, `ScaffoldingService` and `ManageAiToolsDialog` call `LocalFileSystem.getInstance().refreshNioFiles(paths, true, true, null)`. The paths are `.agents/skills` and each detected tool's skills folder, so linked folders enter the VFS.
- **The refresh is recursive from each `skills` folder, not from the individual skill spots,** because `update` can change a spot's type. On Windows, both 2.0.0 (copy fallback) and 2.1.1 (installer forced to `copy` for project scope, and a `win32` branch in the planner) replace a checked-out link text file with a copy folder. On every host, a copy can become a link or the other way round. Refreshing the parent lets the VFS drop the stale entry and pick up the new one. A platform-tier test replaces a `RELEASED` file with a `COPY` folder on disk, refreshes, and asserts that the VFS sees a directory holding `SKILL.md`.
- The artifact watcher that today matches top-level tool roots resolves `.agents` with `toRealPath()` once, on a background thread, so a project whose `.agents` is itself a link is still watched.
- `.agents` is not added to excluded folders.

### 8. Tests

- **`AiToolDetectionContractTest`** passes `DetectionRules.forVersion(v)` to the detector. Its gap entries are removed.
- **`WorkflowInvocationContractTest`** materializes `linkPath` before calling the locator, once per `FixtureLayout` mode: `SYMLINK` (where the probe allows), `COPY` and `LINK_TEXT`. `COPY` and `LINK_TEXT` run on every host. Each dynamic test's display name names its mode, so a symlink case that fell back to a copy can't pass unnoticed. Its gap entries are removed.
- **`SkillPathResolutionContractTest`** and the refresh tests (task 5.1) extend **`HeavyPlatformTestCase`**, following `integration/ArchiveCliVfsRefreshTest`. `BasePlatformTestCase` keeps the project in an in-memory `temp://` file system, which has no links and nothing for `refreshNioFiles` to see. A platform test also publishes `applicationActivated` on the application bus and asserts that the cache was cleared. The resolution test covers:
  - Cursor and Copilot, with skills only in `.agents`;
  - Claude through a link;
  - a 1.x copy;
  - a home-only install.
- **`OpenSpecTargetListContractTest`**, **`GlobalScopeLookupTest`** (injected home) and **`HomeDirectoryTest`**. `HomeDirectoryTest` covers the `USERPROFILE` → `HOME` → `user.home` order with blanks skipped. It also proves that `AiToolDetectionService` and `GlobalSkillsCache` take their home from `HomeDirectory`, through an injected environment map, so a bypass can't survive.
- **Captured positive path for the released-content gate.** The existing 2.x detect cases keep only the invocation lines of each `SKILL.md`, so no captured case can hash into the set, and an "always false" gate would pass. `capture-ai-tools-fixtures.py` gains two cases that keep the full `SKILL.md` text: `agents-unmarked-from-1.17` (1.17.0 `init --tools agents`, then remove `.openspec-target`) and `codex-unmarked-from-1.17` (the same with `codex`). Each is detected by every 2.x release, and the verdict is whatever upstream reports.
- **Captured classifier verdicts.** `scripts/capture-skill-spot-verdicts.py` builds each spot shape on disk and runs upstream's own `classifySkillLinkSpot` on it. The shapes are: a link, a link to a missing target, a link elsewhere, link text with `/` and with `\`, link text pointing elsewhere, a copy, a copy beside `.DS_Store` and `._x`, an edited copy, a released copy, an empty folder, and absent. It writes `<v>/skill-spots/<case>/{layout.json,verdict.txt}`. `SkillFolderShapeTest` asserts the plugin's shape maps onto upstream's verdict through one stated table: `copy`, `released` folder and lone-`SKILL.md` `user` map to `COPY`; `link` maps to `LINK` or `BROKEN`; link-text `released` maps to `RELEASED`. Verdicts aren't hand-decided.
- **`SkillFolderShapeTest`** covers every row of the host table in Decision 5, not just the shapes in the abstract. **Link support is probed by a shared `SymlinkProbe`:** it creates a directory link inside the test's own `@TempDir`. Skips use `assumeTrue` per test, in a `@Nested` links class, never in `@BeforeAll` or a constructor, which would also skip the unconditional cases. The probe result is reported through `TestReporter`. **On a non-Windows host a failed probe fails the test** rather than skipping, so a broken probe can't silently disable the link cases on macOS or Linux. The cases:
  - **On every host:** `COPY` (including a copy beside OS junk), `RELEASED` with a `/` target and with a `\` target, `MISSING` and an empty folder. A `RELEASED` file whose target doesn't point at the shared folder is `USER`. These need no link support, so they are never skipped.
  - **Where the JVM can create a symlink** (probed once with `Files.createSymbolicLink` in a temp dir): `LINK`, `BROKEN`, and a link reached through a linked project root (the real-path comparison). On Windows without Developer Mode the probe fails and these are skipped with `assumeTrue`, and the skip message names the reason. Only link creation is skipped, never a whole OS: a Windows runner with Developer Mode runs them.
  - **The real-path case on macOS:** a project under `/var/folders/...` is compared against a target spelled `/private/var/...`.
- **Host coverage gate.** The macOS, Linux and Windows CI matrix must run all of these green before release. Those legs run on the mirror after merge, so this gates the release, not the PR. A Windows run with every link case skipped is acceptable only because the `COPY` and `RELEASED` cases, which are what Windows users actually have, run there unconditionally.
- **Every pure-nio class is tested in plain JUnit:** `SkillToolList`, `SkillLocator`, `HomeDirectory`, `DetectionRules`, `ReleasedSkillFingerprints`, `SkillLinkDirs` and `GlobalScopeLookupTest`'s subject. Their import graph stays free of `com.intellij` and of icon-holding classes (the static `IconLoader` rule for plain tests).
- **Mutation checks.** Each is temporarily applied, shown red, then reverted:
  - following links in classification turns the broken-link and Claude cases red;
  - ignoring links turns the Cursor and Copilot cases red;
  - skipping the shared-folder step turns them red as well;
  - `V2_1` detecting the global dir the way `V2_0` does turns `2.1.x/minimax-global` red;
  - the `skills-lock.json` gate loosened to 2.1.0 or later turns `2.1.0/skills-lock-agents` red;
  - the fingerprint gate always true turns the 2.x `agents-unmarked-*` cases red, and always false turns the new `*-unmarked-from-1.17` cases red;
  - swapping `USERPROFILE` and `HOME`, or a caller bypassing `HomeDirectory`, turns `HomeDirectoryTest` red;
  - dropping the `\` → `/` normalization, the junk filter, or the real-path comparison turns the matching captured `SkillFolderShapeTest` case red.

## Risks / Trade-offs

- **[Fingerprint list ages]** → It is the same set in 2.0.0–2.1.1. The cli-bump change re-captures it, and the contract test fails on drift.
- **[No-CLI heuristic picks the wrong line]** → This affects only users with no CLI installed, where the plugin can't run `init`/`update` anyway. Lookup doesn't depend on it.
- **[Windows users mostly have copies, not links]** → Lookup reads a copy in the tool's own folder directly, so the 2.1.x Windows project layout works without any link handling. The risk on Windows is the `RELEASED` file from a cross-OS clone. It is covered unconditionally on every CI host, with both separator styles.
- **[Junctions]** → Upstream never makes them for skills. `isOther()` treats one as a link anyway, but no test creates one, since `mklink /J` would need a shell step for a shape upstream doesn't produce.
- **[2.1.1 `update` deletes an edited per-tool copy (upstream defect)]** → This hits Windows hardest, since every Windows tool folder is a copy. It is out of scope here; the update-migration change owns the warning that gates confirmation. This change only makes sure lookup finds the shared skill after the copy is gone.
- **[`USERPROFILE` vs `user.home`]** → Following upstream's order means the plugin reads exactly where the CLI wrote, even when the two values differ.
- **[Home read latency]** → The first EDT lookup may miss a home-only skill until the background load completes. The handoff then falls back to the expanded prompt, the existing no-skill behaviour, and the next lookup finds it.
