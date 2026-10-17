## ADDED Requirements

### Requirement: In-panel links and buttons launch actions through the platform action pipeline

When a link or button inside the plugin's tool window, Getting Started panel, workflow panel, or Setup Wizard launches a plugin action (for example Propose, or the delta-spec diff from the deltas preview), the plugin SHALL run that action through the IDE's standard action-execution pipeline, with a data context derived from the UI component the user interacted with (or, when that component has been disposed, from the project's frame). The action's own availability check SHALL run first, so an action that is disabled in the current context does not run. The plugin SHALL NOT use platform API that is deprecated on any IDE in its supported range for this purpose when a non-deprecated equivalent exists at the compatibility floor.

The Propose action and the delta-spec diff action SHALL be available while the IDE is indexing, because they do not depend on the IDE's indexes.

#### Scenario: Propose from an in-panel link opens the Propose flow
- **WHEN** the user clicks an in-panel Propose affordance (the Getting Started button, the workflow panel link, or the "No active changes" tree hint) in an OpenSpec project
- **THEN** the Propose action SHALL run, exactly as when it is invoked from the menu

#### Scenario: A disabled action does not run
- **WHEN** an in-panel affordance launches an action whose availability check reports it disabled in the current context
- **THEN** the action SHALL NOT run

#### Scenario: Available during indexing
- **WHEN** the IDE is indexing and the user clicks an in-panel Propose affordance or a delta-spec diff link
- **THEN** the action SHALL run rather than being silently skipped

#### Scenario: No deprecated action-invocation API
- **WHEN** the Plugin Verifier checks the plugin against every IDE in the supported range
- **THEN** it SHALL report no usage of the deprecated `ActionUtil.invokeAction` overloads
