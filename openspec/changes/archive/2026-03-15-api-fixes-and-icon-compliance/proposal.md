## Why

Two API compliance issues and one icon convention issue found during pre-review audit:
1. Anthropic API version header is `2023-06-01` — nearly 3 years old, should be `2024-06-01`
2. OpenAI `o1-mini` model receives `max_tokens` but requires `max_completion_tokens` — causes API errors
3. Tool window strip icon uses colored purple instead of JetBrains-standard monochrome gray/white

## What Changes

- Update Anthropic API version header to `2024-06-01`
- Use `max_completion_tokens` for OpenAI o1-series models
- Create monochrome tool window icons (`openspec.svg` / `openspec_dark.svg`) following JetBrains convention: dark gray for light theme, light gray for dark theme

## Capabilities

### New Capabilities
_None_

### Modified Capabilities
_None — bug fixes and convention compliance_

## Impact

- `DirectApiService.java` — API header and parameter fixes
- `icons/openspec.svg` — monochrome for tool window strip
- `icons/openspec_dark.svg` — monochrome for dark theme strip
