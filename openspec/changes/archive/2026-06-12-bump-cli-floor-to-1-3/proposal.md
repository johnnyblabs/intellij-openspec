## Why

The plugin currently supports OpenSpec CLI versions 1.0.0 onward (per `VersionSupport.V1_0` and `V1_1` enum entries). CLI 1.2.0 launched in May 2025, 1.3.x in early 2026, and 1.4.x soon after. Anyone still on pre-1.3 has effectively abandoned the toolchain — `npm i -g @fission-ai/openspec@latest` is one command away. The plugin is the new user surface; designing it around 4-year-old baselines is engineering tax for a near-zero user population.

Dropping pre-1.3 support is a precondition for OS-218 (1.4.x feature surfaces): the BC matrix collapses from 5 columns (no CLI / 1.0 / 1.1 / 1.2 / 1.3 / 1.4+) to 3 (no CLI / 1.3.x / 1.4+). Less conditional code, less test parameterization, less branching in the validator.

## What Changes

### Code

- **Delete `VersionSupport.V1_0` and `V1_1` enum entries.** `V1_2` becomes the only baseline. **The name stays `V1_2`** — it refers to the config-format version (`openspec/config.yaml`'s `version: 1.2.0` field), NOT the CLI version. These two axes are independent.
- **`VersionSupport.fromString`** — when a config declares `version: 1.0.0` or `1.1.0`, route to V1_2 (the only remaining enum entry). No crash. The prefix-match logic continues to handle 1.2.x / 1.3.x / 1.4.x / 1.5.x correctly via the fallthrough.
- **Raise `SchemaService.MIN_CLI_VERSION` from `"1.2.0"` to `"1.3.0"`.** Schema management features (CLI's `openspec schema` family) now require CLI 1.3+ rather than 1.2+.
- **Extract a small `CliVersion.atLeast(String detected, String required)` utility** (in `util/`) so the new startup floor check can share comparison logic with `SchemaService` instead of duplicating it. Replace `SchemaService`'s private comparison with calls to the utility. This is a tiny refactor in scope because both call sites benefit immediately.
- **Plugin startup floor notification.** Extend `OpenSpecProjectService.StartupDetection` — after `CliDetectionService.detect()` succeeds with a version string, compare against 1.3.0; if older, show a notification:
  > "Your OpenSpec CLI is older than 1.3.0. Plugin features that require the CLI may not work as expected. Upgrade: `npm i -g @fission-ai/openspec@latest`."
  
  Fires once per project open via the existing `StartupDetection` hook — same place the CLI-missing notification fires. No new wire-up needed beyond a few lines in the existing `execute()` method and a new `OpenSpecNotifier.cliBelowFloor(project, version)` helper.

### Docs

- **README.md** — line 35 currently says "requires OpenSpec 1.4.x or later" (from openspec-1-4-baseline). Refine: the **minimum CLI** is 1.3.0; the **recommended CLI** is 1.4.x for full feature parity. Two-sentence clarification.
- **CHANGELOG entry under `## v0.3.0`** — add a new bullet with explicit **BREAKING** marker:
  > **BREAKING — Minimum supported OpenSpec CLI is now 1.3.0.** Users on CLI 1.0, 1.1, or 1.2 see a one-time startup notification recommending upgrade. Plugin features that don't require the CLI continue to function on the built-in fallback paths. To stay on a pre-1.3 CLI, pin to plugin v0.2.10.
- **Marketplace listing** (`docs/marketplace-page.md`) — bump the minimum CLI line if present.

### Tests

- `VersionSupportTest` — drop V1_0 / V1_1 assertions; replace with assertions that `fromString("1.0.0")` and `fromString("1.1.0")` both return V1_2.
- `ConfigVersionValidationTest` — drop V1_0 / V1_1 parameterizations.
- New `CliVersionAtLeastTest` — basic comparison cases (`atLeast("1.3.1", "1.3.0") == true`, `atLeast("1.2.99", "1.3.0") == false`, `atLeast(null, "1.3.0") == false`, etc.).
- New `CliFloorNotificationTest` — `StartupDetection.execute()` with mocked `CliDetectionService` returning `"1.2.0"` produces exactly one notification.
- Existing `SchemaServiceTest` — confirm the floor bump (1.2.0 → 1.3.0) is reflected; isSchemaSupported returns false for `"1.2.0"`, true for `"1.3.0"`.

## Capabilities

### New Capabilities
<!-- None — this is cleanup + a small surface addition. -->

### Modified Capabilities
- `validation`: drop scenarios in "Configuration version validation" that assert V1_0 / V1_1-specific behaviors. Add a scenario covering "Old CLI version (< 1.3) detected at startup → notification fires."
- `plugin-core`: add a scenario under "Notification system" (or "CLI re-detection on tool window activation") for the startup floor notification.

## Impact

- **Code:** `VersionSupport.java` (delete 2 enum entries + simplify), `SchemaService.java` (constant bump + use new utility), `OpenSpecProjectService.java` (add floor check to `StartupDetection.execute`), new `CliVersion.java` utility (~30 lines), `OpenSpecNotifier.java` (add `cliBelowFloor` helper).
- **Tests:** `VersionSupportTest`, `ConfigVersionValidationTest`, new `CliVersionAtLeastTest`, new `CliFloorNotificationTest`, `SchemaServiceTest`.
- **Specs:** `openspec/specs/validation/spec.md` and `openspec/specs/plugin-core/spec.md` deltas.
- **Docs:** README, CHANGELOG, Marketplace listing.
- **Plugin behavior for users on CLI 1.3+:** unchanged.
- **Plugin behavior for users on CLI 1.0/1.1/1.2:** one-time startup notification + features that require the CLI gracefully degrade to the same "CLI not detected" UX the plugin already handles. Init action still works via built-in fallback; tool window still works.
- **Plugin behavior for users with no CLI:** unchanged.
- **Risk:** low. Pre-1.3 users keep working; they see one notification. The plugin's built-in fallback is the safety net. Test suite covers all version-resolution paths.

## References

- Forgejo: johnb/intellij-openspec#214 (the canonical tracker)
- Plane: openspec/issue/OS-224 (`31c1f5a3-6e7f-4f85-8910-415effa8781d`)
- Precondition for: #207 / OS-218 (1.4.x feature surfaces) — simpler BC matrix once floor is at 1.3
- Same propose-flow pattern as `fix-init-default-schema` (#208) and `ci-caching-quick-wins` (#211): existing tracker is canonical; no new tracker created.

No new Forgejo/Plane trackers created. When this change archives, the archive flow closes #214 and moves OS-224 to Done.
