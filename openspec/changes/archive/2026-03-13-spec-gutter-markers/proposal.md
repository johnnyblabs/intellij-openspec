## Why

Developers working in source code have no visual connection to the specs that govern their implementation. They must remember which spec requirements apply to the code they're editing. Gutter markers in source files create a direct, clickable link from implementation back to specification — the core of code-to-spec traceability.

## What Changes

- Define a comment convention `// @spec <domain>:<requirement-name>` for linking source code to spec requirements
- Add a `LineMarkerProvider` for Java files that detects `@spec` comments and shows a spec gutter icon
- Clicking the gutter icon navigates to the corresponding spec file
- Tooltip shows the requirement name and domain
- Support multiple `@spec` references on consecutive lines

## Capabilities

### New Capabilities
- `spec-gutter-markers`: Gutter markers in source code files that link back to OpenSpec requirements via `@spec` comment convention

### Modified Capabilities

## Impact

- **New class**: `SpecRefLineMarkerProvider` in the `editor` package
- **plugin.xml**: Register the new `LineMarkerProvider` for Java language
- **No new dependencies**: Uses existing IntelliJ `LineMarkerInfo` and navigation APIs
- **No changes to existing code**: Additive feature — existing line markers in spec files are unaffected
