## Why

OpenSpec CLI **1.15.0** is the new npm-latest line, released 2026-10-24, and the plugin should declare it supported. It also ships a standalone binary that runs without Node.js. An analysis against the real npm 1.14.3, npm 1.15.0 and standalone 1.15.0 binaries found nothing the plugin parses has changed:
- Over the parity corpus, the `validate --all --json` default and `--strict` documents are byte-identical to 1.14.3, apart from `durationMs`.
- Every other JSON shape the plugin reads is identical, apart from version strings.
- The AI-tool registry is unchanged.

So this change declares the line and adds captured evidence and version guards, and changes no behaviour.

Tracked in the linked tracker entry.

## What Changes

- **Declare `1.15.x` a supported generation.** The floor stays `1.3.0`, with no ceiling. Bump the plugin's target version from `1.14.3` to `1.15.0`.
- **Capture real 1.15.0 contract fixtures:**
  - the parity-corpus twins (default and `--strict`), which the cross-generation verdict guard picks up automatically;
  - `version.txt`;
  - the standalone binary's `--version` output (a bare `1.15.0`) and its `version` output (`OpenSpec 1.15.0 (standalone)`).
- **Pin that the standalone binary's version parses.** The version the plugin's CLI detection reads from the standalone binary SHALL parse to `1.15.0`, so a standalone install is recognized as the 1.15 generation once it is found. Finding it in its install location is separate, tracked work.
- **Extend the version guards:** add `1.15.0` to the verdict-stability floor and the at-least assertions, and add anti-lexical ordering checks (`1.15.0` above `1.14.3` and `1.9.0`).
- **Verdict-parity anchors stay put.** Default stays `1.8.0`, strict stays `1.6.0`, and the archive-drop divergence stays pinned from `1.14.2`. 1.15.0 adds no validation rule, so there is nothing to mirror or deliberately not adopt.
- **Adopt-sync:** upgrade the local CLI (pinned) and run `openspec update`. This regenerates the managed skills and commands; only the `generatedBy` stamp changes for this repository's profile.
- **Out of scope:** each of these is its own tracker entry.
  - finding the standalone binary in its install locations (`~/.local/bin` and the Windows per-user programs directory);
  - a preview before Update using 1.15's `update --dry-run --json`;
  - mentioning the standalone installer in install hints.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `plugin-core`: in "Supported CLI versions and capability preservation", add `1.15.x` to the declared lines and add a "1.15.x line is a supported generation" scenario.

## Impact

- **Code (`src/main`):** none expected. The existing parsers and version detection already handle every 1.15.0 output, and the contract fixtures prove it. If a parser turns out to reject a 1.15 output, it is fixed under this change.
- **Tests and fixtures:**
  - a new `src/test/resources/fixtures/cli/1.15.0/` directory (with a `standalone/` subfolder);
  - `ValidatorVerdictVersionStabilityTest.FLOOR`, `CliVersionAtLeastTest` and `CliVersionTest` extended;
  - `openspecTargetVersion` bumped to `1.15.0`, which `TargetVersionSingleSourceTest` checks.
- **Docs (vendor-neutral):** `openspec-support.md`, README, fixtures README and CHANGELOG. The `plugin vX.Y.Z` restatements are bumped at the release cut, not here.
- **Local tooling:** pinned CLI upgrade to 1.15.0, then `openspec update`.
- **Platforms:** no change to IntelliJ 2024.2+ compatibility.
