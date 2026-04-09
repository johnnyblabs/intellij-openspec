## 1. Add Changelog Plugin

- [x] 1.1 Add `id("org.jetbrains.changelog") version "2.2.1"` to the `plugins` block in `build.gradle.kts`
- [x] 1.2 Add `changelog {}` configuration block: set `version`, `path`, and `headerParserRegex` to match the `## v{version} — {title}` format

## 2. Replace Inline changeNotes

- [x] 2.1 Replace the inline HTML `changeNotes = """..."""` block with a `provider {}` that calls `changelog.renderItem()` using `getOrNull(version) ?: getUnreleased()` with `.withHeader(false)` and `.withEmptySections(false)`, rendered as `Changelog.OutputType.HTML`
- [x] 2.2 Delete the entire inline HTML string (~100 lines of `<h3>`, `<ul>`, `<li>` content)

## 3. Verification

- [x] 3.1 Run `./gradlew patchPluginXml` and inspect `build/tmp/patchPluginXml/plugin.xml` to verify the generated `<change-notes>` contains the v0.2.9 entry as HTML
- [x] 3.2 Run `./gradlew build` to confirm full build passes with the generated changeNotes
