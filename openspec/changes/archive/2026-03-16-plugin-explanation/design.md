## Context

The OpenSpec IntelliJ plugin (v0.2.3, plugin ID 30678) has grown into a full-featured IDE client with 20+ actions, 14 project-level services, and multiple delivery modes — but its documentation doesn't clearly articulate *what it is* and *who it's for*. The proposal raises the right question: the plugin serves multiple distinct audiences and use-cases, and we need a clear assessment of these things for potential users.

Current documentation exists across several files (`README.md`, `docs/marketplace-page.md`, `docs/getting-started-copilot.md`, `docs/feature-comparison-matrix.md`) but focuses on feature lists and setup steps. What's missing is a coherent narrative that helps a new user quickly understand: "Is this plugin for me, and how would I use it?"

The marketplace page already has a "Who It's For" table (CLI-native devs, IDE-first devs, API-key users, Reviewers & leads) — this is the right framing but needs to be expanded into proper documentation with concrete workflows.

**Stakeholders:** Plugin users discovering it via JetBrains Marketplace, existing OpenSpec CLI users, teams evaluating spec-driven development tooling.

## Goals / Non-Goals

**Goals:**
- Define the plugin's identity through its distinct usage personas, not just feature lists
- Create documentation that answers "what is this and why should I care?" for each persona
- Update existing docs (README, marketplace page) to lead with value propositions
- Provide concrete workflow examples for each persona so users can self-identify and get started fast

**Non-Goals:**
- No code changes — this is purely documentation
- Not creating API reference docs or Javadoc
- Not redesigning the plugin's UI or onboarding wizard
- Not writing tutorials for the OpenSpec framework itself (that's Fission AI's responsibility)
- Not adding in-IDE help panels or documentation browser features

## Decisions

### 1. Frame the plugin around four personas, not feature categories

**Decision:** Organize all user-facing documentation around persona-based narratives rather than feature-grouped lists.

**Rationale:** The proposal correctly identifies that the plugin serves multiple purposes. A feature list says "we have gutter markers" but doesn't tell you whether you need them. Persona framing says "if you're a team lead who reviews specs, here's what you get without any AI setup." This matches how the marketplace page already segments users but extends it into full documentation.

**The four personas:**

| Persona | Primary value | AI setup required? | Key features |
|---------|--------------|-------------------|--------------|
| **Spec Browser** — Reviewers, leads, PMs | Read-only spec navigation and coverage visibility | None | Tree view, search, coverage panel, gutter markers, inspections |
| **IDE-First Developer** — Uses Copilot, Cursor, Windsurf, or Cline | Workflow orchestration with clipboard delivery to IDE-panel AI tools | None (uses existing AI tool) | Propose, Generate (clipboard mode), pipeline chips, tool-specific guidance, Explore context |
| **CLI Companion** — Uses Claude Code, Gemini CLI, or other terminal AI | Spec dashboard alongside terminal workflow | None (uses existing CLI tool) | Browse, coverage, copy prompts with save-path hints, validation, CLI delegation |
| **Standalone API User** — Has API key, no CLI, no IDE AI tool | Complete self-contained workflow via Direct API | API key only | Fast-Forward, Generate All, Direct API (Claude/OpenAI/Gemini), credential storage, full lifecycle |

**Alternatives considered:**
- *Feature-category organization* (current approach): Easier to write but harder for users to navigate. Rejected because users think in terms of "what can I do?" not "what modules exist?"
- *Single linear getting-started guide*: Too long for users who only need a subset. Rejected in favor of persona entry points that converge on shared reference material.

### 2. Restructure docs/ with persona entry points and shared reference

**Decision:** Create per-persona getting-started guides that link to a shared feature reference, rather than duplicating feature descriptions.

**Proposed structure:**
```
docs/
├── README.md                          # Overview: what is this, four personas, quick links
├── marketplace-page.md                # (existing, update to match persona framing)
├── feature-comparison-matrix.md       # (existing, no changes needed)
├── getting-started-copilot.md         # (existing, rename/update as IDE-first guide)
├── getting-started-api.md             # New: standalone API user walkthrough
├── getting-started-cli-companion.md   # New: CLI companion walkthrough
├── getting-started-browser.md         # New: spec browser / reviewer walkthrough
└── feature-reference.md               # New: canonical feature list (single source of truth)
```

**Rationale:** Each persona needs different setup steps and highlights different features. Per-persona guides are short (1-2 pages) and link to the shared feature reference for details. This avoids the current problem where `README.md` tries to be everything and ends up being overwhelming.

**Alternatives considered:**
- *Single README with persona sections*: Gets too long and users skip to their section anyway. Rejected.
- *Wiki-style interlinked pages*: Over-engineered for 4 personas. Rejected.

### 3. Update README.md to be a landing page, not an exhaustive guide

**Decision:** Slim down `README.md` to a concise landing page: one-paragraph description, persona table with links to guides, installation, and quick links to marketplace/OpenSpec repo.

**Rationale:** The current README is ~500 lines covering everything from troubleshooting to menu reference. This works for maintainers but overwhelms new users. A landing page pattern lets users self-select into the right guide within 30 seconds.

**Alternatives considered:**
- *Keep README comprehensive, add persona guides alongside*: Creates maintenance burden with duplicated content. Rejected.
- *Move everything to a docs site*: Premature — the plugin doesn't have enough users yet to justify a hosted docs site. Rejected for now.

### 4. Lead marketplace description with the "four ways to use it" framing

**Decision:** Update `docs/marketplace-page.md` to lead with the persona value propositions and consolidate the "What It Does" and "Who It's For" sections into a single coherent narrative.

**Rationale:** The current marketplace page has good content but presents features first and personas second. Marketplace visitors decide in ~10 seconds whether to install. Leading with "here's how you'd use it" converts better than leading with "here's what it has."

**Alternatives considered:**
- *Keep current structure*: Already decent but buries the "Who It's For" table below feature lists. Minor restructure is low-risk.

### 5. Extract a canonical feature reference from README

**Decision:** Create `docs/feature-reference.md` as the single source of truth for feature descriptions, organized by functional area (browsing, workflow, AI integration, editor integration, validation, settings).

**Rationale:** Currently, feature descriptions are duplicated across README, marketplace page, and getting-started guides. A canonical reference prevents drift and gives persona guides a stable target to link to.

## Risks / Trade-offs

**[Documentation drift]** → Multiple persona guides could diverge from actual plugin behavior as features evolve. **Mitigation:** Feature reference is the single source of truth; persona guides link to it rather than duplicating descriptions. Add a note in CLAUDE.md or a change convention that feature changes should update `feature-reference.md`.

**[Persona boundaries aren't rigid]** → Users may span personas (e.g., a CLI user who also uses Direct API). **Mitigation:** Persona guides are entry points, not silos. Each guide ends with "you might also want to explore..." cross-links.

**[Over-documenting for current user base]** → The plugin is early-stage (marketplace ID 30678, recently listed). Creating four getting-started guides may be premature. **Mitigation:** Keep guides concise (1-2 pages each). The writing effort is modest and the clarity benefit is immediate for marketplace conversion.

**[Naming: "explanation" vs feature docs]** → This change is called "plugin-explaination" but it's really about restructuring user-facing documentation. **Mitigation:** The change name is fine for internal tracking; the output artifacts are properly named docs.
