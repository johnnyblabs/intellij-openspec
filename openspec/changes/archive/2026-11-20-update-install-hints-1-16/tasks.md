## 1. Install commands

- [x] 1.1 Point `CliInstallHints` at `https://openspec.dev/cli/install.sh` and `https://openspec.dev/cli/install.ps1`, and the npm floor at Node.js 22.12.
- [x] 1.2 Re-capture the upstream Quick Start at v1.16.0 as `fixtures/upstream/readme-quick-start-1.16.0.md` and point `CliInstallHintsTest` at it.

## 2. Documentation

- [x] 2.1 Update the README, `docs/feature-reference.md`, `docs/marketplace/getting-started.html` and the wiki install pages to the same commands and floor.

## 3. Verification

- [x] 3.1 `CliInstallHintsTest` passes against the 1.16.0 Quick Start.
- [x] 3.2 `openspec validate update-install-hints-1-16 --strict` passes with CLI 1.16.0.
