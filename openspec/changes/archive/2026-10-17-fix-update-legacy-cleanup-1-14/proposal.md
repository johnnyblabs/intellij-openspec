## Why

OpenSpec CLI 1.14.0 reworded the guidance line in `openspec update`'s legacy-cleanup block. Before 1.14 it read **"No user content to preserve:"**; from 1.14.0 it reads **"These files will be deleted entirely. Back up any custom content before proceeding:"**. The plugin's parser only accepts the old line, so on every 1.14.x CLI it finds nothing and the cleanup prompt never appears. Real 1.13.2 and 1.14.3 output, captured from the same project, differs only in that line. This breaks the `cli-update` capability and the rule that 1.14.x delivers everything 1.13.x does, and it shipped unnoticed because the newest update fixtures were from 1.7.0. An audit of the upstream release notes found it.

The new wording also changes what the plugin may assume. The cleanup dialog ticks every file by default and quotes the old certification that the files hold no user content. On 1.14, the CLI says the opposite.

Two earlier statements about this flow were also wrong: the uiSmoke journey's skip reason, and the test-drive walkthrough, which said the block disappeared in CLI 1.8. It didn't. It still appears for tools whose old command files are legacy, such as Cursor. The demo project uses Junie, whose commands are regenerated instead, so the flow was never exercised.

Tracked in the linked tracker entry.

## What Changes

- **Recognize the block whatever its guidance line says.** The parser accepts any non-bullet line between the "Files to remove" header and the first bullet, and returns that line alongside the paths. Recognition still keys on the header and the bullet run, and a block it can't read still yields nothing.
- **Follow what the CLI actually says.**
  - The cleanup dialog quotes the CLI's own guidance line rather than a hard-coded one.
  - Files start ticked only when the CLI certifies they hold no user content. When it warns to back up custom content (1.14+), they start unticked, so deletion is an explicit choice.
  - The Update notification no longer claims the CLI "reports [them] safe to remove".
- **Contract coverage from real output:** new `update` captures from CLI 1.13.2 and 1.14.3 (legacy Cursor commands), next to the existing 1.6.0 and 1.7.0 ones.
- **Exercise the flow again:**
  - The demo seeding adds legacy Cursor command files, so the flow fires on current CLIs.
  - The uiSmoke journey's version skip is removed and its committed fixture regenerated.
  - Walkthrough Stop 3 and the incorrect "CLI < 1.8" statements are corrected.
- **Docs:** correct the "no plugin code change" statement for 1.14.x in `docs/openspec-support.md`; CHANGELOG `### Fixed`.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `cli-update`: detection accepts any guidance line in the legacy-cleanup block and records it. The review dialog quotes that line and defaults to unticked when the CLI warns about custom content.

## Impact

- **Code:** `UpdateOutputParser` (tolerant guidance line, returned with the paths), `UpdateLegacyCleanupService` (notification wording), `LegacyCleanupDialog` (quoted line, default tick state).
- **Tests/fixtures:**
  - `fixtures/cli/1.13.2/update-legacy-pending.txt` and `fixtures/cli/1.14.3/update-legacy-pending.txt`;
  - `UpdateOutputParserContractTest` and `UpdateLegacyCleanupFlowTest` extended;
  - the uiSmoke journey and its seeded fixture.
- **Tooling:** `scripts/seed-lifecycle-demo.sh`, and the test-drive skill's walkthrough.
- **Platforms:** no change to IntelliJ 2024.2+ compatibility.
