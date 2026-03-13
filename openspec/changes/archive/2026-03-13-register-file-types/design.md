## Context

OpenSpec projects use three categories of files with special meaning:
1. **`.openspec.yaml`** — change metadata files with a unique, project-wide filename
2. **`spec.md`** under `openspec/specs/`  — main specification files
3. **`spec.md`** under `openspec/changes/*/specs/` — delta specification files

Currently all appear as generic YAML or Markdown. The IDE provides two extension points for customizing file appearance: `FileType` (global file type by name/extension) and `IconProvider` (per-file icon override based on context like path).

## Goals / Non-Goals

**Goals:**
- `.openspec.yaml` files recognized as a distinct file type with OpenSpec icon everywhere (tabs, project tree, search)
- `spec.md` files under `openspec/specs/` show the spec icon in the project tree
- `spec.md` files under `openspec/changes/*/specs/` show the delta-spec icon in the project tree

**Non-Goals:**
- Custom editor or syntax highlighting for these files (they remain YAML/Markdown)
- Registering `proposal.md`, `design.md`, `tasks.md` as distinct types (future work)
- File templates in the New File dialog

## Decisions

### Decision 1: FileType for `.openspec.yaml`, IconProvider for spec files

`.openspec.yaml` has a globally unique filename — safe to register as a `FileType` via `<fileType>` in plugin.xml. This gives full IDE integration: icon in tabs, project tree, search results, file type filters.

`spec.md` is too generic to register as a global file type — it would affect every `spec.md` in any project. Instead, use an `IconProvider` that checks whether the file lives under `openspec/specs/` or `openspec/changes/*/specs/` and returns the appropriate icon. `IconProvider` only affects the project tree/navigation, which is sufficient since editor tabs still show the Markdown icon (acceptable trade-off).

**Alternative considered:** Register all three as FileTypes with filename patterns. Rejected because `spec.md` is not unique enough — would hijack the icon for unrelated projects.

### Decision 2: Extend YAML for `.openspec.yaml` FileType

The `.openspec.yaml` FileType should extend `YAMLFileType` (or associate with YAML language) so that syntax highlighting, formatting, and YAML inspections continue to work. Implemented as a lightweight `FileType` subclass that provides the custom icon but delegates language support to YAML.

**Alternative considered:** Plain `FileType` with no language association. Rejected — would lose YAML syntax highlighting.

### Decision 3: Reuse existing icon assets

All needed icons already exist in `/icons/`: `openspec.svg`/`openspec_dark.svg`, `spec.svg`/`spec_dark.svg`, `delta-spec.svg`/`delta-spec_dark.svg`. No new artwork needed.

## Risks / Trade-offs

- **[Risk] IconProvider only affects project tree, not editor tabs** → Acceptable for v0.2.0. Editor tab icons require FileType, which isn't viable for generic `spec.md` filenames. Could revisit with a virtual file type system later.
- **[Risk] IconProvider ordering conflicts with other plugins** → Mitigated by checking OpenSpec-specific paths; returns `null` for non-matching files so other providers take precedence.
