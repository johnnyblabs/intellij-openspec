## 1. Panel Structure & Layout

- [x] 1.1 Refactor `OpenSpecSettingsPanel` constructor to build three sections: "OpenSpec CLI" titled panel, "General" titled panel, and a `JBTabbedPane` with "Tools & Delivery" and "Direct API" tabs
- [x] 1.2 Create the "OpenSpec CLI" section with CLI path field, browse button, detect button, and prominent version/status label with color coding (green for available, warning color for not found)
- [x] 1.3 Create the "General" section with schema profile combo, version override combo, auto-refresh checkbox, and strict validation checkbox
- [x] 1.4 Add HTML description labels at the top of each tab explaining its purpose

## 2. Tools & Delivery Tab

- [x] 2.1 Add detected AI tools display showing each tool with its type indicator ("Claude Code (CLI)", "GitHub Copilot (IDE)")
- [x] 2.2 Add preferred tool dropdown populated from `AiToolDetectionService.getDetectedTools()` with "None" option and type suffixes
- [x] 2.3 Add delivery method dropdown with "Copy to Clipboard", "Open in Editor Tab", and "Generate via API" options
- [x] 2.4 Implement smart defaults: pre-select first detected tool if no preference saved; default delivery to "Generate via API" if API configured, otherwise "Copy to Clipboard"
- [x] 2.5 Add status note beneath delivery method that reflects API configuration state (e.g., "Generate via API" disabled with note when no API key is configured)

## 3. Direct API Tab

- [x] 3.1 Move AI provider dropdown, API key field + Test button, model dropdown, and test result label into the "Direct API" tab
- [x] 3.2 Change provider dropdown to show display names ("Claude", "OpenAI", "Gemini", "None") instead of enum names
- [x] 3.3 Ensure provider change handler still updates model list and key field state correctly within the tab

## 4. Settings Persistence Wiring

- [x] 4.1 Update `OpenSpecConfigurable` to read/write `preferredTool` and `preferredDeliveryMethod` from/to `OpenSpecSettings.State`
- [x] 4.2 Add `getPreferredTool()`, `setPreferredTool()`, `getDeliveryMethod()`, `setDeliveryMethod()` accessors to `OpenSpecSettingsPanel`
- [x] 4.3 Verify `isModified()` and `apply()` in `OpenSpecConfigurable` account for the new fields

## 5. Cross-Tab Coordination

- [x] 5.1 When Direct API provider/key changes on the "Direct API" tab, update the "Generate via API" availability and status note on the "Tools & Delivery" tab
- [x] 5.2 Ensure the delivery method dropdown disables "Generate via API" when provider is "None" or no API key is stored

## 6. Verification

- [x] 6.1 Open settings with CLI detected — verify CLI section shows green status with version
- [x] 6.2 Open settings with no CLI — verify CLI section shows warning state
- [x] 6.3 Switch between tabs — verify each tab shows its help text and correct controls
- [x] 6.4 Configure API key on Direct API tab, switch to Tools & Delivery — verify "Generate via API" becomes available
- [x] 6.5 Save preferred tool and delivery method, reopen settings — verify selections persist
