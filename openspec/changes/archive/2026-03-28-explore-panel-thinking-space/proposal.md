## Why

The ExplorePanel was reworked in `align-explore-action` from a passive context viewer into a results display, but the interaction model is still transactional: modal dialog → single response → done. The explore skill defines a *thinking partner stance* — "curious, not prescriptive", "visual", "adaptive", "patient" — that requires a conversational UI, not a request/response form. The modal `ExploreTopicDialog` disconnects input from output, the `JBTextArea` renders AI markdown responses as raw syntax (killing the visual stance the skill demands), and the empty state is a dead waiting room instead of an invitation to think.

## What Changes

- Replace the modal topic dialog with an always-visible inline input area at the bottom of `ExplorePanel` for Direct API mode — the input becomes the panel's center of gravity
- Replace `JBTextArea` response display with `JEditorPane` rendering markdown as HTML via commonmark-java so headers, tables, ASCII diagrams, code blocks, and emphasis render properly
- Restructure the panel layout to a conversational flow: topic header at top, scrollable rendered response in the middle, input area at the bottom
- Simplify the toolbar to Copy Response and Clear (inline input replaces "New Explore"; user re-types or edits to replace "Refresh")
- Route `ExploreContextAction` Direct API delivery through the panel's inline input path instead of the modal dialog
- Retain `ExploreTopicDialog` for Clipboard and Editor Tab delivery modes where the panel isn't the destination

## Capabilities

### New Capabilities
- `explore-thinking-space`: Inline input, markdown-rendered responses, and conversational panel layout for the Explore tab

### Modified Capabilities
- `explore-context`: Explore panel requirements change from read-only text area with toolbar actions to inline-input conversational layout with markdown rendering

## Impact

- `ExplorePanel.java` — full rework of layout, rendering, and interaction model
- `ExploreContextAction.java` — Direct API path routes through panel input instead of modal dialog
- `ExploreTopicDialog.java` — no code changes, but scoped to non-Direct-API delivery modes only
- `ExplorePanelService.java` — may need new methods to submit topics from the panel
- New dependency: commonmark-java (org.commonmark:commonmark) for markdown-to-HTML conversion — verify availability on IntelliJ classpath or add to `build.gradle.kts`
- Existing `explore-context` spec requirements for panel display, refresh, copy, and auto-refresh are superseded by this change
