## Why

The plugin's welcome screens use the tiny 13x13 tree-view icon as the hero image — it's barely visible and doesn't create a strong first impression. The 40x40 marketplace icon (`pluginIcon.svg`) has a much better design with a document, text lines, and checkmark badge, but it's only used on the JetBrains Marketplace page. For a good review impression and user onboarding experience, the plugin needs a larger, prominent brand mark in its getting started flow and setup wizard.

## What Changes

- Create a 32x32 brand icon (`openspec-brand.svg` / `openspec-brand_dark.svg`) scaled from the pluginIcon design
- Update `EmptyStateFactory` to render larger icons with better vertical spacing
- Use the brand icon in `GettingStartedPanel` welcome states instead of the 13x13 icon
- Add a "Spec-Driven Development" tagline under the "OpenSpec" title in the getting started flow
- Use the brand icon in `SetupWizardDialog` welcome and done steps

## Capabilities

### New Capabilities

_None_

### Modified Capabilities

- `getting-started-guide`: Adding branded icon and tagline to onboarding screens

## Impact

- 2 new SVG files: `icons/openspec-brand.svg`, `icons/openspec-brand_dark.svg`
- `EmptyStateFactory.java` — support larger icon rendering
- `GettingStartedPanel.java` — brand icon + tagline
- `SetupWizardDialog.java` — brand icon on welcome/done steps
