## Why

I think there is some gaps in testing, perhaps with the settings tool functionality (detect)

## What Changes

Tests should be added to ensure the functionality works properly

## Capabilities

### New Capabilities
<!-- None -->

### Modified Capabilities
- `delivery-preferences-ui`: Add explicit automated test coverage requirements for preferred tool detection outcomes used by the Tools & Delivery settings UI.

## Impact

- `src/main/java/com/johnnyb/openspec/settings/` and related detection/service classes — no behavior change, verification-focused test additions
- `src/test/java/**` — new and expanded unit/integration tests for detection and settings fallback behavior
- `src/test/resources/**` — reusable fixtures for detected, missing, and invalid settings inputs
