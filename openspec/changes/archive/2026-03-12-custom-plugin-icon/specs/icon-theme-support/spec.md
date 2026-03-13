## ADDED Requirements

### Requirement: JetBrains Marketplace plugin icon

The plugin SHALL include a 40x40 `pluginIcon.svg` and `pluginIcon_dark.svg` in `META-INF/` for JetBrains Marketplace listing, following the JetBrains Platform plugin icon guidelines.

#### Scenario: Marketplace icon discovery
- **WHEN** the plugin is loaded by IntelliJ or submitted to the Marketplace
- **THEN** `META-INF/pluginIcon.svg` SHALL be present as a 40x40 SVG
- **AND** `META-INF/pluginIcon_dark.svg` SHALL be present for dark-themed Marketplace pages

#### Scenario: Marketplace icon visual identity
- **WHEN** the Marketplace icon is displayed
- **THEN** it SHALL depict a document with a checkmark badge using the OpenSpec brand purple (#6C5CE7)
- **AND** it SHALL be visually consistent with the 13x13 tool window icon

## MODIFIED Requirements

### Requirement: Tool window icon renders at 13x13
The tool window icon SHALL render correctly at IntelliJ's 13x13 tool window strip size and use a distinctive pictographic mark rather than a single letter.

#### Scenario: Tool window strip rendering
- **WHEN** the OpenSpec tool window is displayed in the IDE sidebar
- **THEN** the `openspec.svg` icon SHALL be legible and recognizable at 13x13 pixels

#### Scenario: Icon is a pictographic mark
- **WHEN** the tool window icon is rendered
- **THEN** it SHALL depict a simplified document with checkmark, not a letter in a box
