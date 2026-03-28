## Context

The `align-explore-action` change reworked `ExploreContextAction` from a clipboard-only context copier into a multi-mode delivery system (Clipboard, Editor Tab, Direct API) with a topic dialog and an `ExplorePanel` that displays results. The panel uses `JBTextArea` for response content and a modal `ExploreTopicDialog` for topic input.

The explore skill defines a thinking-partner stance: curious, visual, adaptive, patient. The current panel layout — modal input disconnected from output, raw markdown rendering, passive empty state — doesn't support this stance. AI responses rich with headers, tables, ASCII diagrams, and emphasis render as raw syntax.

The plugin targets IntelliJ Platform 2024.2. The only external dependency is Gson. No markdown rendering library is currently available.

## Goals / Non-Goals

**Goals:**
- Embed an always-visible input area in `ExplorePanel` so Direct API users never leave the panel to start or continue exploring
- Render AI markdown responses as styled HTML so headers, tables, code blocks, emphasis, and ASCII diagrams display properly
- Structure the panel as a conversational layout (topic → response → input) that feels like a thinking space, not a results dump
- Simplify the toolbar to match the new interaction model
- Keep Clipboard and Editor Tab delivery modes working via the existing modal dialog

**Non-Goals:**
- Multi-turn conversation (conversation history, message threading) — layout anticipates this but v1 is single-turn with replacement
- Streaming response display — responses still arrive complete from `DirectApiService`
- Custom CSS theming for rendered markdown beyond IntelliJ's look-and-feel defaults
- Changing `ExplorePromptService`, `ExploreContextService`, or `DirectApiService`

## Decisions

### 1. Add commonmark-java as an explicit dependency

Add `org.commonmark:commonmark:0.21.0` to `build.gradle.kts` implementation dependencies. Use the `org.commonmark.parser.Parser` and `org.commonmark.renderer.html.HtmlRenderer` to convert AI markdown responses to HTML.

**Why:** CommonMark is the standard markdown spec, the library is small (~100KB), has no transitive dependencies, and is actively maintained. IntelliJ's bundled Markdown plugin does not expose its rendering API to other plugins, so we can't reuse it.

**Alternative considered:** Regex-based markdown-to-HTML conversion. Rejected — fragile, incomplete, and a maintenance burden for something a well-tested library handles.

**Alternative considered:** `JBCefBrowser` (JCEF) for rich rendering. Rejected — heavy dependency, overkill for read-only content display, and JCEF availability varies across IDE installations.

### 2. Use JEditorPane with HTMLEditorKit for response rendering

Replace `JBTextArea` with a `JEditorPane` using Swing's built-in `HTMLEditorKit`. Set the editor pane to non-editable, configure it with a stylesheet that inherits from IntelliJ's `JBColor` and `JBUI.Fonts` for theme-aware rendering.

**Why:** `JEditorPane` is available everywhere Swing runs (no platform dependency), handles HTML tables, headings, code blocks, emphasis, and links. It integrates naturally with IntelliJ's Darcula and light themes via a dynamic CSS stylesheet that reads `JBColor.foreground()`, `JBColor.background()`, etc.

**Alternative considered:** `JTextPane` with `StyledDocument`. Rejected — would require manual markdown parsing into styled segments, essentially reimplementing a markdown renderer.

**Alternative considered:** IntelliJ's `MarkdownJCEFHtmlPanel`. Rejected — requires a plugin dependency on `org.intellij.plugins.markdown`, which may not be installed.

### 3. Inline input area replaces modal dialog for Direct API mode

Add a `JBTextArea` (2-3 rows, word-wrapping) at the bottom of `ExplorePanel` with a Send button. When the user types a topic and clicks Send (or presses Ctrl+Enter), the panel calls `ExploreContextAction.runExplore(project, topic)` directly, bypassing the modal dialog.

The input area is always visible: in the empty state it serves as the invitation ("What would you like to explore?" as placeholder text); after a response it stays ready for the next thought.

**Why:** The modal dialog separates input from output — the user types in one place and results appear in another. For Direct API mode, the panel is the destination, so the input should live there. This matches the explore stance: the space itself invites thinking.

