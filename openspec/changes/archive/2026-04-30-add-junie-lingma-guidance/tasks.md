## 1. TOOL_GUIDANCE entries

- [x] 1.1 Add `"Junie" → new ToolGuidance("Junie", "Open Junie and paste the prompt", "/opsx-", false)` to `TOOL_GUIDANCE` in `AiToolDetectionService.java`. Place after the existing `"Bob Shell"` entry to keep recently-added tools grouped.
- [x] 1.2 Add `"Lingma" → new ToolGuidance("Lingma chat", "Open Lingma chat and paste the prompt", null, false)` immediately after the Junie entry.

## 2. Tests

- [x] 2.1 In `AiToolDetectionServiceTest$ToolGuidanceLookups`, remove the existing `junieAndLingma_stillFallThroughToDefault` test (it would now fail) and add `junie_hasExplicitGuidanceWithOpsxPrefix` asserting `chatPanelName == "Junie"`, `pasteAction == "Open Junie and paste the prompt"`, `promptPrefix == "/opsx-"`, `canAutoSave == false`.
- [x] 2.2 Add `lingma_hasExplicitGuidanceWithoutPrefix` asserting `chatPanelName == "Lingma chat"`, `pasteAction == "Open Lingma chat and paste the prompt"`, `promptPrefix == null`, `canAutoSave == false`.
- [x] 2.3 Add `unknownTool_fallsThroughToDefaultGuidance` covering the modified "Default fallback when no explicit entry exists" spec scenario — assert `getToolGuidance("Some Future Tool")` returns a `ToolGuidance` with `chatPanelName == "your AI tool"` and `pasteAction == "Paste into your AI tool"`. This preserves the default-fallback regression coverage that the removed `junieAndLingma_stillFallThroughToDefault` test was previously providing.

## 3. Documentation + Verify

- [x] 3.1 Add a CHANGELOG entry under `## Unreleased`: "Added: tailored delivery guidance for Junie and Lingma AI tools (replaces generic default with panel-specific copy)." (Bundled with ForgeCode/Bob Shell guidance into a single release-level "Tailored delivery guidance" line — same change ships them all together.)
- [x] 3.2 Run `./gradlew test` and confirm zero regressions; the three updated/new guidance tests pass; full suite remains green. (734 tests / 0 failures, up from 732 — net +2 after removing one fall-through test and adding three new.)
- [x] 3.3 Run `./gradlew compileJava` to confirm the additions don't break `Map.ofEntries` (which is now 16 entries — well within its varargs overload). Compile clean.
