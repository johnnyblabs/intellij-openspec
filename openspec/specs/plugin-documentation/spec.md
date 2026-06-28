# Plugin Documentation

## Purpose
User-facing documentation organized around four personas (Spec Browser, IDE-First Developer, CLI Companion, Standalone API User) with per-persona getting-started guides and a canonical feature reference.
## Requirements
### Requirement: Documentation defines four user personas
The project documentation SHALL define exactly four user personas that represent the distinct ways the plugin is used: Spec Browser, IDE-First Developer, CLI Companion, and Standalone API User. Each persona definition SHALL include the persona's primary value proposition, whether AI setup is required, and which plugin features are most relevant.

#### Scenario: All four personas are defined
- **WHEN** a user reads the project README
- **THEN** the README SHALL present a persona table containing all four personas (Spec Browser, IDE-First Developer, CLI Companion, Standalone API User) with a brief description and link to each persona's getting-started guide

#### Scenario: Personas differentiate by AI setup requirement
- **WHEN** a user compares the four personas
- **THEN** the documentation SHALL make clear that Spec Browser requires no AI setup, IDE-First Developer and CLI Companion use the user's existing AI tool, and Standalone API User requires only an API key

### Requirement: Per-persona getting-started guide exists
The project SHALL provide a dedicated getting-started guide for each of the four personas. Each guide SHALL include installation steps, persona-specific configuration, and a concrete end-to-end workflow example demonstrating the plugin's value for that persona.

#### Scenario: Spec Browser guide covers zero-AI-setup workflow
- **WHEN** a reviewer or team lead reads `docs/getting-started-browser.md`
- **THEN** the guide SHALL walk through installing the plugin, opening the tool window, browsing the spec tree, viewing coverage, and navigating via gutter markers — with no AI configuration steps

#### Scenario: IDE-First Developer guide covers clipboard delivery workflow
- **WHEN** a Copilot/Cursor user reads the IDE-first getting-started guide
- **THEN** the guide SHALL walk through selecting clipboard delivery mode, proposing a change, generating an artifact, and pasting the clipboard content into their AI tool's chat panel

#### Scenario: CLI Companion guide covers dashboard-alongside-terminal workflow
- **WHEN** a Claude Code or Gemini CLI user reads `docs/getting-started-cli-companion.md`
- **THEN** the guide SHALL walk through using the plugin as a spec browser and coverage tracker while running AI commands in the terminal, including how copy prompts include save-path hints

#### Scenario: Standalone API User guide covers Direct API workflow
- **WHEN** an API-key user reads `docs/getting-started-api.md`
- **THEN** the guide SHALL walk through configuring a Direct API provider (Claude, OpenAI, or Gemini), storing credentials, and using Fast-Forward to create a change with all artifacts generated in one click

### Requirement: Canonical feature reference exists
The project SHALL maintain a single canonical feature reference document (`docs/feature-reference.md`) that describes all plugin features organized by functional area. Persona getting-started guides SHALL link to the feature reference for detailed descriptions rather than duplicating feature content.

#### Scenario: Feature reference covers all functional areas
- **WHEN** a user reads `docs/feature-reference.md`
- **THEN** the document SHALL contain sections for: Browsing & Navigation, Workflow Orchestration, AI Integration & Delivery, Editor Integration, Validation, and Settings & Configuration

#### Scenario: Getting-started guides link to feature reference
- **WHEN** a persona getting-started guide mentions a plugin feature
- **THEN** the guide SHALL link to the corresponding section in `docs/feature-reference.md` rather than providing a full standalone description of that feature

### Requirement: README serves as landing page
The project README SHALL function as a concise landing page that enables a new user to determine within 30 seconds whether the plugin is relevant to them and how to proceed. The README SHALL NOT contain exhaustive feature documentation — it SHALL link to the feature reference and persona guides instead.

#### Scenario: README contains persona selection table
- **WHEN** a new user opens the project README
- **THEN** the README SHALL display a persona table within the first screenful of content, with each row containing the persona name, a one-sentence value proposition, and a link to the corresponding getting-started guide

#### Scenario: README includes essential information only
- **WHEN** a user reads the README end-to-end
- **THEN** the README SHALL contain only: a one-paragraph plugin description, the persona table, installation instructions, links to getting-started guides, links to marketplace and OpenSpec framework, and a link to the feature reference — no menu reference, no troubleshooting, no exhaustive settings documentation

### Requirement: Marketplace page leads with persona value propositions
The marketplace page content (`docs/marketplace-page.md`) SHALL lead with persona-based framing that communicates "four ways to use this plugin" before listing features. The "Who It's For" section SHALL appear before or integrated with the "What It Does" section.

#### Scenario: Marketplace description opens with value framing
- **WHEN** a user views the JetBrains Marketplace plugin page
- **THEN** the description SHALL present the persona/use-case framing within the first paragraph or section, before any feature bullet lists

#### Scenario: Marketplace content aligns with documentation personas
- **WHEN** comparing the marketplace page personas with the README personas
- **THEN** the same four personas SHALL appear in both, using consistent naming and descriptions

### Requirement: Persona guides cross-link to other personas
Each persona getting-started guide SHALL end with a "You might also want to explore" section that links to other relevant persona guides, acknowledging that users may span multiple personas.

#### Scenario: Cross-links are contextually relevant
- **WHEN** a user finishes the Spec Browser getting-started guide
- **THEN** the guide SHALL suggest the IDE-First Developer or CLI Companion guide as a next step for users who want to start generating artifacts, not just browsing

#### Scenario: Cross-links exist in all guides
- **WHEN** reviewing any of the four persona getting-started guides
- **THEN** each guide SHALL contain at least one cross-link to another persona guide with a brief explanation of when that other guide would be relevant

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

### Requirement: OpenSpec client coverage matrix is published

The project SHALL publish and maintain a coverage matrix at `docs/openspec-support.md` that maps OpenSpec client capabilities to the plugin's support status, with each capability annotated by the minimum CLI version it requires. The matrix SHALL distinguish supported, partial, divergent, planned, and plugin-original capabilities, and SHALL state the plugin's CLI-version support contract (minimum supported version, baseline, and runtime version-awareness).

#### Scenario: Matrix reflects client coverage with version annotations
- **WHEN** a user reads `docs/openspec-support.md`
- **THEN** it SHALL present OpenSpec client capabilities grouped by area, each with a support status and a CLI-version annotation
- **AND** it SHALL state the minimum supported CLI version and that behavior degrades gracefully below it

#### Scenario: README links to the coverage matrix
- **WHEN** a user reads the README
- **THEN** the README SHALL link to the OpenSpec client coverage matrix

#### Scenario: Coverage matrix stays vendor-neutral on the public mirror
- **WHEN** the coverage matrix or its accompanying change artifacts are published to the public mirror
- **THEN** they SHALL reference only public identifiers (OpenSpec change names, CHANGELOG versions)
- **AND** they SHALL NOT contain internal tracker identifiers or environment-specific references

