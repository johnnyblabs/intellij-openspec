## Why

The plugin is about to be published to JetBrains Marketplace where the plugin ID is permanent. The current package `com.johnnyb.openspec` doesn't match the vendor domain `johnnyblabs.com`. Renaming to `com.johnnyblabs.openspec` before publishing ensures the plugin ID, package name, and vendor identity are all consistent and follow Java's reverse-domain convention.

## What Changes

- Rename Java package `com.johnnyb.openspec` → `com.johnnyblabs.openspec` across all source and test files
- Move source directories from `com/johnnyb/openspec/` to `com/johnnyblabs/openspec/`
- Update `plugin.xml`: plugin ID, all service/action class references
- Update `build.gradle.kts`: `group` and `pluginConfiguration.id`
- Update `<vendor>` name from `johnnyb` to `johnnyblabs`

## Capabilities

### New Capabilities

_None — this is a mechanical rename, not a feature change._

### Modified Capabilities

_None — no spec-level behavior changes._

## Impact

- **Every Java file** (112 files): package declaration and cross-package imports
- **plugin.xml**: plugin ID and all fully-qualified class names in service/action registrations
- **build.gradle.kts**: `group` and `pluginConfiguration.id`
- **Directory structure**: `src/main/java/com/johnnyb/` → `src/main/java/com/johnnyblabs/`
- **No runtime behavior change** — purely a namespace rename
