## Context

The plugin documentation (README, marketplace page, getting-started guide, feature matrix) was last comprehensively written for v0.1.0. Since then, v0.2.0–v0.2.3 and v0.3.0 Phase 1–3 have added ~15 new actions, 2 new tool window tabs, 2 new settings sections, and restructured the primary workflow from right-click menus to the Workflow Action Panel.

## Goals

- Every shipped feature appears in at least one doc file
- README serves as the canonical reference (marketplace page and guide link to it)
- Marketplace page focuses on selling points, not exhaustive reference
- Getting-started guide walks one complete workflow end-to-end
- Feature matrix stays factual and version-accurate

## Non-Goals

- Architecture documentation (internal code structure)
- API documentation for extension points
- Creating new doc files beyond the four identified

## Decisions

### 1. README structure

**Decision:** Reorganize README into: Quick Start, Features (grouped by category), Settings, Workflow Patterns, Menu Reference, Troubleshooting.

**Rationale:** The current README mixes workflow steps with feature descriptions. Grouping features by category (Workflow Actions, Spec Intelligence, AI Integration, Tool Window) makes it scannable. The Menu Reference table becomes the exhaustive list.

### 2. Marketplace page scope

**Decision:** Add Phase 3 features to the Key Features bullet list and update the "What It Does" section. Do NOT restructure the page layout.

**Rationale:** The marketplace page structure (What It Does → Who It's For → Key Features → What It Is Not) tested well. Just update content within the existing structure.

### 3. Getting-started guide workflow

**Decision:** Update the worked example to use the Workflow Action Panel (click Generate button, pipeline chips advance) instead of right-click context menus.

**Rationale:** The Workflow Action Panel is now the primary interaction model. The right-click workflow still works but is secondary.

### 4. Feature matrix version

**Decision:** Update to `0.2.3` (current tagged version), not `0.3.0-dev`.

**Rationale:** Document what's released, not what's in development. Phase 3 features on the dev branch aren't released yet.

## Risks

- **Marketplace page HTML formatting** — HTML must be valid for JetBrains Marketplace renderer. Mitigation: keep changes minimal, test rendering.
- **Feature matrix accuracy** — Competitor extensions may have updated. Mitigation: note "Last updated" date, don't re-research competitors in this change.
