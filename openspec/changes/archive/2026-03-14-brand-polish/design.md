## Context

The plugin has two icon assets: a 13x13 tree-view icon and a 40x40 marketplace icon. The getting started panel uses the 13x13 icon via `EmptyStateFactory.createPanel()`, which renders it at native size — too small for a hero/welcome context. The setup wizard uses IntelliJ's built-in `AllIcons` and has no custom branding.

## Goals / Non-Goals

**Goals:**
- Create a visually prominent brand mark for onboarding screens
- Consistent branding across GettingStartedPanel and SetupWizardDialog
- Tagline "Spec-Driven Development" to communicate what the plugin does at a glance

**Non-Goals:**
- Redesigning the tree-view icons (they're fine at 13x13)
- Adding animations or complex UI effects
- Changing the plugin's color palette

## Decisions

### Create 32x32 brand icon by scaling the pluginIcon design

The 40x40 `pluginIcon.svg` has the best brand identity (document + text lines + checkmark badge). Scale it to 32x32 with adjusted coordinates and stroke widths. This is larger than the tree icon but appropriate for a centered welcome panel without dominating the UI.

**Alternative considered**: Using the 40x40 icon directly — rejected because IntelliJ's `IconLoader` may scale it unpredictably, and 32x32 is the standard "large icon" size in the platform.

### Update EmptyStateFactory with icon size parameter

Add an overload that accepts an icon size hint or simply increase the spacing around the icon. The current 4px insets are too tight for a 32x32 icon. Use 12px top margin for the icon and 8px between icon and title.

### Add tagline as a styled subtitle

Below the bold "OpenSpec" title, add "Spec-Driven Development" in a smaller, gray font. This communicates the plugin's purpose immediately without requiring users to read a paragraph.

### Brand icon in SetupWizardDialog welcome step

The wizard's welcome step (Step 0) is the first thing users see. Add the brand icon above the welcome text for a polished first impression. Reuse the same icon on the done step (Step 4) for visual bookending.

## Risks / Trade-offs

- **[Low] SVG rendering at 32x32** → IntelliJ's SVG renderer handles arbitrary sizes well. The design uses simple shapes (rects, lines, circles) that scale cleanly.
- **[Low] Dark theme contrast** → Both light and dark variants already exist in the pluginIcon design. The brand icon follows the same pattern.
