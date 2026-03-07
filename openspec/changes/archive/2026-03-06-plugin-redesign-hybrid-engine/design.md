# Design: Plugin Redesign — Hybrid Engine

## Approach

Six-phase implementation with clear dependency ordering:

1. **Foundation** — Settings panel, CLI auto-detection, notification infrastructure
2. **Hybrid Validation** — Built-in validator with version-aware rules, merged with CLI output
3. **Built-in Scaffolding** — Template-driven change creation, init, archive without CLI
4. **Tool Window Enhancements** — CLI status bar, context menus, tabbed layout with console
5. **CLI Output Refinements** — Structured output parsing, background execution
6. **Dogfooding** — Track this redesign as an OpenSpec change

## Architecture

- `CliDetectionService` detects CLI at startup, caches result
- `OpenSpecSettings` (PersistentStateComponent) stores version override, CLI path, strictness
- `BuiltInValidator` runs always; CLI validation merged when available
- `ScaffoldingService` creates changes/init via VFS when CLI unavailable
- All CLI actions run in `Task.Backgroundable` to avoid EDT blocking
- `OpenSpecCliAction.handleCliMissing()` hook enables per-action fallback

## Trade-offs

- Built-in validation rules may diverge from CLI rules over time — mitigated by version-aware behavior
- Template content is hardcoded — acceptable for v0.2.0, could be externalized later
