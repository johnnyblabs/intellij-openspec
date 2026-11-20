## Why

OpenSpec CLI 1.16.0 moved its installer to `https://openspec.dev/cli/install.sh` and `https://openspec.dev/cli/install.ps1`, and raised the npm install's Node.js floor to 22.12.0. The plugin's install hints, the README and the getting-started docs still show the old GitHub installer URLs and the Node.js 20.19 floor, so a user who follows them gets a command that upstream no longer documents.

## What Changes

- The install hint names the standalone installer at `https://openspec.dev/cli/install.sh` (macOS and Linux) and `https://openspec.dev/cli/install.ps1` (Windows), and gives the npm floor as Node.js 22.12.
- The install-hint requirement in the onboarding capability uses the new commands. Its structure, ordering and the no-execution rule are unchanged.
- The README, the feature reference, the getting-started page and the wiki install pages show the same commands and floor.
- The captured upstream Quick Start fixture is re-captured at 1.16.0 and the test that checks the hint's commands against it points at that fixture.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `onboarding`: the install-hint requirement names the 1.16 installer commands.

## Impact

- `CliInstallHints` and its test, the upstream Quick Start fixture, and the install commands in the README, `docs/feature-reference.md`, `docs/marketplace/getting-started.html` and the wiki sources.
- No behaviour change beyond the text the plugin shows. The plugin still never runs an installer.
