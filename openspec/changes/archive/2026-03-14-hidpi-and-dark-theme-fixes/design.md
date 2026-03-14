## Context

IntelliJ's `JBUI.scale()` converts logical pixels to physical pixels based on the display's scale factor. On a 2x Retina display, `JBUI.scale(400)` returns `800`, ensuring text wraps at the correct visual width. Without it, HTML body widths are interpreted as physical pixels, making text appear too narrow on HiDPI.

The tree renderer uses `JBColor` with separate light/dark values. The current dark theme colors are functional but could be brighter for better readability.

## Goals / Non-Goals

**Goals:**
- All HTML body width values use `JBUI.scale()`
- Dark theme tree colors are easily readable

**Non-Goals:**
- Redesigning the tree renderer
- Changing light theme colors (they're fine)

## Decisions

### Use JBUI.scale() via string concatenation

Same pattern as EmptyStateFactory: `"width:" + JBUI.scale(400) + "px"`. Simple and consistent.

### Brighten dark theme status colors slightly

Current dark green `(100, 210, 100)` → `(120, 220, 120)` — slightly brighter.
Current dark blue `(110, 150, 255)` → `(120, 160, 255)` — slightly brighter.
Current dark gray `(140, 140, 140)` → `(150, 150, 150)` — slightly brighter.
These are subtle adjustments, not a redesign.

## Risks / Trade-offs

- **[None]** Pure visual fixes with no behavioral impact.
