## Context

The WorkflowActionPanel was recently restructured from a `BorderLayout` CENTER+EAST split to a fully vertical `BoxLayout.Y_AXIS` stack, and guidance labels were changed from `JBLabel` to wrapping `JTextArea`. This fixed text truncation but left the visual presentation unpolished — there are no visual separators between sections, colors are ad-hoc `new Color(...)` literals scattered throughout, font sizes are inconsistent, and the vertical stack has no breathing room between logical sections. The panel needs a single polish pass to create clear visual hierarchy before v0.1.0 ships.

The panel is rendered inside an IntelliJ tool window sidebar, typically 250–400px wide. It must look correct in both light (IntelliJ) and dark (Darcula) themes. All color usage must go through `JBColor` for theme awareness.

## Goals / Non-Goals

**Goals:**
- Establish clear visual hierarchy: change name is prominent, pipeline is the focal point, controls are accessible, guidance is secondary
- Add lightweight separators between the five logical sections (header, pipeline, controls, progress/tasks, guidance)
- Consolidate scattered color literals into named constants for consistency and future maintainability
- Ensure all text is readable in both light and dark themes with sufficient contrast
- Use consistent `JBUI.scale()`-based spacing so the panel respects IDE scaling settings

**Non-Goals:**
- No new features or behavioral changes — this is purely visual
- No changes to the pipeline chip interaction model (click, context menu, tooltips)
- No new custom painting or icons — use existing `AllIcons` and standard Swing borders
- No changes to the tool selector, file watcher, or generation logic
- No accessibility audit beyond basic contrast (that's a separate effort)

## Decisions

### 1. Extract color constants instead of inline `new Color(...)` calls

**Decision:** Define a block of named `JBColor` constants at the top of `WorkflowActionPanel` for all colors used in the panel (chip states, guidance text, success/error indicators, progress bar).

**Rationale:** The panel currently has ~20 inline `new JBColor(new Color(...), new Color(...))` calls. Duplicates exist (e.g., success green appears in at least 4 places). Constants make it easy to tune dark mode contrast in one place and keep light/dark pairs consistent.

**Alternative considered:** A separate `WorkflowColors` utility class. Rejected — these colors are specific to this one panel and don't need to be shared. Static constants within the class are sufficient.

### 2. Use `JBUI.Borders` separator lines between sections, not `JSeparator`

**Decision:** Add thin `JBUI.Borders.customLine(JBColor.border(), 1, 0, 0, 0)` top-borders to the pipeline row, action row, and guidance panel to visually separate logical sections.

**Rationale:** `JSeparator` adds a heavyweight component to BoxLayout and fights with alignment. A 1px top border on each section panel is lighter, respects theme border color automatically, and doesn't consume vertical space. This matches how IntelliJ's own tool windows separate sections.

**Alternative considered:** `Box.createVerticalStrut()` with larger values (8–12px). Rejected — spacing alone doesn't create enough visual distinction in a dense panel; a subtle line is clearer.

### 3. Use `JBUI.scale()` for all spacing values

**Decision:** Replace hardcoded pixel values (`4`, `6`, `8`) in borders, struts, and FlowLayout gaps with `JBUI.scale()` equivalents.

**Rationale:** IntelliJ supports HiDPI scaling. `JBUI.scale(4)` returns the correct physical pixels for the user's display scaling. Without this, the panel looks cramped on HiDPI and oversized on 1x. This is standard IntelliJ plugin practice.

### 4. Establish a three-tier font size hierarchy

**Decision:** Use three font size tiers:
- **Primary (13f, Bold):** Change name label, success/failure result messages
- **Secondary (12f, Plain):** Pipeline chip labels, button text, task progress
- **Tertiary (11f, Italic or Plain):** Guidance watching text, hints, elapsed time, next-artifact tip

**Rationale:** The current panel uses 11f for almost everything except the change name (which inherits default bold). This makes it hard to scan — the change name doesn't stand out from guidance text. Three tiers create a scannable hierarchy without going overboard with font variation.

### 5. Add vertical padding between sections using compound borders

**Decision:** Each logical section panel (headerRow, pipelinePanel, actionRow, guidancePanel) gets a compound border combining its separator line with `JBUI.Borders.empty(top, 0, bottom, 0)` padding.

**Rationale:** Using compound borders keeps padding co-located with the section it belongs to, rather than scattered `Box.createVerticalStrut()` calls between sections. It also means hidden sections (e.g., `guidancePanel.setVisible(false)`) don't leave orphaned whitespace.

### 6. Tune dark-mode chip colors for contrast

**Decision:** Audit and adjust the dark-mode half of every `JBColor` pair used in pipeline chips:
- DONE: brighter green text (`100, 210, 100`) against transparent background
- READY: lighter blue background (`35, 50, 75`) with brighter blue border
- BLOCKED: lighter gray (`140, 140, 140`) for visibility against dark backgrounds
- ERROR: slightly brighter red background (`90, 25, 25`) for readability

**Rationale:** The current dark-mode colors were chosen quickly during initial implementation. Several have low contrast against Darcula's `#3C3F41` background — particularly the DONE green (`80, 200, 80` on transparent) and BLOCKED gray (`JBColor.GRAY`).

## Risks / Trade-offs

**[Risk] Color constant extraction touches many lines** → Changes are mechanical (find-replace inline colors with constant references). No logic changes. Each can be verified by visual inspection in both themes.

**[Risk] JBUI.scale() changes spacing from current values** → On 1x displays, `JBUI.scale(4)` returns `4`, so no visible change. On 2x displays, spacing becomes proportional. This is strictly an improvement.

**[Risk] Section separators add visual noise in narrow panels** → Using `JBColor.border()` (the IDE's own border color) keeps separators subtle. They're 1px lines that blend with the IDE chrome, not heavy dividers.

**[Trade-off] Guidance JTextArea font from UIManager vs component** → `createWrappingLabel` currently uses `UIManager.getFont("Label.font")`. This is correct for matching the IDE theme font but may not pick up user-customized editor fonts. This is intentional — guidance text should match IDE UI, not editor content.
