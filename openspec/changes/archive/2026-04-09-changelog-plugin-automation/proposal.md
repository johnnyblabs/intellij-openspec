## Why

The `changeNotes` HTML block in `build.gradle.kts` must be manually kept in sync with `CHANGELOG.md`. This was missed for v0.2.7 and v0.2.8 — JetBrains Marketplace showed stale v0.2.6 notes to users. The CI pipeline has no guard against this. The fix is to eliminate the manual copy entirely by deriving `changeNotes` from `CHANGELOG.md` at build time using JetBrains' official gradle-changelog-plugin.

## What Changes

- Add `org.jetbrains.changelog` Gradle plugin to `build.gradle.kts`
- Configure the changelog plugin to parse `CHANGELOG.md` with version-aware lookup
- Replace the ~100 line inline HTML `changeNotes` block with a `provider {}` that calls `changelog.renderItem()` to auto-generate HTML from the current version's CHANGELOG entry
- Normalize `CHANGELOG.md` version headers to be compatible with the plugin's parser (the plugin expects `## [version]` or `## version` format)

## Capabilities

### New Capabilities
- `changelog-automation`: Build-time generation of plugin changeNotes from CHANGELOG.md via gradle-changelog-plugin

### Modified Capabilities
- `ci`: Release pipeline now depends on CHANGELOG.md having an entry matching the build version — missing entry fails the build before publish

## Impact

- **Files changed**: `build.gradle.kts`, potentially minor `CHANGELOG.md` header format normalization
- **New dependency**: `org.jetbrains.changelog` Gradle plugin (build-time only, not a runtime dependency)
- **Behavioral change**: Build fails if CHANGELOG.md has no entry for the current version — this is the desired fail-fast behavior
- **No runtime or plugin code changes**
