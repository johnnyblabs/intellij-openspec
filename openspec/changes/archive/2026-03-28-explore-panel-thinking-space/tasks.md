## 1. Dependency Setup

- [x] 1.1 Add `org.commonmark:commonmark:0.21.0` to `build.gradle.kts` implementation dependencies
- [x] 1.2 Verify the dependency resolves and the project compiles with `./gradlew compileJava`

## 2. Markdown Rendering Utility

- [x] 2.1 Create a `MarkdownHtmlRenderer` utility class with a `render(String markdown)` method that uses commonmark-java `Parser` and `HtmlRenderer` to convert markdown to an HTML fragment
- [x] 2.2 Add a `buildThemeStylesheet()` method that generates a CSS string from current IntelliJ theme colors (`JBColor.foreground()`, `JBColor.background()`, `JBUI.Fonts.label()`) covering body, headings, code/pre, tables, blockquotes, and lists
- [x] 2.3 Add a `wrapInHtml(String cssStylesheet, String htmlFragment)` method that produces a complete HTML document (`<html><head><style>...</style></head><body>...</body></html>`) suitable for `JEditorPane`

## 3. Rework ExplorePanel Layout

- [x] 3.1 Replace `JBTextArea contentArea` with a `JEditorPane` configured with `HTMLEditorKit`, set to non-editable, and wrapped in `JBScrollPane` for the response area
- [x] 3.2 Remove the `CardLayout`/`cardPanel` switching between empty state and result panels — the response area always exists, showing either invitation HTML or rendered response HTML
- [x] 3.3 Add an input panel at the bottom: a `JBTextArea` (3 rows, word-wrapping, placeholder "What would you like to explore?") inside a `JBScrollPane`, with a Send `JButton` to its right, composed in a `BorderLayout` sub-panel
- [x] 3.4 Wire Ctrl+Enter (Cmd+Enter on macOS) key binding on the input `JBTextArea` to trigger submit, and ensure plain Enter inserts a newline
- [x] 3.5 Simplify the toolbar to two buttons: Copy Response (copies raw markdown to clipboard) and Clear (resets panel to invitation state and clears input)
- [x] 3.6 Implement the invitation empty state as muted HTML content rendered in the `JEditorPane` (e.g., "Start exploring — ask a question, describe a problem, or just think out loud.") using the theme stylesheet with muted foreground color

## 4. Panel Interaction Logic

- [x] 4.1 Implement `submitTopic()` method: reads the input area text, disables the input area and Send button, calls `ExploreContextAction.runExplore(project, topic)` to trigger the explore flow
- [x] 4.2 Update `showResult(String topic, String response)` to: store raw markdown in `lastResponse`, render markdown to HTML via `MarkdownHtmlRenderer`, set the `JEditorPane` content, update the topic header, scroll to top, re-enable the input area and Send button, enable Copy Response
- [x] 4.3 Update `showLoading(String topic)` to: display a loading message as muted HTML in the response area, update the topic header, keep input area and Send button disabled
- [x] 4.4 Update `showError(String topic, String error)` to: display error as red-styled HTML in the response area, update the topic header, re-enable the input area and Send button
- [x] 4.5 Implement Clear button handler: reset response area to invitation HTML, clear the input area text, clear `lastResponse` and `lastTopic`, disable Copy Response

## 5. ExploreContextAction Routing

- [x] 5.1 Update `ExploreContextAction.actionPerformed()` to check the resolved delivery mode — if Direct API, activate the Explore panel via `ExplorePanelService.getAndActivate()` and call `focusInput()` on the panel instead of showing `ExploreTopicDialog`
- [x] 5.2 Add a `focusInput()` method on `ExplorePanel` that requests focus on the inline input `JBTextArea`
- [x] 5.3 Verify that Clipboard and Editor Tab delivery modes still show the `ExploreTopicDialog` modal dialog and work end-to-end as before

## 6. Testing and Verification

- [x] 6.1 Verify the inline input area is visible on panel open, accepts text, and submits via Send button and Ctrl+Enter
- [x] 6.2 Verify AI responses render as styled HTML: headers, bold/italic, code blocks, tables, and lists display correctly
- [x] 6.3 Verify theme-aware styling: response looks correct in both Darcula and light themes
- [x] 6.4 Verify Copy Response copies raw markdown (not HTML) to clipboard
- [x] 6.5 Verify Clear resets the panel to invitation state and clears input
- [x] 6.6 Verify Direct API delivery activates the Explore panel and focuses the input instead of showing the dialog
- [x] 6.7 Verify Clipboard and Editor Tab delivery modes still show the modal dialog and work correctly
- [x] 6.8 Verify loading state disables input and shows progress, error state re-enables input with red-styled message
