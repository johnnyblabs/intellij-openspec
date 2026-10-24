# Proposal

## Why

OpenSpec 1.15's Quick Start now leads with a standalone installer that needs no Node.js. npm and the official Homebrew formula are listed as alternatives. Every install hint the plugin shows still says only `npm i -g @fission-ai/openspec`, so a new user is told to install Node.js for a CLI that no longer needs it. The same hint is written out separately in five places, and they have already drifted apart. Tracked by the linked tracker entry.

A user can also end up with two copies: an older npm or Homebrew install, plus a standalone one from following the new Quick Start. The terminal then runs whichever comes first in PATH, while the plugin keeps the copy it finds first. Nothing in the docs explains the install routes, or that the plugin's Update action refreshes project files rather than the CLI.

## What Changes

- **One source for the install hint.** All five places get their wording from one helper, so they can't drift apart again:
  - the CLI-not-found notification;
  - the warning shown when a CLI command runs without the CLI;
  - the setup wizard's CLI step;
  - the Manage AI Tools footer;
  - the Settings CLI-path tooltip.
- **The hint leads with the standalone installer for the current OS:**
  - macOS and Linux: `curl -fsSL https://raw.githubusercontent.com/Fission-AI/OpenSpec/main/scripts/binary/install.sh | sh`;
  - Windows: `irm https://raw.githubusercontent.com/Fission-AI/OpenSpec/main/scripts/binary/install.ps1 | iex`.

  It then names npm (`npm install -g @fission-ai/openspec@latest`, which needs Node.js 20.19 or later) and, outside Windows, `brew install openspec`. These are the commands upstream's README gives.
- **An "Install instructions" link** opens upstream's installation page. The two notifications offer it as an action, and the wizard and the Manage AI Tools footer show it as a link. Notifications keep their short text and name the routes without the long one-liner.
- **The Settings CLI-path tooltip** lists the standalone install locations next to the existing examples.
- **Unchanged:** the below-1.3.0 upgrade message stays npm-only, because no standalone build is older than 1.15.
- **Docs:**
  - **Install routes:** the README's install section, the feature reference's troubleshooting, the Copilot and CLI-companion getting-started guides, and the Marketplace Getting Started copy show the three install routes.
  - **"Keep one copy" note:** it names the three routes and where each installs, and says how to upgrade each the way it was installed. It explains that the plugin's Update action runs `openspec update` (it refreshes the project's instruction files, not the CLI), and that Settings → CLI path picks a specific copy.
- **Off-model:** the plugin only shows the commands. It never runs an installer, edits PATH, or removes an install.
- **Not in this change:** a warning when two copies at different versions are found. That needs detection to keep looking after its first hit, so it has its own tracker entry.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `onboarding`: a new requirement that every install hint names the standalone installer for the host OS first, then the package-manager alternatives, and links to upstream's install instructions.

## Impact

- **Code:**
  - A new helper in `com.johnnyblabs.openspec.util` for the install hint.
  - Callers: `OpenSpecNotifier.cliMissing`, `OpenSpecCliAction`, `SetupWizardDialog`, `ManageAiToolsDialog` and `OpenSpecSettingsPanel`.
- **Tests:**
  - Unit tests for each OS variant of the helper.
  - A test that the not-found notification carries the instructions action.
  - A guard test that fails if the npm-only hint is written out anywhere else in `src/main`.
- **Docs:** `README.md`, `docs/feature-reference.md`, `docs/getting-started-copilot.md`, `docs/getting-started-cli-companion.md`, `docs/marketplace/getting-started.html` (pasted into the listing by hand after release), and a `CHANGELOG.md` `## Unreleased` Changed entry.
- **Platform:** no new platform API beyond `BrowserUtil`, which the plugin already uses. The plugin still supports IntelliJ IDEA 2024.2 and later.
