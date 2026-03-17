## MODIFIED Requirements

### Requirement: Delivery method routing

The plugin SHALL support three delivery methods (clipboard, editor tab, Direct API) with smart default selection based on detected tools and configured providers. The Fast-Forward flow SHALL use the same delivery method resolution as the standard generation flow rather than hardcoding Direct API.

#### Scenario: Resolution chain
- **WHEN** determining the delivery method
- **THEN** the plugin SHALL check: user preference → configured API → detected tools → generic clipboard fallback

#### Scenario: FF respects delivery method
- **WHEN** FF creates a change and triggers generation
- **THEN** the generation method SHALL match the currently selected tool/delivery method in the WorkflowActionPanel tool selector
