## Context

The `EmptyStateFactory.createPanel()` renders description text as a plain `JBLabel(description)`. JBLabel doesn't wrap text — it renders on a single line. When the tool window is narrow (typical for side panels), text gets clipped.

Separately, `GettingStartedPanel` creates a Propose button that fires `OpenSpec.Propose` but never transitions the tool window from the "Get Started" tab to the normal "Browse + Console" tabs after the change is created. The factory (`OpenSpecToolWindowFactory`) only performs this transition after the setup wizard closes.

## Goals / Non-Goals

**Goals:**
- Description text wraps naturally within the tool window width
- After a successful propose from the Getting Started panel, the tool window transitions to the normal tree view

**Non-Goals:**
- Redesigning the empty state cards
- Adding animations or transitions between states

## Decisions

### 1. HTML-wrap description in EmptyStateFactory
Wrap the description string in `<html><body style='width:280px'>...</body></html>` to force text wrapping. The 280px width is a reasonable default for a typical tool window panel. Using a fixed pixel width in the HTML style is the standard IntelliJ approach (used in the setup wizard already).

**Alternative considered:** Using `JBTextArea` with word wrap — heavier component, harder to style consistently with `JBLabel`.

### 2. Pass ToolWindow to GettingStartedPanel for content transition
Add a `ToolWindow` parameter to `GettingStartedPanel`'s constructor. After the Propose button's action completes, re-detect state and if `READY`, replace the tool window content with the normal Browse + Console tabs by calling the same content creation logic used in the factory.

**Alternative considered:** Using a VFS listener to detect the new change directory — too indirect, race-prone, and over-engineered for a button callback.

### 3. Extract content creation into a shared helper
Move the `createNormalContent()` logic into a static method or make `OpenSpecToolWindowFactory` methods accessible, so both the factory and `GettingStartedPanel` can trigger the same transition.

## Risks / Trade-offs

- [Risk] Fixed HTML width (280px) may not suit all tool window widths → Mitigation: 280px is a conservative default; HTML wrapping will break at any width, just won't be pixel-perfect. This matches the pattern already used in `SetupWizardDialog`.
