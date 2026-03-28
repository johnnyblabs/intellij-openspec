## ADDED Requirements

### Requirement: Background thread for change selection refresh

The plugin SHALL perform artifact status lookups on a background thread when the active change is set via external sources (e.g., tree selection). The EDT SHALL NOT be blocked by CLI or orchestration service calls during change selection.

#### Scenario: setActiveChange dispatches to background thread
- **WHEN** `setActiveChange()` is called from the EDT (e.g., tree selection handler)
- **THEN** the artifact status lookup SHALL execute on a pooled background thread, not on the EDT

#### Scenario: Pipeline updates asynchronously after selection
- **WHEN** `setActiveChange()` dispatches the refresh to a background thread
- **THEN** the pipeline display SHALL update on the EDT via `invokeLater` after the background work completes
