## ADDED Requirements

### Requirement: Workflow profiles documentation page is published

The `Workflow-Profiles.md` documentation SHALL be committed to the project's `main` branch on GitHub at `scripts/docs/wiki/Workflow-Profiles.md` so that the plugin's documentation links resolve to a live page rather than returning 404. The page SHALL avoid enumerating specific workflow names except where explicitly maintained as the canonical source — wiki content is the single point at which workflow lists may be enumerated, since the plugin code intentionally does not.

#### Scenario: Docs URL resolves
- **WHEN** the user clicks "About profiles…" in the status bar widget popup or "Read the full guide" in the Settings panel ContextHelpLabel
- **THEN** the link SHALL open a live, fully rendered documentation page (HTTP 200) and not return 404

#### Scenario: Page covers the three-way semantic split
- **WHEN** a user reads the workflow profiles documentation page
- **THEN** the page SHALL distinguish between schema (e.g., `spec-driven`), project profile (config.yaml `profile:` block name/description/language metadata), and workflow profile (global CLI config — `core` or a custom workflow set)

#### Scenario: Page covers the two-step profile change process
- **WHEN** a user reads the workflow profiles documentation page
- **THEN** the page SHALL describe the two-step OpenSpec profile change process: first run `openspec config profile <preset>` (or use Customize workflows… to launch the interactive picker) to switch the workflow set, then run `openspec update` to install the corresponding skills for the user's AI tools

#### Scenario: Page reflects D2 — combo only lists CLI presets
- **WHEN** the page describes the Settings panel workflow profile combo
- **THEN** the page SHALL accurately reflect that the combo lists only CLI-accepted presets (default and `core`) and that the path to a non-preset workflow set is via the "Customize workflows…" button — the page SHALL NOT describe `custom` as a third combo entry the user can pick directly
