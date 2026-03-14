## Why

The plugin has `@spec domain:requirement` gutter markers in Java files and a tree of parsed specs, but no way to see the relationship between them — which requirements have code references and which don't. A coverage panel closes this loop, giving developers a quick view of spec-to-code traceability without manually searching.

## What Changes

- Add a `SpecCoverageService` that scans Java source files for `@spec` references and cross-references them against parsed specs from `SpecParsingService`
- Add a "Coverage" tab to the OpenSpec tool window showing per-domain and per-requirement coverage status
- Each requirement shows covered (has `@spec` reference in code) or uncovered, with click-to-navigate to the referencing code or spec file
- Show overall and per-domain coverage percentages

## Capabilities

### New Capabilities

- `spec-coverage`: Spec-to-code coverage analysis and display panel

### Modified Capabilities

- `tool-window`: Adding a new "Coverage" tab to the tool window

## Impact

- **Code**: New service (`SpecCoverageService`), new panel class (`SpecCoveragePanel`), tool window factory updated to add tab
- **Performance**: File scanning runs on background thread; results cached and refreshed on demand
- **Dependencies**: Reuses existing `SpecParsingService` and the `@spec` regex pattern from `SpecRefLineMarkerProvider`
