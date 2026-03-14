## Why

The plugin is feature-complete for v0.2.0 but only installable from a local ZIP. Publishing to the JetBrains Marketplace makes it discoverable, auto-updatable, and gives it a credible public listing. There are currently 0 JetBrains plugins for OpenSpec — this would be the first.

## What Changes

- Update `build.gradle.kts` version to `0.2.0` and add `publishPlugin` configuration
- Add `<change-notes>` to `plugin.xml` with v0.1.0 and v0.2.0 feature summaries
- Expand `<description>` in `plugin.xml` with a richer Marketplace-ready description including features, usage, and links
- Add `<vendor>` URL and email for Marketplace contact info
- Create `CHANGELOG.md` at project root
- Run plugin verification (`./gradlew verifyPlugin`) to confirm Marketplace readiness
- Add Marketplace token configuration to the publish workflow

## Capabilities

### New Capabilities

_None — this is build/packaging configuration, not plugin functionality._

### Modified Capabilities

_None._

## Impact

- **Build**: Version bumped to 0.2.0, publishing tasks added to Gradle
- **plugin.xml**: Richer description and change notes for Marketplace listing
- **New file**: `CHANGELOG.md` at project root
- **Credentials**: Marketplace API token needed (stored in environment, not committed)
