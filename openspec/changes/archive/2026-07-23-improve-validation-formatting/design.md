## Context

`OpenSpecValidateAction.showValidationResults`/`showInConsole` is the single sink for every validation surface (toolbar Validate → `WHOLE_PROJECT`, Project-View scoped Validate → `CHANGE`/`SPEC`). Today it builds one `StringBuilder` and dumps it via `OpenSpecConsolePanel.printOutput` (pass) or `printError` (fail): a header line, then one line per issue `[SEVERITY] <path-from-openspec/>:<line> — <message> [<rule>]`. No hyperlinks, no per-severity color, no grouping.

`OpenSpecConsolePanel` wraps a real IDE `ConsoleView` (created via `TextConsoleBuilderFactory`) and today only exposes `print(text, ConsoleViewContentType)`-style helpers. The model is sufficient as-is: `ValidationIssue(Severity{ERROR,WARNING,INFO}, String filePath, int line, String message, String rule)` and `ValidationResult(passed, errorCount, warningCount, issues, source)`.

The two issue sources populate `filePath` differently — this is the load-bearing fact of the whole design:
- **Built-in validator** (`BuiltInValidator`): `filePath = file.getPath()` — an **absolute filesystem path** with a real 1-based `line`. Resolvable → hyperlink.
- **CLI parser** (`CliOutputParser`): `filePath` is `type + "/" + id` (e.g. `spec/actions`) or a bare id, and `line == 0`. **Not a real path** → must degrade to plain colored text, never a dead link.

## Goals / Non-Goals

**Goals:**
- Clickable `file:line` that opens the file at the line, for resolvable issues.
- Per-severity coloring via theme-driven `ConsoleViewContentType`.
- Group-by-file layout, errors-first, with a verdict/target/count header and a clean pass state.
- One shared render path so every validate surface improves at once.
- Encapsulate severity→content-type and file-resolution on `OpenSpecConsolePanel` so they're unit-testable.

**Non-Goals:**
- No dedicated results tree/tool-window tab (a console is ephemeral by design; that's a later item if users want persistence).
- No quick-fix affordances in the console (that belongs to the inspections layer).
- No resolving CLI `type/id` pseudo-paths to their backing file (a later enhancement; MVP degrades them to plain text).
- No change to the model, the notification balloon's contents, or the validation pipeline itself.
- No collapsible groups / severity filters.

## Decisions

**1. `printHyperlink` + `OpenFileHyperlinkInfo`, NOT a `Filter`.** We hold the structured `ValidationIssue` (path + 1-based line) at print time, so we print the link explicitly. A regex `Filter` would force re-serializing structured data into a `path:line` text pattern and re-parsing it — mis-firing on Windows drive-letters (`C:\…:12`), paths with spaces, and the `openspec/`-relative shortening we already do. `Filter` is only right for opaque stdout streams; reserve it for a hypothetical future "pipe raw CLI stdout" feature.

Per-issue print shape (splitting today's single `sb.toString()`):
```
print("  ", NORMAL) ; printHyperlink("L12", info) ; print("  ERROR    …message… [rule]\n", ERROR_OUTPUT)
```
`printHyperlink` prints with the console's default link attributes and takes no content type — so severity color is carried by the surrounding `print` segments, and the link token itself is the standard link style.

**2. File resolution + off-by-one.** Resolve with `LocalFileSystem.getInstance().findFileByPath(path)` (a VFS cache lookup — EDT-safe, no read action; the files were just validated so they're in VFS). If `filePath` is openspec-relative, resolve against the project base first. Do **not** use `refreshAndFindFileByPath` (synchronous VFS refresh, blocking on EDT). `OpenFileHyperlinkInfo(project, file, line)` is **0-based** (platform `RegexpFilter` subtracts 1 from its 1-based match), and the validator emits **1-based** lines → pass `line - 1`, guarding `line <= 0` → `0`. Null resolve (`findFileByPath` returns null) → print plain colored text, never construct `OpenFileHyperlinkInfo` with a null file (its ctor is `@NotNull`).

**3. Severity → content type (theme-driven, no hardcoded colors).** There is no built-in `WARNING_OUTPUT`. Map:

| Severity | ConsoleViewContentType |
|---|---|
| ERROR | `ConsoleViewContentType.ERROR_OUTPUT` (built-in) |
| WARNING | `ConsoleViewContentType.LOG_WARNING_OUTPUT` (built-in on 242) |
| INFO | `ConsoleViewContentType.LOG_INFO_OUTPUT` or `NORMAL_OUTPUT` |

All resolve through the active color scheme (correct in Darcula/Light/High-contrast). Prefer the plain built-in `LOG_WARNING_OUTPUT`/`LOG_INFO_OUTPUT` constants over constructing a custom type from `CodeInsightColors`.

**4. Encapsulate on the panel.** Add to `OpenSpecConsolePanel`: `printWarning(String)`, `printInfo(String)`, a severity→content-type helper, and `printFileHyperlink(Project, String filePath, int oneBasedLine, String linkText, ConsoleViewContentType fallbackType)` that does the resolve + degrade. This keeps the mapping in one place and unit-testable, rather than reaching through `getConsoleView()` in the action.

**5. Grouping & ordering (deterministic — matters for uiSmoke/screenshot stability).** Group by file. Files: error-containing first, then warning-only, then info-only; within a tier by path ascending. Within a file: by line ascending, ties by severity (ERROR<WARNING<INFO). CLI pseudo-path issues (line 0, unresolvable) group last under their identifier as plain non-clickable headers.

**6. Threading.** Rendering stays on the EDT inside the existing `invokeLater` continuation in `runValidation`. Resolution is a cache lookup, so building the segmented output inline on the EDT is fine — no off-EDT pre-resolve needed.

**7. Testability split** (for test-engineer PLAN at apply-start). The link *wiring* is unit-testable by factoring resolution into a pure helper: assert it builds an `OpenFileHyperlinkInfo` at `line-1` for a resolvable path and falls back to plain print for a null file; assert the group/order/severity-mapping is deterministic on structural markers. The actual *click-opens-editor-at-line* behavior is a headless-untestable UI concern → the one release-gated uiSmoke journey asserts the console *content* (verdict/count line + issue rows), not click navigation (Driver-clicking a console hyperlink and checking caret is brittle).

## Risks / Trade-offs

- **verifyPlugin gate.** The diff adds new `com.intellij.execution.filters.OpenFileHyperlinkInfo` references. All APIs here are stable, non-deprecated on 242, and multi-IDE-safe (the execution console framework ships in GoLand/PyCharm/RubyMine), so the verifier is expected green — but the pre-push `verifyPlugin` gate will (correctly) run because a new `com.intellij.*` reference is added. This is the intended safety net, not a concern.
- **CLI issues aren't navigable in MVP.** CLI-sourced issues degrade to plain text (no link) because their `type/id` isn't a filesystem path. Acceptable for MVP; resolving them to their backing `spec.md`/change dir is a noted follow-up.
- **Duplicate issues across sources.** `ValidationResult.merge` concatenates built-in and CLI issues, so the same underlying problem can appear twice (once navigable, once plain). Pre-existing behavior; presentation surfaces it but does not worsen it. Out of scope to dedupe here.
- **`LOG_WARNING_OUTPUT`/`LOG_INFO_OUTPUT` styling** derives from console log-filter attributes; if a given IDE/theme renders them too subtly, the fallback is a custom `ConsoleViewContentType` from a `CodeInsightColors` `TextAttributesKey` — a one-line swap, still theme-driven, no hardcoded `JBColor`.
