## 1. Shared install hint

- [x] 1.1 Add `com.johnnyblabs.openspec.util.CliInstallHints` with:
  - `INSTALL_DOCS_URL` (`https://openspec.dev/docs/installation`) and `INSTALL_DOCS_LABEL`;
  - `standaloneCommand(isWindows)`;
  - `standaloneLocation(isWindows)`;
  - `notificationText(isWindows)`;
  - `html(isWindows)`.

  Capture upstream's README `## Quick Start` at tag v1.15.0 as `src/test/resources/fixtures/upstream/readme-quick-start-1.15.0.md`, with its source and the re-capture command in a header comment. Verify in a new `CliInstallHintsTest`:
  - **Contract:** the curl, irm, npm and brew commands each appear verbatim in the fixture (code blocks and inline code).
  - **Unix:** `html` contains the curl command, then npm, then brew, in that order.
  - **Windows:** `html` contains the irm command and npm, and neither `brew` nor `curl`.
  - **Notifications:** `notificationText` names no command on either OS, and mentions Homebrew only outside Windows.

## 2. Surfaces

- [x] 2.1 `OpenSpecNotifier`:
  - add `installInstructionsAction()`, which opens `INSTALL_DOCS_URL` through `BrowserUtil`;
  - in `cliMissing`, use `notificationText(SystemInfo.isWindows)` and offer the instructions action before Settings;
  - leave `cliBelowFloor` unchanged.

  Verify in a platform test that captures the real `Notification` from `Notifications.TOPIC`. Don't mock `OpenSpecNotifier`, which would make the test vacuous:
  - the content has no npm command;
  - both actions are present, in order;
  - running the instructions action browses `INSTALL_DOCS_URL`, with `BrowserUtil` statically mocked.
- [x] 2.2 `OpenSpecCliAction`: the CLI-missing warning uses `notificationText(...)` and gets both actions. Verify in a platform test with a test subclass and an unavailable CLI stub:
  - the content names the command label;
  - it has no npm command;
  - both actions are attached, in order.
- [x] 2.3 `SetupWizardDialog`:
  - the CLI step's hint embeds `html(SystemInfo.isWindows)`;
  - the link opens `INSTALL_DOCS_URL`;
  - keep the Windows `.cmd` sentence.

  Verify by building the step's hint text on both OSes. Extract a static `cliHintHtml(isWindows)` if that's needed to test it without a dialog.
- [x] 2.4 `ManageAiToolsDialog`: the footer embeds `html(...)` with a working link. Verify the footer text through a static builder, as in 2.3.
- [x] 2.5 `OpenSpecSettingsPanel`: the CLI-path tooltip adds `standaloneLocation(...)` next to its existing examples. Verify through a static `cliPathTooltip(isWindows)`.
- [x] 2.6 Add `InstallHintDriftTest`, implementing design decision 5: allowed hits are checked one by one, there must be more than 100 files scanned, a hit in `CliInstallHints`, and exactly two below-floor hits. Mutation-check it: re-adding the old string in `ManageAiToolsDialog` must fail the test, and so must leaving it in `cliMissing`.
- [x] 2.7 Mutation-check the new tests by hand. Each must fail when one of these is reverted:
  - drop the brew line;
  - show brew on Windows;
  - swap the standalone and npm order;
  - drop the instructions action from `cliMissing`;
  - use the Unix command on Windows;
  - make `notificationText` name a command.

## 3. Docs

- [x] 3.1 `README.md` install section: the standalone installer for macOS/Linux and Windows first, then npm and Homebrew, with a short "keep one copy" note. Update the Windows note to match.
- [x] 3.2 `docs/feature-reference.md`: in troubleshooting, change the install step to the three routes, and add a "Which OpenSpec CLI the plugin uses" subsection covering:
  - where each route installs;
  - that the plugin picks the first copy it finds, in its detection order;
  - upgrading each copy the way it was installed;
  - that the Update action refreshes the project's files, not the CLI;
  - that Settings → CLI path picks a copy.
- [x] 3.3 `docs/getting-started-copilot.md` and `docs/getting-started-cli-companion.md`: update the prerequisite tables and the Copilot tip.
- [x] 3.4 `docs/marketplace/getting-started.html`: lead with the standalone installer, with npm and Homebrew as alternatives. Run `python3 scripts/check-marketplace-listing.py`, which is expected to report drift until the listing is pasted after release, and record that in the PR.
- [x] 3.5 `CHANGELOG.md` `## Unreleased` Changed entry: install hints now lead with OpenSpec's standalone installer, which needs no Node.js, and link to upstream's install instructions.
- [x] 3.6 `scripts/docs/wiki/Installation.md` and `Troubleshooting.md`, the internal wiki sources: the three routes and the "keep one copy" note.

## 4. Verify

- [x] 4.1 `./gradlew build` is green, and `openspec validate standalone-install-hints --strict` is clean.
- [x] 4.2 Run `caffeinate -dimsu ./gradlew uiSmoke`, because the wizard and the Manage AI Tools dialog are UI. Every journey must be green, or any failure must be triaged as environmental per the usual rule.

## Workflow follow-up

- Archive the change, then close the tracker entry.
