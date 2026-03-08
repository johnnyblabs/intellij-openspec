## Context

The current `OpenSpecSettingsPanel` builds a single flat `FormBuilder` with all controls in one column separated by thin lines. There is no visual hierarchy, no help text, and no exposure of `preferredTool` or `preferredDeliveryMethod` despite these fields existing in `OpenSpecSettings.State`. Users cannot tell why an API key exists alongside detected tools, or what each is for.

The IntelliJ Platform provides `JBTabbedPane`, `IdeBorderFactory.createTitledBorder()`, and HTML-capable `JBLabel` for building structured settings UIs.

## Goals / Non-Goals

**Goals:**
- Separate CLI/tool concerns from API concerns using a tabbed layout
- Add contextual help text so users understand what each section does and why
- Expose preferred tool and delivery method controls in settings
- Show provider display names instead of enum constants

**Non-Goals:**
- Changing the settings persistence model (`OpenSpecSettings.State` is unchanged)
- Adding new AI providers or modifying `DirectApiService` behavior
- Changing the first-run setup card in `WorkflowActionPanel` (that remains the entry point for first-time users)
- Redesigning the OpenSpec Core section beyond regrouping it

## Decisions

### 1. Tabbed layout for AI configuration

**Decision**: Use a `JBTabbedPane` with two tabs beneath the core settings section.

- **Tab 1 — "Tools & Delivery"**: Detected AI tools list, unified "Deliver via" dropdown with contextual status notes.
- **Tab 2 — "Direct API"**: Provider dropdown (display names), API key field + Test button, model dropdown, test result label.

**Why tabs over titled sections**: The user's mental model is "I either use my own API key OR I use an AI coding tool." Tabs make this an explicit either/or visual, while titled sections imply both must be configured. Tabs also keep the panel compact without needing a scroll pane.

**Alternative considered**: Three titled sections in a scrollable panel. Rejected because it still presents everything at once and doesn't convey the conceptual separation.

### 2. Distinct CLI detection section at the top

**Decision**: The OpenSpec CLI gets its own titled "OpenSpec CLI" section at the very top of the panel with:
- CLI path field + Browse + Detect button
- Status display: "OpenSpec CLI v1.2.0 — /opt/homebrew/bin/openspec" (green) or "OpenSpec CLI not found" (red/gray) — prominent, not an afterthought
- Version override combo (only shown if CLI is detected, allows pinning a spec version)

This is the first thing users see when opening settings — the health of their CLI installation.

**Rationale**: CLI detection is the plugin's foundation. If the CLI isn't working, nothing else matters. Making it visually distinct and top-level communicates this priority.

### 3. General settings section below CLI

**Decision**: Schema profile and the two checkboxes (auto-refresh, strict validation) go in a "General" section between CLI and the tabs.

**Rationale**: These are project-level preferences that apply regardless of AI configuration. Keeping them separate from both CLI and AI avoids clutter.

### 4. Help text via HTML labels

**Decision**: Each tab gets a brief HTML description label at the top:
- Tools & Delivery: *"OpenSpec detected these AI coding tools in your project. Choose your preferred tool and how generated content should be delivered."*
- Direct API: *"Use your own API key to generate specs and artifacts directly. Optional if you use an AI coding tool."*

**Rationale**: Standard IntelliJ pattern. `JBLabel` supports `<html>` content. Keeps help inline without tooltips or popups.

### 5. Provider display names in combo box

**Decision**: Show "Claude", "OpenAI", "Gemini", "None" in the provider dropdown instead of "CLAUDE", "OPENAI", "GEMINI", "NONE". Use `AiProvider.getDisplayName()` which already exists.

### 6. Unified delivery dropdown (replaces separate "preferred tool" and "delivery method")

**Decision**: Replace the two separate dropdowns (preferred tool + delivery method) with a single "Deliver via" dropdown that combines both concepts. Each option maps to both a `DeliveryMode` and an optional tool name for persistence.

Options are built dynamically:
- One "Copy for {ToolName}" entry per detected tool (e.g., "Copy for Claude Code", "Copy for Windsurf")
- "Copy to Clipboard" as generic fallback when no tools detected
- "Open in Editor Tab" — always available
- "Generate via API" — always available, with warning if no API configured

A contextual status label beneath the dropdown explains what the selected option does:
- Clipboard: "Prompt copied to clipboard, ready to paste into {tool}."
- Editor tab: "Generated content opens in a new editor tab for review."
- Direct API: "Artifacts will be generated via {provider} API." or warning if unconfigured.

**Why unified over separate**: "Preferred tool" only affected button labels — it wasn't a real independent setting. Users think in one decision ("I want to copy for Copilot") not two ("set preferred tool to Copilot AND delivery method to clipboard"). The unified dropdown eliminates invalid combinations and confusion.

**Alternative considered**: Two separate dropdowns (preferred tool + delivery method). Rejected because the "preferred tool" concept was misleading — users expected it to influence generation behavior, but it only changed labels. The two-control approach also allowed invalid combinations (e.g., IDE tool selected with "Generate via API").

**Smart defaults on first open**: Pre-select "Copy for {first detected tool}" if tools found. If no tools but API configured, select "Generate via API". Otherwise "Copy to Clipboard".

## Risks / Trade-offs

**[Taller panel]** → The tabbed layout keeps height manageable since only one tab's content is visible at a time. No scroll pane needed.

**[Tab switching to test API]** → Users must switch to the Direct API tab to configure/test their key, then back to Tools & Delivery to set delivery method. → The delivery method dropdown automatically reflects API configuration state regardless of which tab you're on, so this is a minor friction.

**[Detected tools may change]** → If user adds/removes `.claude/` etc. between settings opens, the delivery dropdown is rebuilt from current detection. If the saved preference referenced a now-missing tool, it falls back to the first available option.
