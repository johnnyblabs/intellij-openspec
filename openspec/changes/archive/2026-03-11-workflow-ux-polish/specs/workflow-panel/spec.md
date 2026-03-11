## ADDED Requirements

### Requirement: Panel section separators
The WorkflowActionPanel SHALL display lightweight visual separators between its logical sections (header, pipeline, controls, guidance) so that each section is visually distinct.

#### Scenario: Separator lines between sections
- **WHEN** the workflow panel is displayed with an active change
- **THEN** the pipeline row, action button row, and guidance panel SHALL each have a 1px top border using the IDE's theme border color (`JBColor.border()`)

#### Scenario: Hidden sections leave no orphaned separators
- **WHEN** a section is hidden (e.g., guidance panel not visible)
- **THEN** the separator associated with that section SHALL also be hidden and SHALL NOT leave empty space

### Requirement: Theme-aware color constants
The WorkflowActionPanel SHALL define all colors as named `JBColor` constants with explicit light and dark theme values, rather than inline `new Color(...)` calls.

#### Scenario: Color constants used for chip states
- **WHEN** pipeline chips are rendered in any state (DONE, READY, GENERATING, ERROR, BLOCKED)
- **THEN** the foreground color, background color, and border color SHALL each reference a named constant

#### Scenario: Color constants used for guidance text
- **WHEN** guidance text is displayed (success message, watching status, error message)
- **THEN** the text foreground color SHALL reference a named constant

#### Scenario: Duplicate color values consolidated
- **WHEN** the same semantic color is used in multiple locations (e.g., success green in chip DONE state and guidance success message)
- **THEN** both locations SHALL reference the same named constant

### Requirement: Dark mode contrast
The WorkflowActionPanel SHALL ensure all text and visual indicators have sufficient contrast against the Darcula theme background.

#### Scenario: DONE chip text readable in dark mode
- **WHEN** a pipeline chip is in DONE state and the dark theme is active
- **THEN** the green text color SHALL have sufficient contrast against the transparent chip background on the dark tool window surface

#### Scenario: BLOCKED chip text readable in dark mode
- **WHEN** a pipeline chip is in BLOCKED state and the dark theme is active
- **THEN** the gray text color SHALL be lighter than `JBColor.GRAY` to ensure readability against the dark background

#### Scenario: Guidance watching text readable in dark mode
- **WHEN** the guidance watching label is displayed in dark theme
- **THEN** the italic gray text SHALL be visible against the dark panel background

### Requirement: Three-tier font size hierarchy
The WorkflowActionPanel SHALL use a consistent three-tier font hierarchy to establish visual scanning order.

#### Scenario: Primary tier for change name and result messages
- **WHEN** the change name label or a success/failure result message is displayed
- **THEN** the text SHALL use 13f Bold font

#### Scenario: Secondary tier for pipeline and progress
- **WHEN** pipeline chip labels or task progress text is displayed
- **THEN** the text SHALL use 12f Plain font

#### Scenario: Tertiary tier for guidance and hints
- **WHEN** guidance watching text, next-artifact tips, elapsed time, or task hints are displayed
- **THEN** the text SHALL use 11f Plain or Italic font

### Requirement: HiDPI-aware spacing
The WorkflowActionPanel SHALL use `JBUI.scale()` for all spacing values so the layout scales correctly on HiDPI displays.

#### Scenario: Section padding uses scaled values
- **WHEN** padding is applied between panel sections
- **THEN** the padding values SHALL be wrapped in `JBUI.scale()` calls

#### Scenario: FlowLayout gaps use scaled values
- **WHEN** FlowLayout is used for pipeline chips, buttons, or guidance button rows
- **THEN** the horizontal and vertical gap values SHALL be wrapped in `JBUI.scale()` calls

#### Scenario: Vertical struts use scaled values
- **WHEN** `Box.createVerticalStrut()` is used for spacing between components
- **THEN** the strut size SHALL be wrapped in `JBUI.scale()`

### Requirement: Section padding via compound borders
The WorkflowActionPanel SHALL apply vertical padding to each logical section using compound borders rather than standalone strut components, so hidden sections do not leave orphaned whitespace.

#### Scenario: Pipeline section has top padding
- **WHEN** the pipeline row is displayed
- **THEN** it SHALL have top padding via a compound border combining the separator line with empty space

#### Scenario: Guidance section has top padding
- **WHEN** the guidance panel is displayed
- **THEN** it SHALL have top padding via a compound border combining the separator line with empty space

#### Scenario: Hiding a section removes its padding
- **WHEN** a section with a compound border is hidden via `setVisible(false)`
- **THEN** neither its separator line nor its padding SHALL be rendered
