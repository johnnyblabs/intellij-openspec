## 1. Explore Prompt Assembly

- [x] 1.1 Create `ExplorePromptService` (project-level service) with a `buildPrompt(String topic)` method that reads the explore skill file from the project (`.claude/commands/opsx/explore.md`, `.augment/commands/opsx-explore.md`, or `.github/prompts/opsx-explore.prompt.md`), falls back to a built-in default, appends assembled context from `ExploreContextService`, and appends the topic
- [x] 1.2 Add a built-in default explore prompt constant covering the core explore stance and guardrails (extracted from the canonical skill definition) for use when no skill file exists

## 2. Topic Input Dialog

- [x] 2.1 Create `ExploreTopicDialog` extending `DialogWrapper` with a single text field, placeholder text "What would you like to explore?", and OK/Cancel buttons
- [x] 2.2 Add `getTopic()` method returning the entered text (empty string if blank)

## 3. Delivery Mode Routing

- [x] 3.1 Add a `generate(String systemPrompt, String userMessage)` overload to `DirectApiService` that sends a raw prompt to the configured AI provider and returns the response text
- [x] 3.2 Rework `ExploreContextAction.actionPerformed()` to: show the topic dialog, build the explore prompt via `ExplorePromptService`, resolve the delivery mode via `DeliveryMethodResolver`, and route to the appropriate delivery path
- [x] 3.3 Implement Clipboard delivery path: copy the assembled explore prompt to the clipboard with notification "Explore prompt copied — paste into your AI tool to start exploring."
- [x] 3.4 Implement Editor Tab delivery path: write the explore prompt to a temporary scratch file (`openspec-explore-prompt.md`) and open it in the editor
- [x] 3.5 Implement Direct API delivery path: send the explore prompt on a background thread, activate the Explore tab, and display the topic + AI response in the reworked panel
- [x] 3.6 Handle Direct API fallback: if the delivery mode is Direct API but no provider is configured, fall back to Clipboard delivery with a notification suggesting the user configure an AI provider

## 4. Rework ExplorePanel

- [x] 4.1 Rework `ExplorePanel` to replace the read-only context viewer with an explore results display: topic header, response content area, and empty state prompting the user to start an explore session
- [x] 4.2 Update toolbar to: New Explore (opens topic dialog and runs explore), Copy Response (copies current response to clipboard), Refresh (re-runs the last explore with the same topic)
- [x] 4.3 Remove the VFS auto-refresh listener, Open in Editor button, and passive context assembly — these are no longer needed
- [x] 4.4 Add a `showResult(String topic, String response)` method for the Direct API path to populate the panel
- [x] 4.5 Add a `showError(String topic, String error)` method to display errors with appropriate styling

## 5. Error Handling

- [x] 5.1 Handle API errors in the Direct API path: display errors in the Explore tab with error styling and show a notification
- [x] 5.2 Show a progress indicator (background task with title "Exploring...") while waiting for the Direct API response

## 6. Testing and Verification

- [x] 6.1 Verify each delivery mode works end-to-end: Clipboard copies the full explore prompt (not just context), Editor Tab opens a scratch file with the prompt, Direct API sends and displays the response in the Explore tab
- [x] 6.2 Verify the topic dialog cancel aborts without delivery
- [x] 6.3 Verify fallback to built-in prompt when skill files are absent
- [x] 6.4 Verify the Explore tab empty state shows when no explore has been run yet
