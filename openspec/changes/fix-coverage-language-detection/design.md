## Context

The `@spec <domain>:<requirement>` convention lets developers reference a spec requirement from a code comment. Two plugin surfaces consume it, and both are currently hard-gated to Java:

1. **Coverage panel** — `SpecCoverageService.scanSourceFiles()` iterates project content but early-returns on any file whose extension is not exactly `java` (`!"java".equals(file.getExtension())`). Non-Java projects scan zero files → every requirement reads as unreferenced → 0% coverage, all specs greyed out.
2. **Gutter markers** — `SpecRefLineMarkerProvider` is registered in `plugin.xml` with `language="JAVA"`. Its logic already matches the language-neutral base `PsiComment` interface, so only the registration confines it to Java editors.

The plugin targets the full JetBrains IDE family (GoLand, PyCharm, RubyMine, RustRover, WebStorm, CLion, PhpStorm, Rider, DataGrip, DataSpell, etc.), where the primary language is frequently not Java. The `@spec` reference itself is plain comment text — the regex (`@spec\s+([\w-]+):(.+)`) is identical in both surfaces and is already language-agnostic. The only defect is the file/registration filtering.

## Goals / Non-Goals

**Goals:**
- `@spec` references in any language (`.kt`, `.go`, `.py`, `.ts`, `.rb`, `.rs`, …) count toward spec coverage.
- `@spec` gutter navigation icons appear in any language's comments, not just Java.
- The fix is robust across the JetBrains IDE matrix — including projects that have **no explicitly configured source roots** (common outside IntelliJ IDEA Java projects).
- Add the first automated tests for `SpecCoverageService`.

**Non-Goals:**
- A hardcoded extension allowlist (maintenance burden; always lags new languages).
- A user-configurable extension setting (unnecessary with auto-detect; a possible later enhancement).
- Changing the `@spec` syntax, the coverage UI, or the gutter icon.

## Decisions

### 1. Coverage scan: skip only RECOGNIZED binaries, do NOT require source-root marking
Replace the `.java`-only gate with: skip directories and skip files whose type is a **recognized binary** — `file.getFileType().isBinary() && fileType != UnknownFileType.INSTANCE`. Scan everything else under the project content roots that `ProjectFileIndex.iterateContent` already yields (excluded dirs such as `node_modules`, build output, and VCS are already filtered out by the platform).

The `UnknownFileType` carve-out is essential, not cosmetic: when the running IDE has no plugin for a language, that language's files resolve to `UnknownFileType`, whose `isBinary()` returns **true**. A plain `isBinary()` skip would therefore drop, e.g., `.go` files opened in IntelliJ IDEA Community (no Go plugin) — silently re-creating the exact 0%-coverage bug this change fixes. Treating unknown types as scannable text is what makes the fix robust across the IDE matrix; genuine binaries (images, archives, compiled classes) have registered binary file types and are still skipped.

Alternatives considered:
- **Hardcoded extension allowlist** (`java`, `kt`, `go`, `py`, …) — rejected: needs a code change for every new language; directly contradicts the "language-agnostic" goal.
- **Restrict to `ProjectFileIndex.isInSourceContent`** — rejected: many non-Java IDE projects (and lightweight "open a folder" projects) have no source roots configured, so this would re-break exactly the projects this change fixes. Skipping recognized binaries under content roots is the robust superset.
- **Plain `file.getFileType().isBinary()`** — rejected: `UnknownFileType.isBinary()` is `true`, so this skips source files in any language the IDE lacks a plugin for (the most common real-world failure mode here).
- **Scan all files including binaries** — rejected: wasteful I/O reading images/archives; the recognized-binary guard is the cheap filter.

### 2. Gutter markers: register for all languages
Change the `SpecRefLineMarkerProvider` registration from `language="JAVA"` to `language=""`, matching the already-global `OpenSpecLineMarkerProvider`. No code change to the provider is required — it already guards on `isOpenSpecProject` and `element instanceof PsiComment` and returns early otherwise. Update the class Javadoc that currently says "Java source files".

Alternative: register the provider once per supported language ID — rejected for the same maintenance reason as the allowlist.

### 3. Keep the two surfaces' regex in lockstep
Both surfaces compile the same `@spec\s+([\w-]+):(.+)` pattern independently. This change does not unify them (out of scope), but the tests assert both interpret a reference identically so they cannot silently diverge.

## Risks / Trade-offs

- **Broader I/O — coverage now reads every non-binary file under content roots, not just `.java`.** → Binary files are skipped cheaply via `isBinary()`; the platform already excludes `node_modules`/build/VCS dirs; coverage is computed on demand and cached. Acceptable for typical projects; revisit with a size cap only if a large-monorepo report surfaces.
- **`@spec` matches in non-code text (docs, config) now count.** → Harmless and arguably correct: a reference is a reference regardless of file type. Not worth special-casing.
- **The line-marker provider now runs for every language's PSI.** → It early-returns on non-`PsiComment` elements and non-OpenSpec projects, identical to the existing global `OpenSpecLineMarkerProvider`; no measurable cost.

## Migration Plan

No data, schema, or settings migration. The only observable change is that coverage percentages rise (correctly) for non-Java projects and gutter icons appear in non-Java files. Rollback is a straight revert of the code + `plugin.xml` change.

## Open Questions

None blocking. Configurability and a possible size cap are deferred follow-ups.