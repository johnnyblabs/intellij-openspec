## Why

Spec coverage reports **0%** for every non-Java project, and `@spec` gutter markers never appear, because both surfaces are hard-gated to Java source files. The plugin ships across JetBrains IDEs whose primary language is not Java — GoLand, PyCharm, RubyMine, RustRover, WebStorm, CLion, PhpStorm, Rider, DataGrip, and more — so for a large share of users the coverage feature is silently broken (all requirements show greyed-out / unreferenced) and the click-to-navigate gutter icons are absent. Reported on GitHub #18 (Kotlin), with a confirmed +1 on a Go project. The `@spec <domain>:<requirement>` reference is plain comment text and is already language-neutral; only the file/registration filters are Java-only.

## What Changes

- **Coverage scan becomes language-agnostic.** `SpecCoverageService.scanSourceFiles()` currently skips every file whose extension is not exactly `java`. It will instead scan any non-binary file under the project's content roots, regardless of language (and without requiring configured source roots, which many non-Java IDE projects lack), so `@spec` references in `.kt`, `.go`, `.py`, `.ts`, `.rs`, etc. count toward coverage.
- **`@spec` gutter markers become language-agnostic.** `SpecRefLineMarkerProvider` already operates on the base `PsiComment` interface (not Java PSI); only its `plugin.xml` registration pins it to `language="JAVA"`. It will be registered for all languages (matching the sibling `OpenSpecLineMarkerProvider`), so the navigate-to-spec gutter icon appears in any language's comments.
- **First test coverage for `SpecCoverageService`** (none exists today): non-Java files containing `@spec` references are counted; binary and non-source files are skipped.
- **Docs clarify** the `@spec` convention works in any language's comments, not just Java.
- Out of scope (possible follow-up): a hardcoded extension allowlist or a user-configurable extension setting. The auto-detect approach needs neither.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `editor`: the "Spec reference gutter markers" and "Spec coverage panel" requirements change from "Java files" to "any source file, language-agnostic".

## Impact

- **Code:** `src/main/java/com/johnnyblabs/openspec/services/SpecCoverageService.java` (scan filter); `src/main/resources/META-INF/plugin.xml` (line-marker registration); `SpecRefLineMarkerProvider` Javadoc.
- **Tests:** new `src/test/java/com/johnnyblabs/openspec/services/SpecCoverageServiceTest.java`.
- **Specs:** `openspec/specs/editor/spec.md` (delta).
- **Docs:** `docs/feature-reference.md`, `CONTRIBUTING.md` (note language-agnostic `@spec`); `CHANGELOG.md` (Fixed, v0.3.0, user-facing).
- **Platform compatibility:** no change. Uses `ProjectFileIndex`, `FileTypeRegistry`/`VirtualFile` file-type, and `codeInsight.lineMarkerProvider` APIs already available in IntelliJ IDEA 2024.2+ and shared across all the JetBrains IDEs the plugin targets.
- **Tracker:** GitHub #18 (public bug report). Resolution ships as a GitHub PR linked with `Closes #18`; mirrored to internal trackers via the sidecar.