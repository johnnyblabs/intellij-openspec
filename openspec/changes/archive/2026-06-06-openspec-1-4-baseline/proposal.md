## Why

OpenSpec CLI 1.4.0 expanded the upstream AI tool registry from 28 to 30 (adding Kimi CLI and Mistral Vibe — Antigravity was already in the plugin's 1.3.x set) and introduced a second workflow schema, `workspace-planning`, alongside the existing `spec-driven`. The plugin was last aligned with 1.3.x in v0.2.10, so projects on 1.4.x now have two tools the plugin can't detect and can adopt a schema the plugin's validator rejects.

## What Changes

- Add **Kimi CLI** (CLI ID `kimi`, dir `.kimi`, type `CLI`) to `AiToolDetectionService`'s `TOOL_DIRS`, `TOOL_TYPES`, and `CLI_TOOL_IDS` registries.
- Add **Mistral Vibe** (CLI ID `vibe`, dir `.vibe`, type `CLI`) to the same three registries.
- Add **tailored delivery guidance** for both tools to `TOOL_GUIDANCE` — terminal-style copy ("Paste into Kimi CLI", "Paste into Mistral Vibe") with `canAutoSave: true` so the IDE watches `tasks.md` for progress instead of prompting for manual save. Mirrors the v0.2.10 pattern for ForgeCode / Bob Shell; preserves the "generic 'Paste into your AI tool' fallback no longer appears for new CLI tools" invariant.
- Bump the detection-service expectation from 28 to 30 entries (`AiToolDetectionServiceTest`).
- Add `workspace-planning` to `VersionSupport.V1_2.getValidSchemas()` so changes scaffolded under the new schema validate cleanly. Earlier enum entries (V1_0, V1_1) remain `spec-driven`-only — the new schema is a 1.4.x addition and shouldn't retroactively become valid for older config versions.
- Update README, CHANGELOG, and any other user-facing references from "OpenSpec 1.3.x" to "1.4.x" (including the `28 supported AI tools` count → `30`).
- **Out of scope (deferred):** IDE surfaces for the new `workspace`, `context-store`, and `initiative` CLI commands; the `init --profile <core|custom>` flag. These are net-new feature areas tracked separately.

## Capabilities

### New Capabilities
<!-- None — this is alignment work on existing capabilities. -->

### Modified Capabilities
- `plugin-core`: tool-detection requirement updates from "all 28 AI tools supported by the OpenSpec CLI 1.3.x" to "all 30 AI tools supported by the OpenSpec CLI 1.4.x"; the CLI_TOOL_IDS mapping must continue to match upstream `@fission-ai/openspec/dist/core/config.js`.
- `validation`: schema-compatibility validation accepts `workspace-planning` as a valid schema for projects on the V1_2 schema-version baseline.

`schema-management` is not listed: the plugin already calls `openspec schemas --json` and displays whatever it returns, so no plugin-side change is needed for the new `workspace-planning` schema to appear in the listing UI.

## Impact

- **Code:** `src/main/java/com/johnnyblabs/openspec/services/AiToolDetectionService.java`, `src/main/java/com/johnnyblabs/openspec/version/VersionSupport.java`.
- **Tests:** `AiToolDetectionServiceTest` (28→30 assertion), `VersionSupportTest` (new `workspace-planning` schema assertion), `ConfigVersionValidationTest` (new schema acceptance for V1_2).
- **Docs:** `README.md`, `CHANGELOG.md`, plus any wiki / docs pages with "1.3.x" or "28 supported AI tools" copy.
- **Plugin behavior:** no breaking changes. Existing projects on `spec-driven` see no functional difference. New tools start being detected; new schema stops being flagged as invalid.
- **No new minimum CLI version** required at the IDE — `openspec --version` parsing already tolerates 1.4.x because `VersionSupport.fromString` matches by major-minor prefix and defaults to V1_2 on unknown.

## References

- Tracker: the linked issue
- Tracker: the linked issue