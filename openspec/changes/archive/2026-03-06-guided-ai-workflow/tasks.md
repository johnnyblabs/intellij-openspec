## 1. Gemini API Provider

- [x] 1.1 Add `GEMINI` to `AiProvider` enum with models (`gemini-2.5-pro`, `gemini-2.5-flash`) and display name "Gemini"
- [x] 1.2 Add `callGemini()` method to `DirectApiService` using the Gemini REST API endpoint (`https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent`) with API key as query parameter
- [x] 1.3 Wire `GEMINI` case into `DirectApiService.generate()` and `testConnection()` switch statements
- [x] 1.4 Update `OpenSpecSettingsPanel` to include Gemini in the provider dropdown and model list
- [x] 1.5 Add `.gemini` to `AiToolDetectionService.TOOL_DIRS` for Gemini IDE detection

## 2. Smart Delivery Method

- [x] 2.1 Add `preferredDeliveryMethod` field to `OpenSpecSettings` (persisted, nullable)
- [x] 2.2 Create `DeliveryMethodResolver` service that determines the delivery method based on priority: saved preference → configured API → detected tools → clipboard fallback
- [x] 2.3 Update `AiToolDetectionService` to expose a method mapping detected tools to suggested clipboard labels (e.g., "Claude Code" → "Copy for Claude Code")

## 3. Workflow Action Panel

- [x] 3.1 Create `WorkflowActionPanel` JPanel component with: change name label, progress indicator (N/M artifacts), and Generate split button
- [x] 3.2 Implement the split button: main button uses `DeliveryMethodResolver` default, dropdown chevron shows all available methods
- [x] 3.3 Wire Generate button to fetch instructions via `ArtifactOrchestrationService`, execute the resolved delivery method, and write the result for Direct API mode
- [x] 3.4 Implement auto-advance: after Direct API generation, refresh DAG status and update panel to show next ready artifact
- [x] 3.5 Implement clipboard/editor completion: show "Done — check for updates" link that re-checks artifact file status
- [x] 3.6 Handle empty states: "Create a change to get started" when no active change, "All artifacts complete" when done

## 4. First-Run AI Setup

- [x] 4.1 Create `AiSetupCard` component — an inline panel showing detected tools and delivery method options
- [x] 4.2 Wire into `WorkflowActionPanel`: show `AiSetupCard` on first Generate click when no preferred method is set; replace with normal button after selection

## 5. Tool Window Integration

- [x] 5.1 Add `WorkflowActionPanel` to `OpenSpecToolWindowPanel` layout between tree and status bar
- [x] 5.2 Wire panel refresh: update on change selection, propose action, tree refresh, and artifact generation events
- [x] 5.3 Update `OpenSpecProposeAction` to trigger panel update after successful propose

## 6. Verify

- [x] 6.1 Build the project and confirm no compilation errors
- [x] 6.2 Verify Gemini provider appears in settings dropdown with correct models
- [x] 6.3 Verify workflow panel shows correct states: no change, ready artifacts, all complete
- [x] 6.4 Verify Generate button uses smart default based on detected tools / configured API
- [x] 6.5 Verify first-run setup card appears and dismisses after method selection
