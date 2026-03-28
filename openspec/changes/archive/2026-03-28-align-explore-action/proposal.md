## Why

The plugin's Explore action (`ExploreContextAction`) assembles project context and copies it to the clipboard. The OpenSpec `explore` command (`/opsx:explore`) is defined as an interactive AI thinking partner — it investigates the codebase, compares approaches, visualizes with diagrams, and can transition into a proposal when insights crystallize. The plugin's current behavior is a context dump, not an exploration session. We need the Explore menu action to honor what `openspec explore` actually means: an interactive, AI-driven exploration delivered through the plugin's existing delivery modes.

## What Changes

- **Rework `ExploreContextAction`** to prompt for an optional topic, assemble the explore prompt (from the skill definition) together with project context, and deliver through the configured delivery mode (Direct API, Editor Tab, or Clipboard).
- **Add an Explore topic input dialog** so the user can optionally describe what they want to explore before delivery.
- **Direct API delivery**: Send the explore prompt + context to the configured AI provider and stream the response into a console or chat view within the tool window.
- **Editor Tab delivery**: Open an editor tab pre-filled with the explore prompt + context, ready for the user's AI tool.
- **Clipboard delivery**: Copy the explore prompt + context (not just raw context) so when pasted into an AI tool the conversation starts in explore mode.
- **Rework `ExplorePanel`** (the Explore tab in the tool window) from a passive read-only context viewer into the home for interactive explore results — showing the topic, assembled prompt, and AI response when using Direct API delivery.

## Capabilities

### New Capabilities
_None — this change enhances existing explore functionality._

### Modified Capabilities
- `explore-context`: The Explore action gains topic input, prompt assembly, and multi-mode delivery aligned with the OpenSpec explore workflow. The Explore panel is reworked to display explore results instead of raw context.

## Impact

- **Actions**: `ExploreContextAction` — reworked to prompt for topic and route through delivery modes.
- **Services**: `ExploreContextService` — may need a method to assemble explore prompt + context (not just raw context). Alternatively, a new `ExplorePromptService` or prompt assembly in the action itself.
- **UI**: New topic input dialog. Explore tab reworked to display explore results for Direct API delivery.
- **Dependencies**: Reuses `DirectApiService`, `WorkflowActionPanel` delivery infrastructure, `OpenSpecConsoleService`.
