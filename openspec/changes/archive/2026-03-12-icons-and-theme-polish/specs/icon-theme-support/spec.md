## ADDED Requirements

### Requirement: Dark-theme-aware color constants
All UI components SHALL use `JBColor` with explicit light/dark color pairs instead of raw `Color` constants. This ensures correct rendering in both IntelliJ Light and Darcula themes.

#### Scenario: Tree node status colors in light theme
- **WHEN** the tree is rendered in a light theme
- **THEN** proposed/done nodes SHALL display in green, applied/ready nodes in blue, and missing/blocked/hint nodes in gray

#### Scenario: Tree node status colors in dark theme
- **WHEN** the tree is rendered in a dark theme
- **THEN** the same status colors SHALL use lighter/desaturated variants that are readable on dark backgrounds

#### Scenario: CLI status label in settings panel
- **WHEN** the CLI status label displays "OpenSpec CLI v..." or "OpenSpec CLI not found"
- **THEN** the success color SHALL use `JBColor` with a green pair and the warning color SHALL use `JBColor` with an orange pair

#### Scenario: API test result label
- **WHEN** the API test result label displays success or failure
- **THEN** it SHALL use `JBColor` for the success (green) and failure (red) colors

### Requirement: Distinct icons per tree node type
The tree cell renderer SHALL assign visually distinct icons to each node type so users can differentiate specs, artifacts, delta specs, and missing artifacts at a glance.

#### Scenario: Artifact nodes use artifact icon
- **WHEN** a tree node of type ARTIFACT, ARTIFACT_DONE, ARTIFACT_READY, or ARTIFACT_BLOCKED is rendered
- **THEN** it SHALL display the `artifact.svg` icon (not the spec icon)

#### Scenario: Delta spec nodes use delta-spec icon
- **WHEN** a tree node of type DELTA_SPEC is rendered
- **THEN** it SHALL display the `delta-spec.svg` icon

#### Scenario: Missing artifact nodes use missing-artifact icon
- **WHEN** a tree node of type MISSING_ARTIFACT is rendered
- **THEN** it SHALL display the `missing-artifact.svg` icon

#### Scenario: Existing node types retain their icons
- **WHEN** tree nodes of type SPECS, SPEC_DOMAIN, REQUIREMENT, CHANGES, CHANGE, or ARCHIVE are rendered
- **THEN** they SHALL continue using their current icons (spec, requirement, change, archive)

### Requirement: Dark-theme SVG icon variants
All custom SVG icons SHALL have `_dark.svg` variants that IntelliJ's `IconLoader` auto-selects in dark themes.

#### Scenario: Dark variant auto-selection
- **WHEN** the IDE is using a dark theme and an icon is loaded via `IconLoader.getIcon("/icons/name.svg")`
- **THEN** IntelliJ SHALL automatically resolve to `/icons/name_dark.svg` if it exists

#### Scenario: All custom icons have dark variants
- **WHEN** the icon resources directory is listed
- **THEN** every `.svg` file SHALL have a corresponding `_dark.svg` file

### Requirement: Getting Started panel uses OpenSpec branding
The Getting Started panel SHALL display the OpenSpec icon instead of a generic IntelliJ icon.

#### Scenario: Branded empty state
- **WHEN** the Getting Started panel renders a state card (not initialized, no AI, no changes)
- **THEN** the icon SHALL be the OpenSpec icon loaded from `/icons/openspec.svg`
- **AND** it SHALL NOT use `AllIcons.General.Information`

### Requirement: Tool window icon renders at 13x13
The tool window icon SHALL render correctly at IntelliJ's 13x13 tool window strip size.

#### Scenario: Tool window strip rendering
- **WHEN** the OpenSpec tool window is displayed in the IDE sidebar
- **THEN** the `openspec.svg` icon SHALL be legible and recognizable at 13x13 pixels
