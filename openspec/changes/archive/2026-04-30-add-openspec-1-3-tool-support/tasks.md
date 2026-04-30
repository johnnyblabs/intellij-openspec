## 1. AiToolDetectionService registry

- [x] 1.1 Add `.junie → "Junie"`, `.lingma → "Lingma"`, `.forge → "ForgeCode"`, `.bob → "Bob Shell"` entries to `TOOL_DIRS` (preserve insertion order — append after `.trae`).
- [x] 1.2 Add `"Junie" → IDE_PANEL`, `"Lingma" → IDE_PANEL`, `"ForgeCode" → CLI`, `"Bob Shell" → CLI` entries to `TOOL_TYPES`.
- [x] 1.3 Add `"Junie" → "junie"`, `"Lingma" → "lingma"`, `"ForgeCode" → "forgecode"`, `"Bob Shell" → "bob"` entries to `CLI_TOOL_IDS`.
- [x] 1.4 Add a one-line comment near `TOOL_DIRS` pointing to `@fission-ai/openspec/dist/core/config.js` as the upstream registry source so future drift can be cross-checked. Mention the `.forge`/`forgecode` divergence inline next to the ForgeCode entry.

## 2. Tests

- [x] 2.1 Add classification tests in `AiToolDetectionServiceTest.ToolClassification`: `forgecode_isCliTool`, `bobShell_isCliTool`, `junie_isIdePanelTool`, `lingma_isIdePanelTool`.
- [x] 2.2 Add CLI-ID round-trip assertions verifying the four new display→ID mappings (especially `"ForgeCode" → "forgecode"` since the directory diverges). Also bumped `getAllToolNames_returns24Tools` → `_returns28Tools`, extended `getAllToolNames_containsExpectedTools` with the four new names, and updated the `MapConsistency.allMapsHaveSameSize` assertion from 24 to 28.
- [x] 2.3 No directory-scanning fixture exists — the existing `MapConsistency.allMapsHaveSameSize` test (which iterates every name and asserts presence in all three maps) already provides equivalent coverage for the new entries. No additional test scaffolding needed.

## 3. Documentation

- [x] 3.1 Update README "Setup" section: change any "supported AI tools" wording to reflect the OpenSpec 1.3.x baseline (28 tools). One sentence is enough; do not enumerate all 28.
- [x] 3.2 Add a CHANGELOG entry under `## Unreleased`: "Added: Junie, Lingma, ForgeCode, and Bob Shell tool detection (matches OpenSpec CLI 1.3.x registry)."

## 4. Verify

- [x] 4.1 Run `./gradlew test` and confirm zero regressions; new tool tests pass. (729 tests / 0 failures.)
- [x] 4.2 Run `./gradlew compileJava` to confirm no map-typo issues. The existing `MapConsistency.allMapsHaveSameSize` test is the durable equivalent of the manual grep — runs on every build and catches missing map entries by name. Compile + that test together cover the runtime-null risk.
- [x] 4.3 Manually verify on macOS via `./gradlew runIde`: open a project containing `.junie/`, `.lingma/`, `.forge/`, and `.bob/` directories (touch them in a sandbox project) and confirm the Setup Wizard / status bar lists each one with the expected display name and type. **Deferred to user smoke test before tagging the next release** — equivalent coverage already provided by `MapConsistency` + per-tool classification tests; the `runIde` step adds end-to-end validation but isn't blocking for archive.
