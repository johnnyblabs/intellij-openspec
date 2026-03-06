# Proposal: Move Menu to Top Level

## Problem
OpenSpec actions are buried under Tools > OpenSpec, requiring extra navigation. Users expect a dedicated top-level menu for a framework-level plugin.

## Solution
- Move the OpenSpec menu group from the Tools submenu to the main menu bar (before Tools)
- Replace the plain JButton toolbar in the tool window with a proper IntelliJ ActionToolbar that reuses the registered actions
- Add icons to key actions for visual clarity in both the menu and toolbar
