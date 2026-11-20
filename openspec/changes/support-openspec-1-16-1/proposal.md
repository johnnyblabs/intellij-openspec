## Why

OpenSpec CLI **1.16.1** is the new patch on the 1.16 line, and the plugin should declare it supported and target it. Two of its changes alter how the CLI reads specs:
- Section headings written with a CommonMark closing `#` run (`## Purpose ##`, `## Requirements ##`, `## ADDED Requirements ##`) are now read the same as their plain forms.
- A delta spec that puts a requirement under an unrecognized `## ... Requirements` section (such as `## DEPRECATED Requirements`) now fails `validate`, and archive refuses it.

Neither changes a verdict the plugin reads today, but the plugin's own handling of both is not yet mirrored. This change captures the 1.16.1 behaviour as contract fixtures and pins it in tests. Mirroring the plugin's handling is separate work, tracked in the tracker.

## What Changes

- **Declare the 1.16.1 patch supported.** The `1.16.x` line already covers it. Bump the plugin's target version from `1.16.0` to `1.16.1`.
- **Capture real 1.16.1 contract fixtures** under `fixtures/cli/1.16.1/`, following the recipe in the fixtures README:
  - the parity-corpus twins and `version.txt`;
  - the AI-tool registry and `init --help`;
  - the workflow ids and the design and specs instructions;
  - a change with a requirement under `## DEPRECATED Requirements`, in default mode, showing the error;
  - a main spec whose headings carry closing `#` runs, showing it validates like the plain form.
- **Contract-test the new captures at the parse level:**
  - the unrecognized-section change reads as failing, with the error, in default mode;
  - the closing-run spec reads as passing, the same as its plain twin.
- **Extend the version guards** so they include `1.16.1` in the floor and at-least assertions.
- **Adopt-sync:** with the local CLI at 1.16.1, run `openspec update`, review the diff, and restore any customizations from git.
- **Verdict-parity anchors stay put.** Default stays `1.8.0`, strict stays `1.6.0`, and the archive-drop divergence stays pinned from `1.14.2`.
- **Out of scope:** each is its own tracker entry.
  - mirroring the closing-run heading recognition in the plugin's section matching;
  - mirroring the unrecognized-section error in validate and archive, with and without the CLI.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `plugin-core`: the supported-versions requirement gains the 1.16.1 scenario.

## Impact

- `gradle.properties` (the target version), `fixtures/cli/1.16.1/`, and the contract tests that read the target-scoped fixtures.
- The regenerated OpenSpec skills and commands, which carry the new version stamp.
- No plugin behaviour changes in this change. The two mirroring items above are separate.
