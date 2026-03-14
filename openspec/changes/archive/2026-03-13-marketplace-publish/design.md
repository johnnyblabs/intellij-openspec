## Context

The IntelliJ Platform Gradle Plugin (v2.13.0) provides `publishPlugin` and `signPlugin` tasks. The build already uses `pluginConfiguration` for ID, name, vendor, and IDE version. Publishing requires adding a token, expanding the description, and ensuring the plugin passes `verifyPlugin`.

The `pluginIcon.svg` already exists at the correct location (`META-INF/pluginIcon.svg`). All runtime icons have light/dark variants.

## Goals / Non-Goals

**Goals:**
- Make the plugin publishable to JetBrains Marketplace via `./gradlew publishPlugin`
- Create a compelling Marketplace listing (description, changelog, vendor info)
- Bump version to 0.2.0 to match the tagged release
- Pass `verifyPlugin` with no errors

**Non-Goals:**
- Plugin signing (optional for non-paid plugins, can add later)
- Automated CI/CD publishing pipeline (manual publish for v0.2.0)
- Marketplace paid/freemium model (plugin is free)

## Decisions

### Decision 1: Marketplace token via environment variable

Use `System.getenv("JETBRAINS_MARKETPLACE_TOKEN")` in `build.gradle.kts`. The token is not committed to the repo — it's set in the shell before running `./gradlew publishPlugin`. This matches the existing pattern of keeping secrets in environment variables.

### Decision 2: Rich HTML description in plugin.xml

The `<description>` tag in `plugin.xml` supports HTML (inside CDATA). Expand it with feature list, usage overview, and links. This is what appears on the Marketplace listing page. Keep it concise but informative.

### Decision 3: Change notes in build.gradle.kts patchPluginXml

Use the `changeNotes` property in `pluginConfiguration` to inject release notes at build time. This keeps the source of truth in one place rather than duplicating between `plugin.xml` and `build.gradle.kts`.

### Decision 4: Version 0.2.0 for first Marketplace release

The first published version is 0.2.0 since that's the current tagged release. v0.1.0 was an internal milestone. The CHANGELOG includes both for history.

## Risks / Trade-offs

- **[Low risk] Marketplace review time** → JetBrains reviews new plugin submissions, typically within a few days. Automated checks run immediately.
- **[Low risk] Description formatting** → Marketplace renders a subset of HTML. Stick to `<p>`, `<b>`, `<ul>`, `<li>`, `<a>`, `<br>` tags.
- **[Trade-off] No plugin signing** → Signed plugins get a verified badge. Skipping for now since signing requires a certificate and adds build complexity. Can add in a future release.
