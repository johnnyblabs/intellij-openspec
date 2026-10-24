## Context

Five places write out the install hint, each with its own npm-only string:

| Surface | Code | Form |
|---|---|---|
| CLI-not-found notification | `OpenSpecNotifier.cliMissing` | plain text, with a Settings action |
| CLI command run without the CLI | `OpenSpecCliAction.actionPerformed` | plain text, with a Settings action |
| Setup wizard CLI step | `SetupWizardDialog` (a Windows and a Unix variant) | HTML label |
| Manage AI Tools footer | `ManageAiToolsDialog` | HTML label |
| Settings CLI-path tooltip | `OpenSpecSettingsPanel` (a Windows and a Unix variant) | HTML tooltip, example paths only |

The below-floor upgrade text (`cliBelowFloor`, and `BuiltInValidator`'s status line) is out of scope; see the proposal.

Upstream's README `## Quick Start`, identical at tag v1.15.0 and on `main` on 2026-10-24, gives the commands below. It links to the installation page `https://openspec.dev/docs/installation` for the other routes.
- `curl -fsSL https://raw.githubusercontent.com/Fission-AI/OpenSpec/main/scripts/binary/install.sh | sh` for macOS and Linux;
- `irm https://raw.githubusercontent.com/Fission-AI/OpenSpec/main/scripts/binary/install.ps1 | iex` for Windows;
- `npm install -g @fission-ai/openspec@latest`, which needs Node.js 20.19.0 or later;
- `brew install openspec`.

## Goals / Non-Goals

**Goals:**
- One source for the wording, tested per OS.
- Every surface leads with the standalone installer and names the alternatives.
- An easy way into upstream's full instructions.
- Docs that explain the install routes and the "keep one copy" rule.

**Non-Goals:**
- Warning about two copies at different versions; that has its own tracker entry.
- Running or verifying an installer.
- Changing the below-floor upgrade text.
- Copy-to-clipboard buttons.

## Decisions

1. **A small static helper, `CliInstallHints`, in `util`.** It takes an `isWindows` flag rather than reading `SystemInfo` itself, so tests can check both OS variants on any host. Callers pass `SystemInfo.isWindows`. It exposes:
   - `standaloneCommand(isWindows)`: the OS's one-liner.
   - `notificationText(isWindows)`: one short sentence for the two notifications, e.g. "Install it with OpenSpec's installer (no Node.js needed), npm or Homebrew." It names no commands, because the one-liner is too long for a balloon.
   - `html(isWindows)`: a fragment for labels. It holds the standalone command in `<code>`, "or `npm install -g @fission-ai/openspec@latest` (needs Node.js 20.19 or later)", plus "or `brew install openspec`" outside Windows, but no link, since labels can't follow one.
   - `INSTALL_DOCS_URL = "https://openspec.dev/docs/installation"`, upstream's installation page, which the README Quick Start also links to.
   - `standaloneLocation(isWindows)`, for the tooltip.

   *Alternative:* a message bundle. Rejected, because the plugin's other user strings are inline Java, and a bundle would be a project-wide change.
2. **Notifications keep their short text and gain an action.**
   - **Text:** `cliMissing` and `OpenSpecCliAction` end their text with `notificationText(...)` instead of the npm command.
   - **Action:** both add an "Install Instructions" action that calls `BrowserUtil.browse(INSTALL_DOCS_URL)`, before the existing Settings action. The action is built by one helper in `OpenSpecNotifier`, next to `openSettingsAction()`, so both callers share it.
3. **Labels show the command and the link.**
   - **Wizard:** its CLI step and the Manage AI Tools footer embed `html(...)`.
   - **Links:** a `JBLabel` doesn't open links, so each dialog adds a `HyperlinkLabel` reading `INSTALL_DOCS_LABEL` under the hint, and it calls `BrowserUtil.browse(INSTALL_DOCS_URL)`. This is the pattern `GettingStartedPanel` and `WorkflowActionPanel` already use.
   - **Windows extra:** the wizard keeps its Windows-only sentence about the `.cmd` extension, which still applies to npm installs.
4. **Tooltip:** it adds `standaloneLocation(isWindows)` to the examples it already lists, and gets no commands or links, since a tooltip can't be clicked.
5. **A guard against drift.** A unit test scans `src/main/java` for both `npm i -g @fission-ai/openspec` and `npm install -g @fission-ai/openspec`.
   - **Allowed hits:** in `CliInstallHints.java`; or the exact below-floor upgrade text `npm i -g @fission-ai/openspec@latest` in `OpenSpecNotifier.java` and `BuiltInValidator.java`. The rule checks each hit, not whole files, so a stray hint left in `cliMissing` still fails.
   - **Against a vacuous pass:** the test also checks that it scanned more than 100 files, that `CliInstallHints` itself has a hit, and that there are exactly two below-floor hits.

## Risks / Trade-offs

- **A command that pipes a download into a shell** is shown to users. → It's upstream's own primary instruction, word for word. The script comes from upstream's `main`, as their README says; the plugin adds nothing.
- **Upstream may change its commands or move the installation page.** → The commands are tested against a captured copy of the README's Quick Start (`fixtures/upstream/readme-quick-start-1.15.0.md`), which is re-captured on every CLI bump. A changed command then fails the contract test, not a user.
- **The Marketplace Getting Started copy** is pasted into the listing by hand. → It's updated in the repo, and flagged in the PR for the post-release paste, as with screenshots.
