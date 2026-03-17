## Why

The plugin has shipped v0.2.0 through v0.2.3 plus three phases of v0.3.0 features, but all user-facing documentation still describes the v0.1.0 feature set. The README is missing entire feature categories (Fast-Forward, Continue, Verify, Explore panel, Spec Sync, Bulk Archive, custom schemas, config profiles, CLI Update). The marketplace page lacks Phase 3 features. The getting-started guide references a right-click workflow that has been replaced by the Workflow Action Panel. The feature comparison matrix shows `0.1.0-dev`. New users and marketplace visitors see a fraction of what the plugin actually does.

## What Changes

- **README.md** — Rewrite to reflect v0.2.3 feature set:
  - Add workflow actions: Fast-Forward, Continue, Verify, Explore, Sync Specs, Bulk Archive, Update
  - Document Workflow Action Panel (pipeline chips, Generate button, change selector)
  - Document all tool window tabs (Browse, Coverage, Console, Explore)
  - Update Settings section: add Config Profile and Schemas sections, update AI config for Gemini
  - Document delivery methods clearly (Clipboard, Editor Tab, Direct API)
  - Update Menu Reference with all new actions
  - Update Troubleshooting for new features
- **docs/marketplace-page.md** — Add Phase 3 features to Description HTML (custom schemas, explore tab, config management, CLI update). Update Key Features list. Update screenshots guidance for new panels.
- **docs/feature-comparison-matrix.md** — Update plugin version from `0.1.0-dev` to `0.2.3`. Add rows for new features (Fast-Forward, Continue, Verify, Explore, Spec Sync, Bulk Archive, custom schemas, config profiles, CLI update). Update Gap Analysis.
- **docs/getting-started-copilot.md** — Update worked example to use Workflow Action Panel instead of right-click. Update settings walkthrough for new sections. Update "Last verified" date.

## Capabilities

### New Capabilities

- `documentation`: Requirements for keeping user-facing docs accurate and synchronized with the plugin's shipped feature set.

### Modified Capabilities

_(none — documentation only, no spec-level behavior changes)_

## Impact

- **Files**: `README.md`, `docs/marketplace-page.md`, `docs/feature-comparison-matrix.md`, `docs/getting-started-copilot.md`
- **Dependencies**: None — docs-only change
- **Systems**: JetBrains Marketplace listing, Forgejo README
- **Risk**: Low — no code changes, fully reversible
