## 1. TOOL_GUIDANCE entries

- [x] 1.1 Add `"ForgeCode" → new ToolGuidance("terminal", "Paste into ForgeCode", null, true)` to `TOOL_GUIDANCE` in `AiToolDetectionService.java`. Place after the existing `"OpenCode"` entry to keep CLI-style tools grouped.
- [x] 1.2 Add `"Bob Shell" → new ToolGuidance("terminal", "Paste into Bob Shell", null, true)` to `TOOL_GUIDANCE` immediately after the ForgeCode entry.

## 2. Tests

- [x] 2.1 Add a `ToolGuidance` nested test class (or reuse an existing nested class if one already covers guidance lookups) with two assertions: `getToolGuidance("ForgeCode")` returns the expected `ToolGuidance` (`chatPanelName == "terminal"`, `pasteAction == "Paste into ForgeCode"`, `promptPrefix == null`, `canAutoSave == true`) and is **not** equal-by-reference to `DEFAULT_GUIDANCE`. (Field-equality assertions on chatPanelName/pasteAction/promptPrefix/canAutoSave provide the same coverage as a reference-inequality check.)
- [x] 2.2 Mirror 2.1 for `"Bob Shell"`.
- [x] 2.3 Add a regression assertion that Junie and Lingma still fall through to `DEFAULT_GUIDANCE` (they shouldn't get accidental entries from a copy-paste mistake during 1.1/1.2).

## 3. Verify

- [x] 3.1 Run `./gradlew test` and confirm zero regressions; new guidance tests pass. (732 tests / 0 failures, up from 729 — accounts for 3 new tests in `ToolGuidanceLookups`.)
- [x] 3.2 Run `./gradlew compileJava` to confirm the new entries don't break the `Map.ofEntries` size limits or duplicate-key checks. (Compiled clean — `TOOL_GUIDANCE` is now 14 entries; `Map.ofEntries` supports arbitrary count.)
