# Design

## Context

The plugin keeps one record per tool (`AiToolRegistry`), shaped as 1.17 and 2.x have it, with each tool's first and last accepting release. `DetectionRules.forVersion` maps every 1.x release to one `V1` rule set, and `AiToolDetector.detect1x` ports 1.17.0's `getAvailableTools` (`dist/core/available-tools.js`) and `dist/core/shared-skill-target.js`.

The captured registry dumps (`fixtures/cli/<v>/ai-tools.json`, written at each release whose registry changed) show every shape change within 1.x. A study of `available-tools.js` across all 23 cached 1.x releases found the algorithm changes. It is unchanged 1.3.0–1.7.0. 1.8.0 adds home-directory tools and shared-root reconciliation. 1.11.0 adds `hasIndependentDetectionPath`. The algorithm hasn't changed since 1.11.0. 1.15.0 moved file checks behind a `DISK_VIEW` seam (`file-view.js`), and that was checked to behave the same. The 1.9.0 and 1.11.0 additions to `shared-skill-target.js` only guard writes. Tool additions continue to 1.14.0.

## Decisions

### D1. The release reaches the 1.x path

`AiToolDetector.detect(project, home, rules, release)`: for `V1`, `release` is the detected CLI's version. `AiToolDetectionService` passes `cliVersion()`. The other callers (`fromDisk` paths, `InstallToolTicks`, `AiToolRegistry`'s link check) pass `null`, which keeps exactly today's behavior: 1.17.0's shapes and rules, and no release filter. That matters because `InstallToolTicks` runs with a 2.x CLI too. If a missing release meant 1.17.0's filter, it would drop 2.1 tools from the install dialog's ticks. `DetectionRules` stays a line-level enum. The 1.x release is a separate argument, because 2.x rules don't vary by patch release in the plugin's port.

### D2. A tool's shape as of a 1.x release

`AiToolRegistry.asOf1x(tool, release)` returns the tool's skills directory, detection paths and older directories as that release had them. It's the record itself, except for the five tools whose shape changed within 1.x, read from the dumps. Before 1.7.0 upstream names Devin Desktop `windsurf`, and lists it after `trae`; the registry check reads that id as `devin`, and holds the order only from 1.17.0, as today:

| Tool | Before | From |
|---|---|---|
| kimi | `.kimi`, no detection paths | 1.7.0: `.kimi-code`; paths `.kimi-code`, `.kimi` |
| devin (`windsurf`) | `.windsurf`, no paths | 1.7.0: `.devin`; paths `.devin`, `.windsurf` |
| codex | `.codex`, no paths, no older dirs | 1.8.0: `.agents`; paths `.agents/skills`, `.codex/skills`; older `.codex` |
| agents | no skills directory | 1.8.0: `.agents`; path `.agents/skills` |
| antigravity | `.agent`, no paths | 1.11.0: `.agents`; paths `.agent`, `.agents/workflows`; older `.agent` |

`detect1x` also reads only tools the release accepts (`acceptedBy(tool, release)`), when a release is given. That removes 2.1-only tools from every 1.x release, and each 1.x tool before its own release.

### D2a. The set-up verdict follows the release too

The set-up verdict is `AiToolDetectionService`'s port of `getConfiguredTools`, through `AiToolDetector.sharedRootOwner`. It grouped and shaped tools as 1.17 does, so for a hand-written generic skill under `.agents/skills` it counted 15 tools on 1.7.0, where the CLI counts none (`agents-generic-skill-by-hand`). With a detected 1.x release, it now counts only that release's tools, in its shapes (D2), and reconciles a shared folder among them. Two more boundaries of `getConfiguredTools` (`dist/core/shared/tool-detection.js`) apply, each confirmed by a capture:
- **Commands count from 1.7.0.** Before it, upstream counts skills only: `.claude/commands/opsx/*` alone is set up on 1.7.0 and not on 1.6.0 (`claude-commands-only`).
- **A tool's commands in an older folder count from 1.17.0.** Antigravity's `.agent/workflows` alone is set up on 1.17.0, and not on 1.11.0 or 1.14.3 (`antigravity-legacy-workflows-only`).

The old "1.6-era detection path is a skills folder" loop is gone, because D2's pre-1.7 shapes now give Kimi and Devin Desktop those folders as their skills folders. On 2.x, and with no CLI, the verdict is unchanged.

### D3. The algorithm's two boundaries

- **Below 1.8.0**, upstream's whole rule is: detection paths if a tool has them, otherwise its skills directory as a folder. There is no home-directory check and no reconciliation; every candidate is detected.
- **From 1.8.0**, home-directory tools and shared-root reconciliation apply. This needs no gate of its own, because no captured registry from 1.3.0 to 1.7.0 has two tools sharing a skills folder, or any tool with a home folder. With that release's tools and shapes (D2), reconciliation makes each candidate its own folder's owner, and the home check finds nothing. That's exactly "every candidate is detected". A gate was written first; removing it failed no test, which confirmed it changed nothing. The boundary is still pinned, by `builtin-add-codex-list-on-empty` and `agents-generic-skill-by-hand` on 1.7.0 against 1.8.0, through the shapes.
- **From 1.11.0**, an own detection path also counts beside a shared root the tool doesn't own. Between 1.8.0 and 1.10.x, only home-directory tools and each root's owner are detected.

### D4. Proof

- **The registry contract test** (`eachToolsFieldsEqualTheRegistrys`) runs on every captured 1.x dump from 1.3.0, against `asOf1x`, and compares older directories too.
- **The detection contract test** replays every captured case with the release passed. The 12 new cases sit on both sides of each boundary.
- **The known-gap entry is removed.** `KnownGaps.check` fails until it is.

## Risks

- **Upstream's `assertProjectArtifactPath` (from 1.8.0)** ignores markers and skills reached through links that point out of the project, while the plugin's `readMarker` follows them. This is not captured and stays out of scope. If it is found, it becomes its own entry.
- **The plugin's skill-name list includes 2.2's `openspec` router skill**, which no 1.x release has. A 1.x layout holding only that skill would be read differently. That's unreachable in practice: no 1.x release writes it.
