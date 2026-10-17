## Why

Without the CLI, the plugin validates changes with its built-in validator. That validator accepts a delta whose only content is a rename written with bare names (`- FROM: Old` / `- TO: New`). Every supported OpenSpec CLI rejects it, from 1.3.0 to 1.14.3, with two errors: "Delta sections ## RENAMED Requirements were found, but no requirement entries parsed", and "Change must have at least one delta". So a change the plugin calls valid fails `openspec validate` and is refused by `openspec archive`.

The cause is that the built-in validator doesn't read entries the way OpenSpec does:
- **Rename lines:** its rename pattern accepts `FROM:`/`TO:` lines without the `### Requirement:` header, which OpenSpec ignores.
- **Empty files:** it has no per-file "sections but no entries" check.
- **The "no deltas" rule:** it counts delta files, not entries.

The plugin's merge already reads entries like OpenSpec (`SpecMerge`, shipped in this release). The validator should use the same reader.

Probing the supported range (captured in the design) also bounds the fix. A bare rename **beside** a valid ADDED requirement is valid on every supported CLI, which silently ignores the bare lines. So bare lines must not become errors in their own right, or the validator would reject what every CLI accepts.

Tracked in the linked tracker entry.

## What Changes

- **Read entries with OpenSpec's reader.** The built-in validator and the delta IDE inspection recognise delta entries exactly as `openspec archive` and `validate` do, through the shared `SpecMerge` parser:
  - `### Requirement:` blocks;
  - REMOVED bullets carrying the header;
  - `FROM:`/`TO:` lines carrying the header.

  Bare `FROM: Old` lines are not entries.
- **Upstream's per-file rule.** A delta file that has delta sections but no parsed entries is an ERROR, with OpenSpec's wording ("Delta sections … were found, but no requirement entries parsed. …").
- **"At least one delta" counts entries.** The change-level rule now counts parsed entries across a change's delta files, not delta files.
- **Unpaired renames use upstream's reading and wording.** `delta-renamed-fields` reports a header-form `FROM:` with no `TO:` (or a `TO:` with no `FROM:`), with OpenSpec's message: "RENAMED FROM: "<name>" has no matching TO: line. …". It no longer reports bare lines, which are ignored, as OpenSpec ignores them.
- **Unchanged:** an empty RENAMED section beside other entries stays valid, and star- or plus-bulleted header-form pairs stay accepted.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `validation`: delta spec validation and the delta IDE inspection read entries with OpenSpec's reader; a delta file with sections but no entries is an error; the "at least one delta" rule counts entries; unpaired renames follow upstream's reading and wording.

## Impact

- **Code:**
  - `BuiltInValidator`: the delta validation and the change-level "no deltas" rule.
  - `DeltaSpecInspection`.
  - A small public entry summary on `SpecMerge`: the entry count and unpaired renames.
  - `SpecPatterns.RENAMED_PAIR`, if it has no reader left.
- **Tests/fixtures:**
  - `fixtures/cli/1.14.3/validate-renamed/<case>/` and `1.3.0/validate-renamed/<case>/`: real `validate --json` for bare-only, bare beside ADDED, unpaired-only and unpaired beside ADDED;
  - a contract test that the built-in verdict matches 1.14.3 per case, and is never stricter than 1.3.0 except where 1.13.1+ refuses (documented);
  - the existing `delta-renamed-fields` scenarios updated.
- **Docs:** CHANGELOG `### Fixed`.
