## Why

The tool window toolbar contains 5 buttons (Refresh, Validate, Propose, Apply, Archive) but Apply and Archive are change-scoped actions sitting at the project level. They require an active change to function, two of them have no icons, and Apply duplicates functionality already in the workflow panel. This creates confusion about what each button does and when it's available. The toolbar should contain only project-level operations, with change-scoped actions handled entirely by the workflow panel.

## What Changes

- Remove Apply and Archive action references from the `OpenSpec.ToolWindowToolbar` action group in plugin.xml
- Replace missing/custom icons with standard IntelliJ `AllIcons` equivalents for the three remaining toolbar buttons (Refresh, Validate, Propose)
- Update the Propose action icon from custom `change.svg` to `AllIcons.General.Add`
- Update the Refresh action to use `AllIcons.Actions.Refresh`
- Keep Validate using custom `requirement.svg` (brand identity) or switch to `AllIcons.Actions.InspectCode`
- Remove the separator that divided global and change-scoped actions (no longer needed)

## Capabilities

### New Capabilities

### Modified Capabilities
- `tool-window`: Toolbar contents change — remove Apply and Archive from the toolbar action group, update icon assignments for remaining buttons

## Impact

- `META-INF/plugin.xml` — toolbar action group definition
- `OpenSpecRefreshAction.java` — icon assignment
- `OpenSpecProposeAction.java` — icon assignment
- `OpenSpecValidateAction.java` — icon assignment (if changing)
- Apply and Archive actions remain in the main menu (`OpenSpec.MainMenu`) and tree context menus — only removed from toolbar
