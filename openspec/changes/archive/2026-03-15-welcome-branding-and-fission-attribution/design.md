# Design: Welcome Branding and Fission AI Attribution

## Overview
Update the Setup Wizard welcome screen to properly brand the plugin and credit Fission AI.

## Changes
1. Rename the welcome title from "Welcome to OpenSpec" to "Welcome to OpenSpec Plugin" to distinguish this community plugin from the core OpenSpec project.
2. Add a subtle attribution line below the description crediting Fission AI as the creators of OpenSpec.

## Rationale
This plugin supports and builds upon Fission AI's OpenSpec framework. The branding should clearly communicate that this is a plugin for OpenSpec (not OpenSpec itself) while acknowledging the original creators.

## Files Modified
- `src/main/java/com/johnnyblabs/openspec/dialogs/SetupWizardDialog.java`
