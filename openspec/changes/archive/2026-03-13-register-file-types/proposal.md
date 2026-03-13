## Why

OpenSpec projects use distinctive file types (`.openspec.yaml`, `spec.md`, delta specs) that currently appear as generic YAML or Markdown in the IDE. Registering custom file types gives users instant visual recognition in the project tree, editor tabs, and search results — reinforcing that these are structured OpenSpec artifacts, not arbitrary files.

## What Changes

- Register `.openspec.yaml` as a recognized file type with the OpenSpec icon
- Register spec files (`spec.md` under `openspec/specs/`) with the spec icon
- Register delta spec files (`spec.md` under `openspec/changes/*/specs/`) with the delta-spec icon
- All registrations use IntelliJ's `<fileType>` extension point in `plugin.xml`
- Icons leverage existing SVG assets (openspec.svg, spec.svg, delta-spec.svg) with dark variants

## Capabilities

### New Capabilities
- `file-type-recognition`: Custom file type registrations for OpenSpec-specific files, providing distinct icons and type identity in the IDE

### Modified Capabilities
- `plugin-core`: Add file type extension declarations to plugin.xml infrastructure

## Impact

- **plugin.xml**: New `<fileType>` extension declarations
- **New classes**: FileType and FileTypeFactory implementations for each registered type
- **Icons**: Reuse existing `/icons/` SVG assets — no new artwork needed
- **No breaking changes**: Additive only — existing file handling is unaffected
