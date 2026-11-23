# Design — CLI 2.x contract fixtures

## Context

See proposal.md for why. Current state that shapes the approach:

- Captures come from Python scripts in `scripts/` (`capture-ai-tools-fixtures.py`, `capture-workflow-ids.py`, `capture-workflow-invocations.py`, `capture-update-dry-run-fixtures.py`, `capture-error-fixtures.py`). Each one installs a published package into a scratch `npm --prefix` folder and runs `bin/openspec.js` from there, under an isolated `HOME` and `XDG_*` with telemetry off. That already satisfies the rule against pinned `npx` runs.
- Detection layouts are stored as `detect/<case>/layout.json` (`{"project": {path: content}, "home": {...}}`) and recreated by a test helper. They have no notion of a link: a 2.x layout read today would follow each tool-folder link and record the skills twice.
- The registry, detection, invocation and update-plan contract tests read one version, the target from `gradle.properties` (1.17.0) or a hard-coded `1.14.3`. The target is not bumped here; that is the last change in the 2.x line.
- `ValidatorVerdictVersionStabilityTest.FLOOR` lists the versions whose validation corpus must exist.

## Goals / Non-Goals

**Goals:**
- Every 2.x fixture is produced by a script that can be rerun, never by hand.
- 2.x link layouts are captured faithfully and recreated in all three forms a user's checkout can have.
- The four contract tests run over every captured version, and the 2.x gaps they reveal are listed in one place.

**Non-Goals:**
- Any `src/main` change. The plugin does not learn the 2.x layout here.
- The flag guard, a shared CLI-flag builder, and the full version-matrix helpers. Those belong to the CLI version-compatibility matrix change, which builds on the `--help` sweep captured here.
- Bumping `openspecTargetVersion`, or changing the supported range in public docs.

## Decisions

### Capture 2.0.0, 2.1.0 and 2.1.1 in full

Each version gets its own folder with every family, not just the families that differ. 2.0.0 is the first 2.x layout and the most likely CLI for a user who has not updated. 2.1.0 adds `--scope` and the machine-wide default but installs skills by cloning upstream over Git. 2.1.1 installs offline and adds tool inference from `skills-lock.json`. Deduplicating identical families would save a few kilobytes and cost a lookup rule every test has to share. *Alternative:* capture 2.1.1 only and note the others. Rejected, because backward compatibility has to be proven against 2.0.0 and 2.1.0 too.

### The `--help` sweep uses the newest patch of each minor

`help/<command>.txt` is captured at the newest published patch of every 1.x minor from 1.3 and at each 2.x version, for the commands the plugin runs: `init update validate status instructions archive schema store workset list show new`. Flags change by minor, not patch, in the 1.x history, so one patch per minor is enough. A version folder that does not exist yet is created for it. A command a version lacks (for example `store` before 1.5) is recorded in `help/index.json` as `{"<command>": {"exit": <code>, "file": null}}` rather than silently skipped, so the flag guard can tell "flag missing" from "command missing".

### Links go in a `links` map beside the files

`layout.json` gains a top-level `"links": {"project": {"<path>": "<target>"}, "home": {...}}`, present only when a side has links, so a 1.x layout is unchanged. 2.x links each skill folder (`.claude/skills/openspec-explore` → `../../.agents/skills/openspec-explore`), not the whole `skills` folder. The capture walks the tree without following links, so the skills appear once, under `.agents/skills/`. A relative target that lands inside the captured tree is recomputed from real paths: an installer that resolved macOS's `/private/var` differently from `/var` writes a target that climbs through the temp directory, with a machine-dependent number of `..` segments. Any other target is kept as written and sanitized. The test materializer takes a mode:

| Mode | What it writes | Models |
|---|---|---|
| `SYMLINK` | a real directory link | macOS, Linux, Windows with developer mode |
| `COPY` | a copy of the target folder | `skills add` copy installs |
| `LINK_TEXT` | a file holding the target path | a Windows checkout with `core.symlinks=false` |
| `SYMLINK_OR_COPY` | a real link, or a copy where links can't be made | the detection contract, which needs the skill readable on every platform |

`SYMLINK` is skipped with `assumeTrue` when `Files.createSymbolicLink` fails. *Alternative:* commit real symlinks. Rejected: on Windows they check out as text files, so the same test would see a different tree per platform. The hygiene test walks `src/test/resources/fixtures/` reading each entry's attributes without following links, and fails on any link or junction, naming its path. It also lists git's index for mode `120000` entries, because a committed link checks out as a plain file where `core.symlinks` is off, which the disk walk alone would miss.

### Detection cases added for 2.x

- `scope-project` (2.1.x): `init --scope project --tools …`.
- `scope-default-global` (2.1.x): `init --tools …` with no scope. The skills land in the isolated HOME, which is recorded on the home side.
- `mixed-1.17-and-2x`: a project initialised by 1.17.0, then by 2.x.
- `global-2.1-read-by-2.0`: a 2.1 machine-wide install, detected by 2.0.0.
- `user-skill-kept` (2.0+): a user's own `openspec-*` skill already in `.agents/skills/`. 2.0 records it as a `keep <skill>` line in `.openspec-target`; 2.1 no longer writes keep lines.
- `skills-lock-agents` (2.1+): the `skills-lock.json` and skill that `npx skills add` leaves, with no `.openspec-target`. 2.1.1 infers the `agents` tool from the lock; 2.1.0 does not.

The `.openspec-target` each version writes is recorded inside these layouts, so it needs no separate fixture. `own-files` lines appear in every 2.x Claude init, so they need no case of their own.

The 1.x cases that still make sense on 2.x are rerun unchanged. Cases for retired tools (Amazon Q) move to `errors/`.

### Version-parameterized contract tests with a strict gap list

The four tests enumerate the version folders that contain their fixture family and run each case per version. Where the plugin does not yet match a 2.x capture, the case is listed in one `KNOWN_GAPS` map: `version + case → the change that closes it`. A listed case is still run and must still diverge, so a gap that gets fixed fails the test until its entry is removed. A gap can't be added silently, and a fixed one can't linger. *Alternative:* run only versions the plugin passes. Rejected, because the 2.x work would then have no failing evidence to drive it.

### Sanitizing

Absolute project paths become `/fixture/demo-project` and HOME paths become `/fixture/home`, matching existing captures. `projects.json` (written by 2.x under the data dir) is captured with both rewrites applied. A post-capture check greps every new file for the scratch directory prefix and the real HOME, and refuses to write the fixture if either appears.

### Provenance

Each new version folder gets a README section that records `openspec --version`, the package's `dist.integrity` from `npm view`, the capture date, and the script that produced each family.

## Risks / Trade-offs

- **2.1.0's skill install needs Git and network** → the capture script checks for both up front and fails with a clear message. The 2.1.0 `global-skills` dry-run failure path is captured only if it reproduces; if it doesn't, the README records that it was not captured.
- **2.1.x needs Node 22.20 or newer** → the script checks `node --version` before capturing 2.1.x and stops with the required version.
- **Captures can include interactive prompts** → runs set `OPEN_SPEC_INTERACTIVE=0` and close stdin, so a prompt fails fast instead of hanging.
- **Fixture volume grows** → about 15 version folders gain a small `help/` set, and three full 2.x folders are added. Text only. Acceptable.
- **The strict gap list can feel noisy** while 2.x work lands → each later change removes its own entries in the same PR, which is the point.
