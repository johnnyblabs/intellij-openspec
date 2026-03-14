## Context

The plugin uses package `com.johnnyb.openspec` across 112 Java files, `plugin.xml`, and `build.gradle.kts`. Before publishing to JetBrains Marketplace the namespace needs to match the vendor domain `johnnyblabs.com`. The plugin ID (`com.johnnyblabs.openspec`) is permanent once published.

## Goals / Non-Goals

**Goals:**
- Rename all Java packages from `com.johnnyb.openspec` to `com.johnnyblabs.openspec`
- Update all configuration files (plugin.xml, build.gradle.kts) to use the new namespace
- Update vendor name from `johnnyb` to `johnnyblabs`
- Pass build and verifyPlugin after rename

**Non-Goals:**
- Changing any runtime behavior
- Renaming sub-packages (they stay as `actions`, `services`, `toolwindow`, etc.)
- Updating OpenSpec spec files or archived changes (they reference capability names, not Java packages)

## Decisions

### Decision 1: Bulk rename via `sed` + directory move

Use `sed -i` to replace `com.johnnyb.openspec` → `com.johnnyblabs.openspec` across all `.java` files and `plugin.xml`. Then `mv` the directory tree from `com/johnnyb/` to `com/johnnyblabs/`. This is simpler and more reliable than file-by-file editing for a pure string replacement across 112+ files.

### Decision 2: Single atomic commit

All rename changes go in one commit. Partial renames would break compilation.

### Decision 3: Update vendor name to `johnnyblabs`

The `<vendor>` tag in `plugin.xml` and the `vendor` block in `build.gradle.kts` change from `johnnyb` to `johnnyblabs` to match the domain identity.

## Risks / Trade-offs

- **[Low risk] Missed references** → Build verification will catch any remaining `com.johnnyb` references. `grep -r` sweep as a final check.
- **[Low risk] IDE caches** → After the rename, IntelliJ may need a project reimport. Not a code issue, just a developer experience note.
