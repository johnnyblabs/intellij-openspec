## Context

JetBrains tool window strip icons (the sidebar icons) are monochrome by convention — dark gray on light theme, light gray on dark theme. Colored icons in the strip look non-native. The pluginIcon (marketplace) and tree view icons can remain colored.

The Anthropic API version controls which API behaviors are active. Older versions may miss improvements or trigger deprecation warnings. The OpenAI o1-series models use a different parameter name for token limits.

## Goals / Non-Goals

**Goals:**
- Monochrome tool window icon matching JetBrains convention
- Current Anthropic API version
- Working o1-mini model selection

**Non-Goals:**
- Changing tree view icons (those stay colored — they're inside the panel)
- Changing the marketplace pluginIcon (stays branded/colored)
- Changing the brand icon (stays colored — used in onboarding panels)

## Decisions

### Monochrome tool window icon colors

- Light theme: `#6E6E6E` (matches IntelliJ's built-in tool window icons)
- Dark theme: `#AFB1B3` (matches IntelliJ's dark theme strip icons)
- Same document+checkmark shape, just desaturated

### Conditional max_tokens for OpenAI

Check if the model name starts with `o1` — if so, use `max_completion_tokens` instead of `max_tokens` in the request body.

## Risks / Trade-offs

- **[None]** All changes are convention/correctness fixes.
