## Context

`build.gradle.kts` currently has a ~100 line inline HTML string for `changeNotes` that must be manually updated alongside `CHANGELOG.md` on every release. This was missed for two consecutive releases (v0.2.7, v0.2.8), causing JetBrains Marketplace to display stale notes. The CI release pipeline (`v*` tag → build → sign → publish) has no validation that `changeNotes` is current.

JetBrains provides the `gradle-changelog-plugin` (`org.jetbrains.changelog`) specifically for this problem. It's the approach used by the official IntelliJ Platform Plugin Template and production plugins like Google's intellij-gn-plugin.

## Goals / Non-Goals

**Goals:**
- Make `CHANGELOG.md` the single source of truth for release notes
- Auto-generate `changeNotes` HTML from CHANGELOG.md at build time
- Fail the build if CHANGELOG.md has no entry for the current version (fail-fast before publish)

**Non-Goals:**
- Changing the CHANGELOG.md content style or adopting strict "Keep a Changelog" category groupings (Added/Changed/Fixed etc.) — our bullet-list format works fine
- Automating CHANGELOG.md authoring — humans still write the entries
- Modifying the CI workflow YAML — the build-time generation handles everything

## Decisions

### D1: Use `org.jetbrains.changelog` Gradle plugin

**Choice**: Add the official JetBrains gradle-changelog-plugin.

**Why**: It's maintained by JetBrains, purpose-built for IntelliJ plugins, handles markdown-to-HTML conversion, and supports version-aware entry lookup. No custom parsing needed.

**Alternative considered**: Custom Kotlin build script that reads CHANGELOG.md and converts to HTML via regex — rejected because it's fragile, unsupported, and reinvents what the plugin already does.

### D2: Version header format — keep `## v0.2.9 — Title` style

**Choice**: Configure the plugin's `headerParserRegex` to match our existing `## v{version} — {title}` header format rather than reformatting CHANGELOG.md to the plugin's default `## [{version}]` format.

**Why**: Our CHANGELOG already has 10+ entries in this format. Reformatting is unnecessary churn. The plugin supports custom regex via `headerParserRegex`.

**Regex**: `## v(\\d+\\.\\d+\\.\\d+).*` — captures the semver portion from headers like `## v0.2.9 — EDT Threading Compliance`.

### D3: Fallback to `getUnreleased()` for development builds

**Choice**: Use `getOrNull(version) ?: getUnreleased()` pattern.

**Why**: During development, the version in `build.gradle.kts` may be bumped before the CHANGELOG entry exists. Falling back to an `[Unreleased]` section (if present) keeps `./gradlew build` working. If neither exists, the build fails — which is the correct behavior for a release tag.

### D4: Strip header from rendered output

**Choice**: Use `.withHeader(false)` when rendering.

**Why**: The JetBrains Marketplace already displays the version number separately. Including the `## v0.2.9 — Title` header in the HTML would duplicate it.

## Risks / Trade-offs

- **CHANGELOG.md format sensitivity**: If a future entry doesn't match the header regex, the build fails. This is intentional — it's the fail-fast guard we want. Mitigation: the regex is simple and matches our established convention.
- **Plugin version compatibility**: The changelog plugin must stay compatible with our Gradle and IntelliJ Platform plugin versions. Mitigation: it's maintained by JetBrains alongside the platform plugin; version conflicts are unlikely.
- **No rollback risk**: If the plugin causes issues, reverting is trivial — restore the inline HTML block and remove the plugin. No data loss, no migration.

## Migration Plan

1. Add the changelog plugin to `build.gradle.kts`
2. Configure `headerParserRegex` for our format
3. Replace inline `changeNotes` with provider-based generation
4. Run `./gradlew patchPluginXml` to verify HTML output
5. Run `./gradlew build` to confirm full build passes
