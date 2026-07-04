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

The project SHALL publish and maintain a coverage matrix at `docs/openspec-support.md` that maps OpenSpec client capabilities to the plugin's support status, with each capability annotated by the minimum CLI version it requires. The matrix SHALL distinguish supported, partial, divergent, planned, and plugin-original capabilities, and SHALL state the plugin's CLI-version support contract (minimum supported version, baseline, and runtime version-awareness). Support-mechanism classifications (delegated to the CLI, built-in, or surfaced indirectly) SHALL reflect the verified behavior of the code they describe; a claim that a capability is delegated when the implementation is built-in (or vice versa) is a documentation defect.

#### Scenario: Matrix reflects client coverage with version annotations
- **WHEN** a user reads `docs/openspec-support.md`
- **THEN** it SHALL present OpenSpec client capabilities grouped by area, each with a support status and a CLI-version annotation
- **AND** it SHALL state the minimum supported CLI version and that behavior degrades gracefully below it

#### Scenario: Mechanism classification matches code behavior
- **WHEN** the matrix classifies a capability's support mechanism (delegated / built-in / indirect)
- **THEN** that classification SHALL match how the plugin actually implements the capability, verified against the code path rather than assumed

#### Scenario: README links to the coverage matrix
- **WHEN** a user reads the README
- **THEN** the README SHALL link to the OpenSpec client coverage matrix

#### Scenario: Coverage matrix stays vendor-neutral on the public mirror
- **WHEN** the coverage matrix or its accompanying change artifacts are published to the public mirror
- **THEN** they SHALL reference only public identifiers (OpenSpec change names, CHANGELOG versions)
- **AND** they SHALL NOT contain internal tracker identifiers or environment-specific references

### Requirement: Every document declares a maintenance class

Every tracked documentation file — each doc under `docs/` and the top-level README, CHANGELOG, and CONTRIBUTING — SHALL declare a maintenance class near the top of the file, one of: **Living** (updated as part of every relevant change), **Snapshot** (reviewed on a stated cadence and may lag between reviews), **Reference** (stable; updated only when the thing it describes changes), or **Retired** (kept for history, explicitly not maintained). The class makes each document's update contract explicit and auditable.

#### Scenario: Each doc carries a recognized maintenance label
- **WHEN** any tracked documentation file is read
- **THEN** it SHALL contain a `Maintenance:` label naming exactly one of the four classes (Living, Snapshot, Reference, Retired)

#### Scenario: A doc without a maintenance label is a hygiene failure
- **WHEN** a documentation-hygiene check scans the tracked docs
- **THEN** it SHALL fail if any doc lacks a `Maintenance:` label or uses a class outside the defined set

### Requirement: A documentation index maps every doc

The project SHALL maintain a documentation index at `docs/README.md` that lists every tracked documentation file with its purpose, primary audience, and maintenance class, and defines the four maintenance classes. The index SHALL disambiguate documents with similar names (in particular the version coverage matrix versus the competitive comparison matrix). The top-level README SHALL link to the index.

#### Scenario: The index covers every doc with no orphans
- **WHEN** the documentation index is compared to the files present under `docs/`
- **THEN** every doc file SHALL have an index entry and every index entry SHALL point to an existing doc

#### Scenario: The index disambiguates the two matrices
- **WHEN** a reader consults the index to find "the matrix"
- **THEN** the index SHALL distinguish the version/coverage matrix (`openspec-support.md`) from the competitive comparison matrix (`feature-comparison-matrix.md`) by purpose

### Requirement: Version facts have a single source of truth

The project SHALL designate one canonical statement of version/support facts — the "Version support" block in `docs/openspec-support.md` (current plugin version, minimum/baseline/supported CLI versions). Other documentation SHALL link to that canonical statement rather than restating specific version numbers, except where a version is intrinsic to the document (such as a CHANGELOG release heading).

#### Scenario: Other docs link rather than restate
- **WHEN** a doc other than the canonical source needs to refer to the supported CLI range or current plugin version
- **THEN** it SHALL link to the canonical Version support block rather than hard-coding the numbers

