## Why

The plugin has grown to 20+ actions and 14 services, but its documentation doesn't clearly answer "what is this and why should I care?" for the different audiences who would use it. The plugin serves four distinct purposes:

- **Spec browser** — read-only navigation and coverage tracking for reviewers, leads, and PMs with zero AI setup
- **IDE-first workflow tool** — clipboard delivery to Copilot, Cursor, Windsurf, or Cline for developers who already use an IDE-based AI tool
- **CLI companion dashboard** — spec browsing and prompt copying with save-path hints alongside Claude Code, Gemini CLI, or other terminal AI
- **Standalone API client** — complete self-contained workflow via Direct API (Claude, OpenAI, Gemini) for developers with an API key but no external AI tool

Current docs (README, marketplace page, getting-started-copilot guide) focus on feature lists and setup steps but don't help a new user self-identify which of these four use-cases matches their situation.

## What Changes

- Restructure user-facing documentation around four persona-based getting-started guides
- Create a canonical feature reference (`docs/feature-reference.md`) as the single source of truth for all feature descriptions
- Slim the README to a concise landing page with a persona selection table linking to the guides
- Update the marketplace page to lead with persona value propositions before feature lists
- Add cross-links between all guides so users who span personas can discover adjacent workflows

No code changes — this is purely documentation.

## Capabilities

### New Capabilities
- `plugin-documentation`: persona-based documentation structure with canonical feature reference, per-persona getting-started guides, README landing page, and marketplace alignment

### Modified Capabilities

## Impact

- Affected files: `README.md`, `docs/marketplace-page.md`, `docs/getting-started-copilot.md`
- New files: `docs/feature-reference.md`, `docs/getting-started-browser.md`, `docs/getting-started-cli-companion.md`, `docs/getting-started-api.md`
- No code, API, or dependency changes
