## Why

The JetBrains Marketplace verifier reports deprecated API usage on IDEs newer than our own CI checks: 6 usages on 2026.1, 7 on 2026.2 and 24 on the 2026.3 EAP. Our `verifyPlugin` gate stops at 2025.2 (the last IntelliJ IDEA Community build), so it showed zero and these went unnoticed. Two of the four deprecated APIs have a replacement that exists, and is not deprecated, on every IDE from the 2024.2 floor to today. The CI spec allows a plain deprecation only while no such replacement exists, so those two should be migrated. The gate should also cover the newer IDEs so the next round shows up in CI instead of on the Marketplace.

Tracked in the linked tracker entry.

## What Changes

- **Read actions.** Five places read files or parse specs under `ReadAction.compute(...)`, which is deprecated on newer IDEs. They move to `Application.runReadAction(ThrowableComputable)`. On 2024.2 the old call simply delegates to this one, so behaviour is identical.
- **Opening files.** Thirteen places open a file in the editor with `FileEditorManager.openFile(file, true)`, deprecated on newer IDEs. They move to `OpenFileDescriptor(project, file).navigate(true)`, which the plugin already uses for Search Everywhere results. The same file opens, focused, in the same editor. A guard test keeps the deprecated `openFile` and `ReadAction.compute` calls from coming back.
- **Two documented exceptions remain.** The terminal launcher's `createShellWidget` and the Search Everywhere contributor's data-provider API stay as they are, because their replacements don't exist on 2024.2 (and the Search Everywhere replacement is experimental and Kotlin-coroutine-only). They're recorded as exceptions to migrate when the floor advances.
- **Wider verifier range.** `verifyPlugin` checks five IDEs instead of four:
  - the 2024.2 floor;
  - the last Community release (2025.2);
  - the unified IntelliJ IDEA releases 2025.3, 2026.1 and 2026.2.

  2024.3 and 2025.1 drop out. The plugin compiles against 2024.2 and breakages appear first in the newest IDEs, so the floor plus the newest releases catch what matters at about one extra IDE download per run. The 2026.3 EAP stays out of CI: the Marketplace checks every upload against EAP builds already.
- **No user-visible change.** The same files open, in the same places, and every feature behaves as before. Expected Marketplace counts after the change: 2026.1 drops from 6 to 1, 2026.2 from 7 to 2, and the 2026.3 EAP from 24 to 6. All remaining usages are the two documented exceptions.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `ci`: the verified IDE set becomes the compatibility floor plus the latest release of each line from the last Community build onward (instead of "every version in the range"), and plain-deprecated usages with a replacement available at the floor must be migrated, with the remaining exceptions documented.

## Impact

- **Code:**
  - Read actions: `ChangeService`, `SpecParsingService`, `ConfigService`, `BuiltInValidator` and `SpecSearchEverywhereContributor`.
  - File opening: `OpenSpecSettingsPanel` (three sites), `OpenSpecNotifier`, `OpenSpecToolWindowPanel` (three), `CoordinationPanel`, `WorkflowActionPanel` (three), `ExploreContextAction` and `LegacyCleanupDialog`.
  - `build.gradle.kts`: the `pluginVerification.ides` block and the exception comment above the verifier severity gate.
- **Tests:** a static guard against the two migrated deprecated calls, in the style of the existing action-invocation guard. Headless tests for a file-opening site and a read-action site.
- **CI:** about one extra IDE download per verify run (three unified IntelliJ IDEA distributions, which are larger than Community ones). The local pre-push verifier gate downloads the new IDEs once and caches them.
- **Platforms:** IntelliJ 2024.2+ compatibility is unchanged. Both replacements exist and are not deprecated on every IDE from 2024.2 through 2026.2. `verifyPlugin` is the gate: on the newer IDEs it must report only the two documented exceptions.
