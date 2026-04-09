## ADDED Requirements

### Requirement: Build-time changeNotes generation

The build SHALL generate the `changeNotes` field in `plugin.xml` from `CHANGELOG.md` at build time using the `org.jetbrains.changelog` Gradle plugin. The plugin SHALL parse `CHANGELOG.md`, locate the entry matching the current project version, convert the markdown content to HTML, and inject it as the `changeNotes` value. The version header SHALL be excluded from the rendered output to avoid duplication with Marketplace's own version display.

#### Scenario: Matching version entry exists
- **WHEN** `CHANGELOG.md` contains an entry matching the current project version
- **THEN** the build SHALL render that entry's content as HTML and use it as `changeNotes`

#### Scenario: No matching version entry
- **WHEN** `CHANGELOG.md` has no entry matching the current project version and no `[Unreleased]` section exists
- **THEN** the build SHALL fail with an error indicating the missing changelog entry

#### Scenario: Unreleased fallback during development
- **WHEN** `CHANGELOG.md` has no entry matching the current project version but an `[Unreleased]` section exists
- **THEN** the build SHALL use the `[Unreleased]` section content as `changeNotes`

### Requirement: Custom header format support

The changelog plugin SHALL be configured with a `headerParserRegex` matching the project's existing `## v{version} — {title}` header format. The regex SHALL extract the semver portion from headers like `## v0.2.9 — EDT Threading Compliance`.

#### Scenario: Existing header format parsed
- **WHEN** `CHANGELOG.md` contains `## v0.2.9 — EDT Threading Compliance`
- **THEN** the plugin SHALL recognize this as the entry for version `0.2.9`

#### Scenario: Header without title parsed
- **WHEN** `CHANGELOG.md` contains `## v0.3.0`
- **THEN** the plugin SHALL recognize this as the entry for version `0.3.0`

### Requirement: No inline HTML changeNotes

The `build.gradle.kts` file SHALL NOT contain a manually-maintained inline HTML string for `changeNotes`. The `changeNotes` field SHALL be assigned exclusively via a Gradle `provider {}` that delegates to the changelog plugin's `renderItem()` method.

#### Scenario: Inline HTML removed
- **WHEN** the build script is inspected
- **THEN** there SHALL be no raw HTML strings assigned to `changeNotes`

#### Scenario: Provider-based assignment
- **WHEN** `patchPluginXml` runs
- **THEN** `changeNotes` SHALL be populated by the changelog plugin's rendering pipeline
