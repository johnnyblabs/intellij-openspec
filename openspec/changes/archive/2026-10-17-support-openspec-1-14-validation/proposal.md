## Why

OpenSpec CLI **1.14.x** (1.14.0–1.14.3) is the current line on npm, and the plugin should declare it supported. An analysis against the real 1.14.1 and 1.14.3 binaries found no breaking change to anything the plugin parses: every JSON shape is unchanged or only gains keys, and the on-disk store and workset layout is the same. It did find one thing the plugin's own guard can't absorb. 1.14.2 adds a warning that makes **one parity-corpus item fail under `--strict`**. That is the first time a newer CLI is *stricter* than the strict anchor, and the version-stability test currently demands an exact match of strict verdicts across generations.

Tracked in the linked tracker entry.

## What Changes

- **Declare `1.14.x` a supported generation.** The floor stays `1.3.0`, with no ceiling. Bump the plugin's target version from `1.13.2` to `1.14.3`.
- **Rework the strict arm of the cross-generation verdict guard** from exact equality to **subset of the strict anchor**. Every captured generation's `--strict` valid-set must be contained in the anchor's (`1.6.0`) valid-set. The plugin matches the anchor, so it stays never more restrictive than any generation, including one that, like 1.14.2, rejects more. The guard also **pins the known 1.14 divergence** (exactly `nameless-change` drops out under `--strict`), so the change can't pass vacuously and any further drift fails loudly.
- **Mirror 1.14's new warnings as "fallback laxer" by policy.** None of the new rules is adopted into the built-in validator:
  - an unknown `.openspec.yaml` key warns (1.14.0), with lowercase `x-*` keys exempt (1.14.2);
  - a very long requirement goes from info to warning (1.14.1);
  - text that archiving would drop (before the first requirement, above the first section, or under a non-delta section) warns (1.14.2).

  All are warnings that only fail under `--strict`. Leaving them out keeps the CLI-absent fallback never more restrictive than the 1.3–1.13 clients it also serves. When the CLI is present, its warnings already surface at their own severity through the existing validate-output parser.
- **Tolerate the additive output.** `show --json` gains `name` on requirements and scenarios; `status --json` and `instructions --json` gain optional `warnings[]`; `instructions apply` gains `tasks[].sourcePath` and `line`. Contract fixtures captured from 1.14.3 prove the existing parsers ignore these keys and still read what they need.
- **Capture real 1.14.3 contract fixtures:** the parity-corpus twins (default and `--strict`), `version.txt`, a positive control per new warning, and the additive-shape samples above.
- **Project instructions:** correct `CLAUDE.md`, which says `.openspec.yaml` *silently* strips unknown keys. Since 1.14 the CLI warns about them, with `x-*` keys exempt. The rule to keep tracker IDs in the gitignored sidecar is unchanged.
- **Out of scope:**
  - shortening this repository's own over-long spec requirements, which now fail `--strict` under 1.14.3 (no CI step runs strict validation);
  - any change to the plugin's validation behaviour beyond the above.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `plugin-core`: in "Supported CLI versions and capability preservation", add `1.14.x` to the declared lines and a "1.14.x line is a supported generation" scenario.
- `validation`: in "CLI verdict is authoritative when the CLI is available", the strict-mode cross-generation invariant becomes subset-of-the-strict-anchor (like the default arm), with the known 1.14 strict divergence pinned.

## Impact

- **Code (`src/main`):** none expected. The parsers already tolerate unknown keys, and the contract fixtures prove it. If a parser turns out to reject a new key, that is fixed under this change.
- **Tests/fixtures:**
  - New `src/test/resources/fixtures/cli/1.14.3/`.
  - `ValidatorVerdictVersionStabilityTest`'s strict arm is redesigned.
  - `FLOOR`, `CliVersionAtLeastTest` and `CliVersionTest` are extended.
  - `openspecTargetVersion` is bumped to `1.14.3`, coupled by `TargetVersionSingleSourceTest`.
- **Docs (vendor-neutral):** `openspec-support.md`, README, CHANGELOG, fixtures README, and the project `CLAUDE.md` wording.
- **Local tooling:** pinned CLI upgrade to 1.14.3 and `openspec update` to regenerate the managed skills.
- **Platforms:** no change to IntelliJ 2024.2+ compatibility.
