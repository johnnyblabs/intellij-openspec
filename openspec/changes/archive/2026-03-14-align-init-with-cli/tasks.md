## 1. Add missing AI tools to AiToolDetectionService

- [x] 1.1 Add 18 new entries to `TOOL_DIRS` LinkedHashMap (directory → display name)
- [x] 1.2 Convert `TOOL_TYPES` from `Map.of()` to `Map.ofEntries()` and add entries for all 24 tools
- [x] 1.3 Add `CLI_TOOL_IDS` map — display name → CLI ID (e.g., "GitHub Copilot" → "github-copilot")
- [x] 1.4 Add `ToolGuidance` entries for new tools (use DEFAULT_GUIDANCE for tools without known chat panel names)

## 2. Delegate init to CLI in ScaffoldingService

- [x] 2.1 Add `initWithCli()` private method that builds `--tools` flag from detected tools and runs `openspec init` via `CliRunner`
- [x] 2.2 Add VFS refresh after CLI init completes (`VfsUtil.markDirtyAndRefresh`)
- [x] 2.3 Update `initOpenSpec()` to try CLI first, fall back to built-in on failure or CLI absence
- [x] 2.4 Fix built-in fallback: create `archive/` as child of `changes/` directory instead of sibling

## 3. Verify

- [x] 3.1 Build plugin and confirm zero compilation errors
- [x] 3.2 Run Plugin Verifier and confirm no new warnings
