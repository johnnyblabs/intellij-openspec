## Context

The current `openspec.svg` is a 13x13 purple rounded rectangle with a bold white "S". It's used for the tool window tab and action icons. There is no `pluginIcon.svg` for Marketplace listing.

JetBrains icon guidelines:
- **Tool window icons**: 13x13, monochrome, simple shapes, must work at small sizes
- **Plugin Marketplace icons**: 40x40 `pluginIcon.svg` in `META-INF/`, with optional `pluginIcon_dark.svg`
- Dark variants use `_dark` suffix and are auto-selected by the platform

## Goals / Non-Goals

**Goals:**
- Create a recognizable OpenSpec brand mark that works at both 13x13 and 40x40
- Follow JetBrains Platform icon design conventions
- Support light and dark IDE themes

**Non-Goals:**
- Changing any other plugin icons (artifact, change, spec, etc. — already redesigned)
- Adding an icon to the plugin's action group in the main menu (uses text, not icon)

## Decisions

### Icon concept: Stylized document with spec checkmark

The icon represents a document/spec sheet with a checkmark overlay, conveying "verified specification." This is distinctive from standard IntelliJ icons and communicates the plugin's purpose.

- **40x40 (Marketplace)**: Full detail — document shape with rounded corners, horizontal lines suggesting text, and a small checkmark badge in the bottom-right corner. Uses the existing brand purple (#6C5CE7) as the primary color.
- **13x13 (Tool window)**: Simplified — just the document outline with checkmark, no text lines (too small to render). Monochrome for consistency with IntelliJ's tool window icon style, but retains the purple fill for brand recognition.

**Why not just a letter?** Single-letter icons are generic and don't scale to Marketplace where dozens of plugins compete visually. A pictographic mark is more memorable.

### Dark variant strategy

- **13x13 dark**: Lighter purple (#A29BFE) fill with white stroke, matching the existing `_dark` convention used by other icons in the project
- **40x40 dark**: Same design with inverted brightness — lighter fills on dark assumed background

### File placement follows JetBrains convention

- `META-INF/pluginIcon.svg` and `META-INF/pluginIcon_dark.svg` — JetBrains Platform auto-discovers these
- `icons/openspec.svg` and `icons/openspec_dark.svg` — existing paths, no plugin.xml changes needed

## Risks / Trade-offs

- **SVG rendering at 13x13** → Detail must be minimal. The checkmark badge may need to be a simple corner notch rather than a detailed tick at this size.
- **Color consistency across themes** → The purple brand color works in both themes. Dark variant adjusts luminosity, not hue.
