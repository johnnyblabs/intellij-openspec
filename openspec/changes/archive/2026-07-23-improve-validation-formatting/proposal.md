## Why

When the plugin validates a project, change, or spec, it prints every issue to the OpenSpec console as one flat, single-color text blob: a header line, then `[SEVERITY] path:line — message [rule]` repeated. Nothing is clickable, so a user reading an error has to hand-navigate to the file; severities are visually indistinguishable; and a clean pass reads the same as a wall of warnings. The console already wraps a real IDE `ConsoleView` that supports hyperlinks and per-segment coloring for free — the presentation simply doesn't use them. This is the last item in the Spec Intelligence & Viewing epic, and it makes validation results *navigable* rather than merely *printed*. (Tracks the validation-result-formatting tracker entry.)

## What Changes

- **Clickable `file:line` navigation.** Each issue's location becomes a hyperlink that opens the file at the reported line in the editor. Issues from the built-in validator carry absolute paths and real 1-based lines and become links; issues from the CLI parser carry non-filesystem pseudo-paths (`spec/<id>`) and line 0 and degrade to plain (still colored) text with no dead link.
- **Per-severity coloring.** ERROR / WARNING / INFO render in distinct, theme-driven console content types instead of one uniform color, so severity is legible at a glance.
- **Group-by-file layout with a restructured header.** Issues are grouped under a per-file header (files containing errors first; within a file, by line), beneath a verdict + target line (`Validation FAILED — Change \`x\``) and a count line. CLI pseudo-path issues group last under their identifier.
- **Clean pass / empty state.** A passing run renders a concise confirmation (`✓ Validation PASSED — <target>`, `No issues found.`) rather than an empty or ambiguous blob.
- **Single sink, every surface.** The improvement lands in the one shared result-rendering path, so the toolbar Validate, the Project-View scoped Validate, and any future caller all benefit at once. The at-a-glance balloon notification is unchanged; the console becomes the navigable detail surface.
- **Demo surface.** One screenshot-tour frame of the colored, navigable validation console, and one release-gated uiSmoke journey asserting the console renders the verdict/count line and issue rows.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities

- `validation`: ADD a requirement governing how validation results are *presented* in the console — grouped by file, per-severity coloring, clickable `file:line` hyperlinks for resolvable paths with graceful degradation for non-filesystem CLI paths, a restructured verdict/count header, and a clean pass/empty state. This is presentation-only: it reflects the existing `ValidationIssue` model (severity ERROR/WARNING/INFO, filePath, line, rule) and MUST NOT invent new severities or a per-file verdict (file headers are grouping keys, not pass/fail badges).

## Impact

- **Affected code:** `OpenSpecValidateAction` (`showValidationResults`/`showInConsole` — the single render site) and `OpenSpecConsolePanel` (new typed print helpers: severity→`ConsoleViewContentType` mapping, and a `file:line` hyperlink helper with file resolution + graceful degradation). No model changes — `ValidationIssue`/`ValidationResult` already carry every field needed.
- **Platform APIs:** introduces `com.intellij.execution.filters.OpenFileHyperlinkInfo` and `ConsoleView.printHyperlink` plus `ConsoleViewContentType` severity constants. All are long-standing, non-deprecated, and present on the plugin's minimum 2024.2 (sinceBuild 242) across the JetBrains IDE family — no since-build bump. The diff adds new `com.intellij.execution.*` references, so the Plugin Verifier pre-push gate will run.
- **Threading:** rendering stays on the EDT (as today, inside the existing `invokeLater`); file resolution is a VFS cache lookup (`LocalFileSystem.findFileByPath`), EDT-safe, no read action needed.
- **No new dependency, no new extension point.** Platform minimum stays 2024.2.