#### Scenario: A drifted version restatement is a hygiene failure
- **WHEN** a documentation-hygiene check scans for hard-coded plugin/CLI version literals outside the canonical source and allowed exceptions
- **THEN** it SHALL fail if a doc reintroduces a version literal that has drifted from the canonical source

### Requirement: Repo markdown is the canonical documentation tier

The project SHALL treat repository markdown as the canonical documentation source. Any published mirror of the documentation (a project wiki, a team knowledge base) is a generated mirror of the repo markdown and SHALL NOT be hand-edited to diverge from it. The documentation index SHALL state this tiering.

#### Scenario: Canonicality is stated
- **WHEN** a contributor reads the documentation index
- **THEN** it SHALL state that repo markdown is canonical and that wiki/knowledge-base copies are published mirrors that must not diverge

### Requirement: Release preparation enforces documentation currency

The release readiness process SHALL check documentation currency before a release is cut: it SHALL flag any `Snapshot` doc whose most recent change predates the previous release, and it SHALL warn when the release diff changed a tracked code area without a corresponding change to its declared Living doc. These are surfaced as release-readiness findings, not silent omissions.

#### Scenario: Stale Snapshot doc is flagged at release time
- **WHEN** release preparation runs and a `Snapshot` doc has not changed since before the previous release
- **THEN** the process SHALL surface it as a currency finding for review

#### Scenario: Living doc not updated with its code is warned
- **WHEN** a release diff changes a tracked code area that maps to a Living doc, and that doc was not changed in the same release window
- **THEN** the process SHALL warn that the Living doc may be stale

### Requirement: Per-CLI-version feature-delta analysis grounds support decisions

Before or as part of adding plugin support for an OpenSpec CLI version, the plugin SHALL produce a **per-version feature-delta & plugin-impact analysis** — a durable document under `docs/cli-versions/` cataloguing the features **introduced, modified, deprecated, and removed** in that CLI version relative to the prior supported version. This analysis is the epistemic base of the capability-preservation contract: the plugin cannot faithfully mirror a client version it has not analysed.

The analysis SHALL:
- **Cite upstream.** Every claim about the CLI's behavior SHALL be cited to Fission's upstream OpenSpec documentation (changelog, releases, or docs). Claims that cannot be verified upstream SHALL be quarantined in an explicit "open questions / to verify" section and SHALL NOT be asserted as fact.
- **Assess plugin impact per feature.** For each feature it SHALL record the practical mechanics, an assessment of whether the plugin can and should surface it (including whether a plugin UI component exists or is warranted), and a consumer how-to (through the plugin, or via the CLI where there is no UI surface).
- **Drive decisions.** Plugin support decisions for that CLI version (what to build, what to leave CLI-only, what to gate) SHALL reference this analysis rather than be made from assumption.
- **Stay current.** It SHALL be updated when the plugin's understanding of that version changes, and carry a maintenance label per the documentation-maintenance framework.

The analysis is produced via an OpenSpec **explore** (research) pass over the upstream documentation. A `docs/cli-versions/` index SHALL list the per-version analyses.

#### Scenario: Analysis precedes support for a CLI version
- **WHEN** the plugin adds or changes support for an OpenSpec CLI version
- **THEN** a per-version feature-delta analysis for that version SHALL exist under `docs/cli-versions/`, and the implementing change SHALL reference it

#### Scenario: Upstream claims are cited; unverified claims are quarantined
- **WHEN** the analysis states a fact about the CLI's behavior in a version
- **THEN** that fact SHALL carry a citation to upstream OpenSpec documentation, and any claim not verifiable upstream SHALL appear only under an "open questions / to verify" section, not as an asserted fact

#### Scenario: Each feature carries a supportability and UI assessment
- **WHEN** a feature appears in the analysis
- **THEN** it SHALL record whether the plugin supports it, how (built-in / delegated / read-only / n-a), whether a plugin UI component exists or is warranted, and a consumer how-to

#### Scenario: Decisions reference the analysis
- **WHEN** a plugin change decides to build, defer, or gate support for a CLI-version feature
- **THEN** the decision SHALL be traceable to the per-version analysis rather than to assumption

