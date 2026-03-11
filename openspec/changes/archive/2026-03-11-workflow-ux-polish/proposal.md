## Why

The workflow panel layout was just restructured to fix text truncation, but the visual presentation still needs a polish pass for v0.1.0. Dark mode colors need
  contrast tuning, guidance text sizing and spacing need refinement, and the vertically stacked layout needs visual hierarchy (separators, section spacing) so the
  panel reads clearly in both narrow and wide tool windows

## What Changes

- Add visual separators between panel sections (header, pipeline, controls, guidance)
  - Tune dark mode colors for pipeline chips, guidance text, and progress indicators
  - Adjust font sizes and weights for visual hierarchy (change name prominent, guidance secondary, hints tertiary)
  - Ensure buttons wrap gracefully in narrow tool windows
  - Add consistent padding and margins across all panel states (generating, watching, complete, error)
  - Test and fix any remaining text clipping in all guidance states (generate, apply, sync retry)

## Capabilities

### New Capabilities
<!-- Capabilities being introduced. Use kebab-case identifiers (e.g., user-auth, data-export). Each creates specs/<name>/spec.md -->

### Modified Capabilities
<!-- Existing capabilities whose REQUIREMENTS are changing. Use existing spec names from openspec/specs/. -->

## Impact

<!-- Affected code, APIs, dependencies, systems -->
