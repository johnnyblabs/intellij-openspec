## MODIFIED Requirements

### Requirement: Delivery method routing

The plugin SHALL support three delivery methods (clipboard, editor tab, Direct API) with smart default selection based on detected tools and configured providers. When the delivery method is Direct API, its configuration state SHALL gate Fast-Forward, whose Direct API run needs a provider; with clipboard or editor tab, Fast-Forward hands off to the AI tool and needs none.

#### Scenario: Resolution chain
- **WHEN** determining the delivery method
- **THEN** the plugin SHALL check: user preference → configured API → detected tools → generic clipboard fallback

#### Scenario: Direct API gates FF availability
- **WHEN** the delivery method is Direct API and Direct API is not configured (no provider selected or no API key)
- **THEN** the FF action SHALL be unavailable

#### Scenario: Fast-Forward in a handoff needs no provider
- **WHEN** the delivery method is Clipboard or Editor Tab
- **THEN** the FF action SHALL be available whether or not Direct API is configured
