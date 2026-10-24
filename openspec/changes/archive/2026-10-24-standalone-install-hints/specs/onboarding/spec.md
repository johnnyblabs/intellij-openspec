## ADDED Requirements

### Requirement: Install hints name every upstream install route

Every install hint the plugin shows SHALL come from one source. It SHALL lead with upstream's standalone installer for the host OS, then name npm and, outside Windows, the Homebrew formula, using upstream's README commands, and SHALL link to upstream's install instructions. The plugin SHALL only show the commands, never run an installer, change PATH, or remove an install.

#### Scenario: Hint on macOS or Linux
- **WHEN** the plugin shows an install hint on macOS or Linux
- **THEN** it SHALL name the standalone installer command `curl -fsSL https://raw.githubusercontent.com/Fission-AI/OpenSpec/main/scripts/binary/install.sh | sh` first
- **AND** it SHALL name `npm install -g @fission-ai/openspec@latest` and `brew install openspec` as alternatives

#### Scenario: Hint on Windows
- **WHEN** the plugin shows an install hint on Windows
- **THEN** it SHALL name the standalone installer command `irm https://raw.githubusercontent.com/Fission-AI/OpenSpec/main/scripts/binary/install.ps1 | iex` first
- **AND** it SHALL name `npm install -g @fission-ai/openspec@latest` as the alternative, and SHALL NOT suggest Homebrew or the `curl … | sh` command

#### Scenario: Notifications link to the install instructions
- **WHEN** the CLI-not-found notification, or the warning for a CLI command run without the CLI, is shown
- **THEN** it SHALL say that the CLI can be installed without Node.js, or with npm or Homebrew, and SHALL offer an action that opens upstream's install instructions, alongside its existing Settings action

#### Scenario: Dialogs show the commands and the link
- **WHEN** the setup wizard's CLI step or the Manage AI Tools footer shows the install hint
- **THEN** it SHALL show the host OS's standalone command and its alternatives, and a link that opens upstream's install instructions

#### Scenario: The Settings tooltip lists the standalone location
- **WHEN** the user hovers over the Settings CLI-path field
- **THEN** the tooltip SHALL list the standalone install location for the host OS (`~/.local/bin/openspec`, or `%LOCALAPPDATA%\Programs\openspec\openspec.exe` on Windows) next to the existing package-manager examples

#### Scenario: One source for the wording
- **WHEN** the CLI-not-found notification, the warning for a CLI command run without the CLI, the setup wizard's CLI step, the Manage AI Tools footer or the Settings CLI-path tooltip shows how to install the CLI
- **THEN** its wording SHALL come from the shared install hint, and no other plugin code SHALL write out its own npm-only install command

#### Scenario: The below-floor upgrade hint is unchanged
- **WHEN** the detected CLI is older than 1.3.0
- **THEN** the upgrade notification SHALL keep its existing npm upgrade command, because no standalone build is older than 1.15
