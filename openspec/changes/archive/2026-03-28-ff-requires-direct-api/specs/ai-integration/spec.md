## MODIFIED Requirements

### Requirement: Delivery method routing

The plugin SHALL support three delivery methods (clipboard, editor tab, Direct API) with smart default selection based on detected tools and configured providers. The Direct API configuration state SHALL additionally gate the availability of Fast-Forward, since FF depends on Direct API for its end-to-end artifact generation workflow.

#### Scenario: Resolution chain
- **WHEN** determining the delivery method
- **THEN** the plugin SHALL check: user preference → configured API → detected tools → generic clipboard fallback

#### Scenario: Direct API gates FF availability
- **WHEN** Direct API is not configured (no provider selected or no API key)
- **THEN** the FF action and FF panel link SHALL be unavailable