**Delivery mode routing change:** `ExploreContextAction.actionPerformed()` checks the resolved delivery mode. If Direct API, it activates the Explore panel and focuses the inline input instead of showing the dialog. If Clipboard or Editor Tab, it shows the existing `ExploreTopicDialog` as before.

### 4. Panel layout: header → response → input (BorderLayout)

```
┌──────────────────────────────────────────┐
│ [Copy Response] [Clear]                  │  NORTH: toolbar (FlowLayout)
├──────────────────────────────────────────┤
│                                          │
│  (Rendered HTML response or              │  CENTER: JBScrollPane wrapping
│   placeholder invitation text)           │          JEditorPane
│                                          │
├──────────────────────────────────────────┤
│ ┌──────────────────────────────┐ [Send]  │  SOUTH: input panel
│ │ What would you like to       │         │    (BorderLayout: JBScrollPane
│ │ explore?                     │         │     wrapping JBTextArea + button)
│ └──────────────────────────────┘         │
└──────────────────────────────────────────┘
```

**Why:** This is the natural conversation layout — content flows down, input stays anchored at the bottom. The `CardLayout` switching between empty state and result panels is removed; the response area always exists, showing either an invitation message (styled as muted placeholder HTML) or the rendered AI response.

### 5. Simplify toolbar to Copy Response and Clear

Remove "New Explore" (the inline input replaces it) and "Refresh" (the user can re-submit or edit their topic). Keep:
- **Copy Response** — copies the raw markdown response text (not HTML) to clipboard
- **Clear** — resets the panel to invitation state and clears the input

**Why:** Fewer buttons, each with a clear purpose. The inline input handles what "New Explore" and "Refresh" did through direct interaction.

### 6. Theme-aware CSS stylesheet for JEditorPane

Build a CSS string at panel construction that reads IntelliJ theme colors:
- `body` — `JBColor.foreground()` color, `JBColor.background()` background, `JBUI.Fonts.label()` font family and size
- `h1, h2, h3` — foreground color, scaled font sizes
- `code` — monospace font, slightly different background (`JBColor.border()` or similar)
- `pre` — monospace, bordered, padding, overflow scroll
- `table` — bordered cells, padding
- `blockquote` — left border, muted color

Rebuild the stylesheet if the theme changes (listen for `LafManager` changes or rebuild lazily on each `showResult`).

**Why:** Static CSS would look wrong in Darcula or custom themes. Reading `JBColor` values at render time ensures the response matches the IDE's current appearance.

### 7. Store raw markdown alongside rendered HTML

`ExplorePanel` keeps `lastResponse` (raw markdown string) for the Copy Response action. The `JEditorPane` displays HTML rendered from this markdown. This separation means copy gives the user portable markdown, not HTML markup.

**Why:** Users copying explore responses will paste into AI tools, documents, or issue trackers — all of which handle markdown better than HTML.

## Risks / Trade-offs

- **HTMLEditorKit rendering fidelity** — Swing's HTMLEditorKit supports HTML 3.2, not full HTML5/CSS3. Complex markdown (nested lists, multi-line table cells, syntax-highlighted code) may not render perfectly. → Mitigated by keeping CSS simple and testing with typical AI response patterns. CommonMark output is clean HTML that stays within HTMLEditorKit's capabilities.

- **commonmark-java dependency size** — Adds ~100KB to the plugin distribution. → Acceptable; the plugin already bundles Gson (~300KB). No transitive dependencies.

- **Theme change responsiveness** — If the user switches themes mid-session, the rendered response may not update immediately. → Mitigated by rebuilding CSS on each `showResult` call. For mid-session theme changes, the Clear + re-explore flow naturally rebuilds.

- **Input area takes vertical space** — The 2-3 row input area reduces the response viewing area. → Mitigated by keeping the input compact (placeholder text collapses visual weight) and the response area scrollable.

- **Ctrl+Enter submit convention** — Users may expect Enter to submit (chat-like) or to insert a newline (text-area-like). → Use Ctrl+Enter to submit (matches IntelliJ conventions for multi-line fields), plain Enter inserts newline. The Send button provides a discoverable alternative.