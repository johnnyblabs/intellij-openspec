## Context

The tool window toolbar is defined declaratively in `plugin.xml` as `OpenSpec.ToolWindowToolbar` (lines 128–136), referencing actions defined in the `OpenSpec.MainMenu` group (lines 83–126). The toolbar currently has 5 buttons: Refresh, Validate, Propose, separator, Apply, Archive. Apply and Archive are change-scoped but have no change context at the toolbar level — when clicked, they prompt the user to select a change or fail if none exists. The Refresh action has no icon, Apply has no icon, and Propose uses a custom `change.svg`.

The toolbar is instantiated in `OpenSpecToolWindowPanel.createActionToolbar()` which looks up the `OpenSpec.ToolWindowToolbar` group from `ActionManager`.

## Goals / Non-Goals

**Goals:**
- Remove Apply and Archive references from the toolbar action group
- Assign standard IntelliJ `AllIcons` icons to Refresh and Propose
- Remove the separator (no longer needed with only global actions remaining)
- Keep Apply and Archive available in the main menu and tree context menus

**Non-Goals:**
- No changes to Apply or Archive action logic — just toolbar removal
- No changes to the workflow panel — panel-lifecycle-completion is a separate change
- No new actions or features
- No changes to custom SVG icons (they stay available for tree nodes and main menu)

## Decisions

### 1. Remove Apply and Archive from toolbar only, keep in main menu

**Decision:** Remove the `<reference ref="OpenSpec.Apply"/>` and `<reference ref="OpenSpec.Archive"/>` lines and the `<separator/>` from the `OpenSpec.ToolWindowToolbar` group. Do not remove them from `OpenSpec.MainMenu`.

**Rationale:** These actions still have value as menu-bar entry points for power users and keyboard-shortcut bindings. The issue is their placement in the toolbar, not their existence.

### 2. Use AllIcons.Actions.Refresh for Refresh

**Decision:** Add `icon="AllIcons.Actions.Refresh"` to the `OpenSpec.Refresh` action definition in plugin.xml.

**Rationale:** This is the standard circular-arrows icon used by every IntelliJ tool window. Users recognize it instantly without thinking. The action currently has no icon at all.

### 3. Use AllIcons.General.Add for Propose

**Decision:** Change the Propose action icon from `icon="/icons/change.svg"` to `icon="AllIcons.General.Add"`.

**Rationale:** The "+" icon is the universal "create new thing" affordance in IntelliJ (used by Database, Maven, Run Configurations, etc.). `change.svg` is meaningful in the tree (where it represents a change node) but not in the toolbar (where the user is thinking "I want to start something new", not "I want a change object"). The custom `change.svg` continues to be used for tree node rendering.

### 4. Keep requirement.svg for Validate

**Decision:** Keep the existing `icon="/icons/requirement.svg"` for the Validate action.

**Rationale:** Validation is an OpenSpec-specific concept (spec format, RFC 2119 compliance, artifact completeness). The custom icon provides brand identity and distinguishes it from IntelliJ's built-in code inspections. `AllIcons.Actions.InspectCode` would work but blends in too much — the custom icon signals "this is an OpenSpec operation".

**Alternative considered:** `AllIcons.Actions.InspectCode`. Rejected — loses brand distinctiveness for the one action that benefits from it.

## Risks / Trade-offs

**[Risk] Users who memorized toolbar button positions** → Only 3 buttons remain in the same order (Refresh, Validate, Propose). Apply/Archive users will find them in the OpenSpec main menu. This is a one-time relearning cost.

**[Risk] Apply has no other quick-access path** → Apply is already available in the workflow panel as a contextual button. The toolbar button was redundant. The main menu entry remains for users who prefer menu navigation.

**[Trade-off] Custom vs standard icons** → Using `AllIcons` for Refresh and Propose means they look "native" but less branded. Keeping `requirement.svg` for Validate provides a balance — two standard icons + one branded icon.
