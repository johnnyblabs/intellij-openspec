# Explore Thinking Space

## Purpose
Conversational thinking-space UI for the Explore tab — inline topic input, markdown-rendered AI responses, and a layout that embodies the explore stance (curious, visual, adaptive, patient).

## ADDED Requirements

### Requirement: Inline topic input

The Explore panel SHALL provide an always-visible text input area at the bottom of the panel where the user can type an explore topic and submit it without leaving the panel.

#### Scenario: Input area visible on panel open
- **WHEN** the user opens the Explore tab for the first time
- **THEN** the panel SHALL display an input area at the bottom with placeholder text "What would you like to explore?" and a Send button

#### Scenario: Submit topic via Send button
- **WHEN** the user types a topic in the input area and clicks the Send button
- **THEN** the panel SHALL initiate an explore request using the entered text as the topic

#### Scenario: Submit topic via keyboard shortcut
- **WHEN** the user types a topic in the input area and presses Ctrl+Enter (Cmd+Enter on macOS)
- **THEN** the panel SHALL initiate an explore request using the entered text as the topic

#### Scenario: Plain Enter inserts newline
- **WHEN** the user presses Enter in the input area without holding Ctrl/Cmd
- **THEN** the input area SHALL insert a newline character (not submit)

#### Scenario: Empty submit starts open exploration
- **WHEN** the user clicks Send or presses Ctrl+Enter with an empty input area
- **THEN** the panel SHALL initiate an explore request with an empty topic (open exploration mode)

#### Scenario: Input area remains visible after response
- **WHEN** the AI response is displayed in the panel
- **THEN** the input area SHALL remain visible and editable at the bottom of the panel

#### Scenario: Input area disabled during loading
- **WHEN** an explore request is in progress
- **THEN** the input area and Send button SHALL be disabled until the response arrives or an error occurs

### Requirement: Markdown-rendered responses

The Explore panel SHALL render AI responses as styled HTML converted from markdown, supporting headers, emphasis, tables, code blocks, lists, and blockquotes.

#### Scenario: Markdown headers render as styled headings
- **WHEN** the AI response contains markdown headers (`#`, `##`, `###`)
- **THEN** the panel SHALL render them as HTML headings with scaled font sizes

#### Scenario: Code blocks render with monospace font
- **WHEN** the AI response contains fenced code blocks (triple backticks)
- **THEN** the panel SHALL render them in a monospace font with a visually distinct background

#### Scenario: Tables render with borders and padding
- **WHEN** the AI response contains markdown tables
- **THEN** the panel SHALL render them as HTML tables with bordered cells and padding

#### Scenario: Emphasis renders as bold and italic
- **WHEN** the AI response contains `**bold**` or `*italic*` markdown
- **THEN** the panel SHALL render them as bold and italic styled text respectively

#### Scenario: Inline code renders distinctly
- **WHEN** the AI response contains inline code (single backticks)
- **THEN** the panel SHALL render it in a monospace font with a slightly different background

#### Scenario: Lists render with proper indentation
- **WHEN** the AI response contains ordered or unordered markdown lists
- **THEN** the panel SHALL render them as HTML lists with appropriate indentation and markers

### Requirement: Theme-aware response styling

The Explore panel's rendered HTML SHALL match the current IntelliJ theme (Darcula, light, or custom) by deriving CSS styles from the IDE's color and font settings.

#### Scenario: Dark theme rendering
- **WHEN** the IDE is using Darcula or another dark theme
- **THEN** the rendered response SHALL use light text on dark background with appropriately styled code blocks, borders, and headings

#### Scenario: Light theme rendering
- **WHEN** the IDE is using a light theme
- **THEN** the rendered response SHALL use dark text on light background with appropriately styled code blocks, borders, and headings

#### Scenario: Style consistency on response display
- **WHEN** a new AI response is displayed via `showResult`
- **THEN** the panel SHALL rebuild the CSS stylesheet from current theme colors before rendering

### Requirement: Conversational panel layout

The Explore panel SHALL use a three-zone vertical layout: topic header at top, scrollable response area in the center, and input area at the bottom.

#### Scenario: Layout structure
- **WHEN** the Explore tab is displayed
- **THEN** the panel SHALL show a toolbar and topic header at the top, a scrollable response area occupying the center, and an input area anchored at the bottom

#### Scenario: Response area scrolls independently
- **WHEN** the AI response content exceeds the visible area
- **THEN** the response area SHALL scroll independently without affecting the toolbar, header, or input area positions

#### Scenario: New response scrolls to top
- **WHEN** a new AI response is displayed
- **THEN** the response area SHALL scroll to the top of the response

### Requirement: Invitation empty state

The Explore panel SHALL display an invitation message in the response area when no explore session has been run, styled as muted placeholder text that communicates the explore stance.

#### Scenario: Initial empty state
- **WHEN** the Explore tab is opened and no explore session has been run
- **THEN** the response area SHALL display an invitation message in muted foreground color

#### Scenario: Clear returns to empty state
- **WHEN** the user clicks the Clear toolbar button
- **THEN** the panel SHALL return to the invitation empty state and clear the input area

### Requirement: Simplified toolbar

The Explore panel toolbar SHALL provide Copy Response and Clear actions.

#### Scenario: Copy Response copies raw markdown
- **WHEN** the user clicks Copy Response and a response has been displayed
- **THEN** the plugin SHALL copy the raw markdown text (not HTML) of the last AI response to the system clipboard and show a notification

#### Scenario: Copy Response disabled without response
- **WHEN** no AI response has been displayed yet (or after Clear)
- **THEN** the Copy Response button SHALL be disabled

#### Scenario: Clear resets panel state
- **WHEN** the user clicks Clear
- **THEN** the panel SHALL clear the response area (showing invitation state), clear the input area, and disable the Copy Response button

### Requirement: Direct API routes through panel input

When the resolved delivery mode is Direct API, the Explore menu action SHALL activate the Explore panel and focus its inline input area instead of showing the modal topic dialog.

#### Scenario: Direct API delivery activates panel
- **WHEN** the user triggers the Explore action and the delivery mode resolves to Direct API
- **THEN** the plugin SHALL activate the Explore tab in the tool window and place focus in the inline input area

#### Scenario: Non-Direct-API delivery shows modal dialog
- **WHEN** the user triggers the Explore action and the delivery mode resolves to Clipboard or Editor Tab
- **THEN** the plugin SHALL show the existing ExploreTopicDialog modal dialog

### Requirement: Loading and error states

The Explore panel SHALL display appropriate visual feedback during API calls and on errors.

#### Scenario: Loading state display
- **WHEN** an explore request is sent to the AI provider
- **THEN** the response area SHALL display a loading message in muted foreground color and the topic header SHALL show the topic being explored

#### Scenario: Error state display
- **WHEN** the AI provider returns an error
- **THEN** the response area SHALL display the error message in error styling (red foreground) and the input area SHALL be re-enabled

#### Scenario: Error state allows retry
- **WHEN** an error is displayed and the user submits a new topic
- **THEN** the panel SHALL clear the error and initiate a new explore request
