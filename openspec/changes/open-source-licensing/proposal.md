## Why

JetBrains Marketplace requires plugin developers to provide their own EULA, and open-source plugins must link to source code. The plugin currently ships with no license and uses the JetBrains Marketplace EULA as a temporary placeholder, which does not meet approval criteria. Adding Apache 2.0 licensing and publishing source to GitHub resolves compliance, builds community trust, and enables contributions.

## What Changes

- Add Apache 2.0 LICENSE file to the project root
- Add license headers or NOTICE file as required by Apache 2.0
- Create a public GitHub repository and configure as a second remote
- Update Marketplace listing metadata: set license to Apache 2.0, link to GitHub source
- Update `build.gradle.kts` vendor block with source repository URL
- Update `plugin.xml` vendor URL if needed
- Update `docs/marketplace-page.md` to reflect the license and source link

## Capabilities

### New Capabilities

_None_ — this is a licensing and distribution change, not a functional change.

### Modified Capabilities

_None_ — no spec-level behavior changes.

## Impact

- `LICENSE` — new file at project root (Apache 2.0 full text)
- `NOTICE` — new file at project root (copyright and attribution)
- `build.gradle.kts` — vendor URL update
- `plugin.xml` — vendor URL update if needed
- `docs/marketplace-page.md` — license field, source link, EULA reference updated
- GitHub repository — new public mirror of the codebase
- `.gitignore` — review for any secrets or private files before going public
