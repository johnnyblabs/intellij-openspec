# Zero State Welcome Screen

## What
Add a "Get Started" welcome screen in the OpenSpec tool window when the project hasn't been initialized with OpenSpec yet.

## Why
When a user installs the plugin in a non-OpenSpec project, the tool window is hidden entirely (`shouldBeAvailable` returns false). There's no way to discover the tool window or initialize OpenSpec without finding the Init action in the main menu. A zero-state welcome screen makes the plugin discoverable and guides first-time users.

## Scope
- Make the tool window always visible (regardless of whether the project is an OpenSpec project)
- When `isOpenSpecProject()` is false, show a welcome panel with an "Initialize OpenSpec" button and brief explanation
- When the project IS initialized, show the normal tree/workflow view as before
