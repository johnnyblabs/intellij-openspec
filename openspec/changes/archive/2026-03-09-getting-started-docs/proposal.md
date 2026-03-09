## Why

The plugin has no user-facing tutorial that walks a developer through the complete OpenSpec + Copilot workflow end-to-end. The README covers feature inventory but not the *experience* — a new user can't answer "how do I actually use this?" without reading source code. A getting-started guide in `docs/` that pairs plugin settings walkthrough with a concrete example (propose → generate → apply → archive) will dramatically reduce onboarding friction.

## What Changes

- Add `docs/getting-started-copilot.md` — a comprehensive tutorial for using the OpenSpec plugin with GitHub Copilot as the AI tool
- Covers plugin installation, settings configuration, CLI setup, and a full worked example creating a change from scratch
- Clearly marks where OpenSpec drives the workflow vs. where AI (Copilot) generates content
- Includes annotated screenshots placeholders for key UI moments (settings panel, tool window, pipeline chips)

## Capabilities

### New Capabilities
- `getting-started-guide`: Documentation artifact covering plugin settings, OpenSpec concepts, and a complete worked example with GitHub Copilot

### Modified Capabilities

## Impact

- New file: `docs/getting-started-copilot.md`
- No code changes — documentation only
- No API or dependency changes
