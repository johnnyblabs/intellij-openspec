## Why

The plugin uses a placeholder icon — a purple square with the letter "S" at 13x13px. This works for tree nodes but doesn't serve as a professional plugin identity. JetBrains Marketplace requires a 40x40 `pluginIcon.svg` for the plugin listing page, and the tool window icon should be a distinct, recognizable mark. A polished icon establishes brand identity and makes the plugin look production-ready.

## What Changes

- Create a new 40x40 `pluginIcon.svg` for JetBrains Marketplace listing (required by Marketplace guidelines)
- Create a matching `pluginIcon_dark.svg` for dark theme Marketplace pages
- Redesign the 13x13 `openspec.svg` tool window icon to be a distinctive mark rather than a letter in a box
- Update `openspec_dark.svg` to match the redesigned light variant
- Ensure all icon sizes and naming follow JetBrains Platform icon guidelines

## Capabilities

### New Capabilities

_None — this is an asset/configuration change with no new behavioral capabilities._

### Modified Capabilities

_None — no spec-level behavior changes._

## Impact

- `src/main/resources/icons/openspec.svg` — redesigned 13x13 tool window icon
- `src/main/resources/icons/openspec_dark.svg` — matching dark variant
- `src/main/resources/META-INF/pluginIcon.svg` — new 40x40 Marketplace icon
- `src/main/resources/META-INF/pluginIcon_dark.svg` — new 40x40 dark Marketplace icon
- No code changes — icon paths in `plugin.xml` remain `/icons/openspec.svg`
