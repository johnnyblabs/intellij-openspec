# Tasks: getting-started-docs

## 1. Create document skeleton

- [x] 1.1 Create `docs/getting-started-copilot.md` with title, last-verified date, and table of contents
- [x] 1.2 Write the Prerequisites section (IntelliJ 2024.2+, OpenSpec plugin, Copilot, OpenSpec CLI)

## 2. Settings reference (Part 1)

- [x] 2.1 Write the CLI Settings section (path field, Detect button, version override)
- [x] 2.2 Write the General Settings section (profile, auto-refresh, strict validation)
- [x] 2.3 Write the Tools & Delivery section (detected tools, delivery dropdown, mode explanations)
- [x] 2.4 Write the Direct API section (provider, API key, model, Test Connection — noted as optional for Copilot workflow)

## 3. Concepts overview

- [x] 3.1 Write "What is OpenSpec?" brief — spec-driven development, changes as units of work
- [x] 3.2 Write "The Artifact Pipeline" section — DAG order, status indicators, pipeline chip visualization
- [x] 3.3 Write "Where OpenSpec Meets AI" section — OpenSpec generates prompts, AI generates content, role markers explained

## 4. Worked example (Part 2)

- [x] 4.1 Write example setup: initialize OpenSpec in a project (`openspec init`), verify tool window appears
- [x] 4.2 Write Step 1: Propose a change — OpenSpec menu > Propose, name it, show tool window state
- [x] 4.3 Write Step 2: Generate proposal — click Generate, clipboard delivery to Copilot Chat, save response
- [x] 4.4 Write Step 3: Generate design — same flow, show pipeline advancing
- [x] 4.5 Write Step 4: Generate specs — same flow, explain delta specs
- [x] 4.6 Write Step 5: Generate tasks — same flow, show all-complete state
- [x] 4.7 Write Step 6: Implement — follow the tasks, mark complete
- [x] 4.8 Write Step 7: Archive — menu action, spec sync prompt, final state

## 5. Polish and review

- [x] 5.1 Add role markers (OpenSpec vs AI callouts) to every step in the worked example
- [x] 5.2 Add "What's Next" section with links to Direct API guide (future), CLI reference, and README
- [x] 5.3 Verify all settings names, menu paths, and button labels match current codebase
