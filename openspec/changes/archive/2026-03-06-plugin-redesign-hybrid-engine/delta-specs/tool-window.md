# Delta Spec: Tool Window

## MODIFIED

### OpenSpecToolWindowFactory
- Creates tabbed layout: "Browse" tab (tree) + "Console" tab (CLI output)

### OpenSpecToolWindowPanel
- Added CLI status indicator in status bar (green=available, red=not found)
- Added right-click context menus:
  - Change node: Apply, Archive, Create Delta Spec
  - Changes root: Propose
  - Spec Domain: Open File
- Replaced direct SwingUtilities.invokeLater refresh with debounced refresh via Alarm (300ms)

### SpecTreeModel
- Change nodes now show lifecycle status labels: [proposed], [applied]
- Missing artifacts appear as grayed-out nodes (MISSING_ARTIFACT type)
- Delta-spec files shown as child nodes of changes (DELTA_SPEC type)

### SpecTreeCellRenderer
- Status-aware coloring: green for proposed, blue for applied
- Missing artifacts rendered in gray italic
- Added MISSING_ARTIFACT and DELTA_SPEC to icon switch

## ADDED

### OpenSpecConsolePanel
- Dedicated console panel for CLI output within OpenSpec tool window
- Methods: printCommand, printOutput, printError, printSystem, clear
