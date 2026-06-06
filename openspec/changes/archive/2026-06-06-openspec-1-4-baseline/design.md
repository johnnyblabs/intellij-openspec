## Context

The plugin's AI-tool detection and schema validation are point-in-time mirrors of the OpenSpec CLI registry. v0.2.10 aligned the plugin with CLI 1.3.x (28 tools, single `spec-driven` schema). The newly-installed CLI 1.4.1 adds two more tools (Kimi CLI, Mistral Vibe) and a second workflow schema (`workspace-planning`), with no shape changes to `.openspec.yaml` or `openspec/config.yaml`. A throwaway `openspec init` under 1.4.1 confirmed that required artifacts and config fields remain identical to V1_2, so no new `VersionSupport` enum entry is needed.

Antigravity (`antigravity`, `.agent`) is already in the plugin's three detection registries — it was added during the v0.2.10 sweep, not in the 1.4.x bump as my first reading of the diff suggested. The CLI 1.4 delta against the plugin is exactly Kimi + Vibe + workspace-planning + doc copy.

## Goals / Non-Goals

**Goals:**
- Plugin detects Kimi CLI and Mistral Vibe in the same way it detects the other 28 tools (directory scan, type classification, CLI-ID mapping for any CLI invocation issued by the plugin).
- Changes scaffolded under the new `workspace-planning` schema validate cleanly against `VersionSupport.V1_2.getValidSchemas()`.
- User-facing copy (README, CHANGELOG, wiki references) reflects 1.4.x and the 30-tool count.

**Non-Goals:**
- Surfacing the new CLI commands (`workspace`, `context-store`, `initiative`, `set`, `init --profile`) in the IDE — these are net-new feature areas, deferred to a follow-up change.
- Adding a `VersionSupport.V1_3` or `V1_4` enum entry — the 1.4.x init shape is identical to V1_2, and the prefix-match in `VersionSupport.fromString` already routes `1.4.x` config values to V1_2.
- Reorganizing tool guidance for the new tools beyond the existing `DEFAULT_GUIDANCE` fallback — tailored chat/paste copy can come later if either tool is observed in real projects.
- Touching `ConfigVersionValidationTest`'s V1_0 / V1_1 acceptance — `workspace-planning` is a 1.4.x addition and shouldn't be retroactively valid for older config baselines.

## Decisions

**Add Kimi and Vibe as `ToolType.CLI`.** Both ship as terminal-driven assistants in their upstream docs (Moonshot AI's Kimi CLI, Mistral's Vibe). That matches how other terminal-first tools (Claude Code, Gemini, Codex, OpenCode, ForgeCode, Bob Shell) are typed and means the existing CLI delivery path ("Paste into …") just works without new guidance entries. Alternative considered: classify as `IDE_PANEL` to be conservative. Rejected because the upstream `successLabel` strings and product framing for both are CLI-shaped, and the IDE_PANEL path would surface a worse delivery prompt ("Open Kimi CLI chat and paste the prompt") for what is in fact a terminal tool.

**Add bespoke `TOOL_GUIDANCE` entries for Kimi and Vibe** — mirroring the v0.2.10 pattern for ForgeCode / Bob Shell. Without entries, `getToolGuidance` falls through to `DEFAULT_GUIDANCE` (`chatPanelName: "your AI tool"`, `canAutoSave: false`), which produces two visible regressions in `WorkflowActionPanel`'s post-copy popover: the copy reads "Paste into your AI tool" instead of the tool's name, and — because `canAutoSave` is `false` — the IDE tells the user "Save tasks.md when done" instead of "watching tasks.md for progress…". The watch behavior is a real feature that CLI tools can use; defaulting it off is a functional regression, not just stylistic. The entries are: `new ToolGuidance("terminal", "Paste into Kimi CLI", null, true)` and `new ToolGuidance("terminal", "Paste into Mistral Vibe", null, true)`. Alternative considered (and originally chosen): skip the entries until usage patterns warrant tailored copy. Rejected because the v0.2.10 CHANGELOG explicitly closed the "generic fallback no longer appears" invariant for new CLI tools, and shipping without these entries silently reopens it.

**Attach `workspace-planning` only to `V1_2`.** The new schema was introduced in CLI 1.4.x, which corresponds to the V1_2 config baseline (per the 1.4.1 probe). Retroactively making it valid for V1_0 / V1_1 would mask legitimate validation errors — a project still on `version: 1.0.0` declaring `schema: workspace-planning` is mis-configured and should be flagged. Alternative considered: a new `V1_3` enum entry to scope the new schema. Rejected because the V1_2 → V1_3 delta is exactly "second valid schema" with no required-artifact or config-field changes, and the plugin's existing prefix-match in `fromString` would still send 1.4.x configs to V1_3 the same way it sends them to V1_2 today. A flat enum addition adds API surface for zero behavioral gain.

**Upstream registry comment stays.** The existing `// Upstream registry: @fission-ai/openspec/dist/core/config.js — cross-check on CLI bumps.` comment is exactly the prompt needed for the next bump; no documentation change required beyond the new entries themselves.

**Defer workspace/context-store/initiative IDE surfaces.** Each is a net-new feature area with its own UX surface (file tree categories, lifecycle dialogs, status reporting). Bundling them into the alignment change would dilute review focus and stretch scope. Tracker items for the deferred surfaces will be added when this change is archived so they don't get lost.

## Risks / Trade-offs

**Mis-classified tool type (Kimi or Vibe is actually IDE_PANEL).** → Mitigation: the only behavioral difference between CLI and IDE_PANEL is the paste-target copy. If either tool ships an IDE panel and a user reports mis-classified guidance, flipping the `TOOL_TYPES` entry is a one-line follow-up.

**Upstream renames Kimi's or Vibe's `skillsDir` before plugin release.** → Mitigation: cross-check `@fission-ai/openspec/dist/core/config.js` against the plugin's `TOOL_DIRS` map at release-prep time (the comment on line 51 already prompts this; release-prep skill can be updated to actually do the diff in a follow-up).

**A project on V1_0 / V1_1 adopts `workspace-planning` and gets a validation error.** → Expected, not a risk. This is the correct behavior: the new schema isn't supported under those older config baselines per upstream OpenSpec, so the validator surfacing the conflict is the right outcome. The error message already says "schema X not in valid schemas for version Y".

**Hidden 1.4.x shape change we missed in the throwaway-probe.** → Mitigation: the probe only ran `openspec init` and `openspec new change` non-interactively. If an interactive-mode init writes a different config.yaml or a 1.4-specific change scaffold field, we'd find out from real-world usage. Mitigation if it happens: a follow-up V1_3 enum addition is a localized change with no broader impact.

## Migration Plan

No migration. This is additive: existing projects on `spec-driven` see no change; projects newly using Kimi/Vibe or `workspace-planning` start working. Rollback is a single-commit revert.
