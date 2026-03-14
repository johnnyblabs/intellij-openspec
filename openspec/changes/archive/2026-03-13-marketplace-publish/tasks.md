## 1. Version and Build Configuration

- [x] 1.1 Update `version` in `build.gradle.kts` from `0.1.0` to `0.2.0`
- [x] 1.2 Add `publishing` block inside `intellijPlatform` in `build.gradle.kts` with `token` sourced from `System.getenv("JETBRAINS_MARKETPLACE_TOKEN")`
- [x] 1.3 Add `changeNotes` to `pluginConfiguration` in `build.gradle.kts` with HTML-formatted v0.2.0 release notes

## 2. Plugin Descriptor

- [x] 2.1 Expand `<description>` in `plugin.xml` with a Marketplace-ready HTML description: feature list (spec browsing, workflow automation, AI-assisted generation, validation, coverage panel, gutter markers), compatibility note, and link to OpenSpec
- [x] 2.2 Add `url` and `email` attributes to `<vendor>` tag in `plugin.xml`

## 3. Changelog

- [x] 3.1 Create `CHANGELOG.md` at project root with v0.2.0 and v0.1.0 entries

## 4. Verification

- [x] 4.1 Build the project (`./gradlew build`) and verify no compilation errors
- [x] 4.2 Run `./gradlew verifyPlugin` and confirm it passes
