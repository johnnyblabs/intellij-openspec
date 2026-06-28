## ADDED Requirements

### Requirement: Schema/mode-aware workflow surfaces

Workflow surfaces (propose, action panel, pipeline, status strip) SHALL consult the resolved workflow schema context and adapt to `actionContext.mode` rather than assuming a `spec-driven`, repo-local layout. For the default spec-driven repo-local case, surfaces SHALL behave exactly as they do today. For a non-default mode (e.g. `workspace-planning`), surfaces SHALL reflect that mode rather than presenting spec-driven-only affordances as if they applied.

#### Scenario: Default spec-driven repo-local is unchanged
- **WHEN** the resolved schema context reports `spec-driven` / repo-local
- **THEN** the workflow surfaces SHALL render and behave exactly as before this change

#### Scenario: Non-default mode is reflected
- **WHEN** the resolved schema context reports a non-default `actionContext.mode` such as `workspace-planning`
- **THEN** the workflow surfaces SHALL adapt to that mode and SHALL NOT present spec-driven-only affordances as applicable

#### Scenario: Surfaces read context, not filesystem layout
- **WHEN** a workflow surface needs to know the active mode or source of truth
- **THEN** it SHALL obtain it from the resolved workflow schema context rather than inferring it from the on-disk directory layout
