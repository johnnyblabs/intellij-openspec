## 1. Detection registry — add Kimi CLI and Mistral Vibe

- [x] 1.1 Add `.kimi` → `"Kimi CLI"` to `TOOL_DIRS` in `AiToolDetectionService.java` (preserve `LinkedHashMap` ordering — group with other 1.4.x additions near the end)
- [x] 1.2 Add `.vibe` → `"Mistral Vibe"` to `TOOL_DIRS` in `AiToolDetectionService.java`
- [x] 1.3 Add `"Kimi CLI"` → `ToolType.CLI` to `TOOL_TYPES`
- [x] 1.4 Add `"Mistral Vibe"` → `ToolType.CLI` to `TOOL_TYPES`
- [x] 1.5 Add `"Kimi CLI"` → `"kimi"` to `CLI_TOOL_IDS`
- [x] 1.6 Add `"Mistral Vibe"` → `"vibe"` to `CLI_TOOL_IDS`
- [x] 1.7 Verify no bespoke `TOOL_GUIDANCE` entries are added for Kimi or Vibe — they intentionally fall through to `DEFAULT_GUIDANCE` (per design.md, decision: "No bespoke TOOL_GUIDANCE entries")

## 2. Detection-service test alignment

- [x] 2.1 Update `AiToolDetectionServiceTest.getAllToolNames_returns28Tools()` (line 210) — rename to `…returns30Tools()` and bump assertion from `28` to `30`
- [x] 2.2 Update `AiToolDetectionServiceTest` line 303 inline comment from "OpenSpec 1.3.x" to "OpenSpec 1.4.x" and the assertion message from "Should have 28 tools" to "Should have 30 tools"
- [x] 2.3 Add a test scenario covering `.kimi` directory → `"Kimi CLI"` detection (CLI type, CLI ID `kimi`)
- [x] 2.4 Add a test scenario covering `.vibe` directory → `"Mistral Vibe"` detection (CLI type, CLI ID `vibe`)

## 3. Schema acceptance — workspace-planning under V1_2

- [x] 3.1 In `VersionSupport.java`, change `V1_2`'s `validSchemas` from `Set.of("spec-driven")` to `Set.of("spec-driven", "workspace-planning")`
- [x] 3.2 Leave `V1_0` and `V1_1` `validSchemas` unchanged (still `Set.of("spec-driven")`)
- [x] 3.3 Add a test to `VersionSupportTest` asserting `V1_2.getValidSchemas()` contains both `spec-driven` and `workspace-planning`
- [x] 3.4 Add a test to `VersionSupportTest` asserting `V1_0.getValidSchemas()` and `V1_1.getValidSchemas()` do NOT contain `workspace-planning`
- [x] 3.5 Add a test to `ConfigVersionValidationTest` asserting `validateChangeSchema("workspace-planning", VersionSupport.V1_2)` returns no issues
- [x] 3.6 Add a test to `ConfigVersionValidationTest` asserting `validateChangeSchema("workspace-planning", VersionSupport.V1_0)` returns the `change-schema-incompatible` warning
- [x] 3.7 Update `ScaffoldingContractTest` if it iterates over `getValidSchemas()` — the larger set may surface a stale assumption (run the test and fix only if it fails) — only uses `contains()`, still passes with larger set; no change needed

## 4. User-facing copy

- [x] 4.1 Update `README.md` line 35: "detection of all 28 supported AI tools" → "30", and "OpenSpec 1.3.x or later" → "OpenSpec 1.4.x or later"
- [x] 4.2 Add a new top entry to `CHANGELOG.md` for the next release: "Added — Detection for two AI tools introduced in OpenSpec CLI 1.4.0 — Kimi CLI (Moonshot AI) and Mistral Vibe. Supported tool count expands from 28 to 30." and "Added — `workspace-planning` workflow schema is accepted as valid under V1_2 config baseline." (Do NOT include internal housekeeping per [[feedback_changelog_scope]].) — used `## Unreleased — OpenSpec 1.4 Baseline` heading; release-prep can rename to the chosen version
- [x] 4.3 Search `scripts/docs/wiki/` for any "1.3.x" or "28 supported" references and update them; if none present, note the absence in the task closeout. — no matches found in wiki, nothing to update
- [x] 4.4 Leave the existing `## v0.2.10 — Windows Support & OpenSpec 1.3 Tools` CHANGELOG entry intact — it correctly records the 1.3.x alignment shipped in that release.

## 5. Verification

- [x] 5.1 Run `./gradlew test` — all existing tests pass, new tests pass — BUILD SUCCESSFUL in 27s
- [ ] 5.2 Spot-check by creating a throwaway `.kimi/` directory in a sandbox project and confirming the IDE detection picks it up — requires running IDE; deferring to manual smoke test
- [ ] 5.3 Spot-check by creating a throwaway change with `.openspec.yaml` set to `schema: workspace-planning` in a V1_2 project and confirming no `change-schema-incompatible` warning appears — requires running IDE; deferring to manual smoke test
- [x] 5.4 Cross-check `TOOL_DIRS` against the current `node_modules/@fission-ai/openspec/dist/core/config.js` to confirm the 30-entry plugin set matches the upstream `available: true` set exactly (Kimi, Vibe, plus the 28 already present) — automated diff: 30/30 plugin entries match upstream skillsDir, no extras either direction
- [x] 5.5 Run `openspec validate openspec-1-4-baseline --strict` — change validates cleanly post-implementation

## 6. Out of scope — capture as follow-up

- [x] 6.1 Open a follow-up OpenSpec change (or tracker note) for the deferred IDE surfaces: `workspace` / `context-store` / `initiative` commands, and the `init --profile` flag. Capture only as a tracker item or skeleton change — do NOT design or implement here. — a tracker entry
