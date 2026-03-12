## MODIFIED Requirements

### Requirement: Getting started guide exists

The project SHALL provide a getting-started guide at `docs/getting-started-copilot.md` that walks users through plugin setup and a complete worked example using GitHub Copilot.

#### Scenario: Guide file location
- **WHEN** a user looks for onboarding documentation
- **THEN** a file SHALL exist at `docs/getting-started-copilot.md`

## ADDED Requirements

### Requirement: NO_CHANGES empty state includes educational content
The GettingStartedPanel NO_CHANGES state SHALL include a brief explanation of what a change is and how to scope one, in addition to the "Propose a Change" button.

#### Scenario: Change concept explained
- **WHEN** the GettingStartedPanel displays the NO_CHANGES state
- **THEN** the description text SHALL explain that a change represents a scoped unit of work with a name, motivation, and list of what will be built or modified

#### Scenario: Scoping guidance provided
- **WHEN** the GettingStartedPanel displays the NO_CHANGES state
- **THEN** the description text SHALL include a brief tip on how to scope a change (e.g., "Keep it focused — one feature or fix per change")
