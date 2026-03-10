# Tasks: delivery-ux-redesign

## 1. Simplify settings panel

- [x] 1.1 Remove `buildToolsAndDeliveryTab()` method and all delivery-related fields from `OpenSpecSettingsPanel`
- [x] 1.2 Remove the `JBTabbedPane` — replace with a "Direct API" titled section displayed inline below General
- [x] 1.3 Move `buildDirectApiTab()` contents into a `buildDirectApiSection()` that returns a bordered panel
- [x] 1.4 Remove `deliveryCombo`, `deliveryOptions`, `deliveryStatusLabel`, `detectedToolsLabel` fields
- [x] 1.5 Remove `buildDeliveryOptions()`, `updateDeliveryStatus()`, `getSelectedDeliveryOption()` methods
- [x] 1.6 Remove `getDeliveryMethod()`, `setDelivery()`, `getPreferredTool()` methods (delivery state moves to workflow panel)
- [x] 1.7 Update `OpenSpecConfigurable` to remove delivery-related apply/reset/isModified logic

## 2. Add tool selector to workflow panel

- [x] 2.1 Add a `JComboBox<String>` tool selector field to `WorkflowActionPanel`
- [x] 2.2 Populate it with detected tools (with type labels), Direct API (if configured), Editor Tab, and Clipboard
- [x] 2.3 Place the selector in the button panel area, left of the Generate button
- [x] 2.4 Wire selection changes to update the Generate button label and save preference to `OpenSpecSettings`
- [x] 2.5 On panel load, restore the saved selection from `OpenSpecSettings`
- [x] 2.6 Remove the setup card (`createSetupCard()`, `addSetupOption()`, `setupCard` field, "setup" CardLayout card)
- [x] 2.7 When no tools detected and no API configured, show inline help text in the selector area

## 3. Add Explore context action

- [x] 3.1 Create `ExploreContextAction` in `actions/` that assembles project context (config.yaml, active changes, detected tools, recent specs)
- [x] 3.2 Copy assembled context to clipboard and show notification
- [x] 3.3 Include active change proposal summary if available
- [x] 3.4 Register the action in `plugin.xml` under the OpenSpec menu group (after Propose, before Apply)

## 4. Verify

- [x] 4.1 Run `./gradlew clean build test` — all green
- [ ] 4.2 Verify settings panel renders correctly without tabs
- [ ] 4.3 Verify tool selector in workflow panel works with detected tools
- [ ] 4.4 Verify Explore action appears in menu and copies context
