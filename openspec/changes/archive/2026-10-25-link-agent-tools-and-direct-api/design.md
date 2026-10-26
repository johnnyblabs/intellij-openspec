# Design

## Context

- **Opening Settings** already happens in two places, `OpenSpecNotifier.openSettingsAction` and the profile status bar widget, through `ShowSettingsUtil.showSettingsDialog(project, "OpenSpec")`. The page is a project configurable (`OpenSpecConfigurable`, id `com.johnnyblabs.openspec.settings`). There's no anchor to a section inside it.
- **Delivery labels:** `DeliveryMethodResolver` builds "Generate via " + `AiProvider.getDisplayName()` ("Claude", "OpenAI", "Gemini") and "Copy for <tool>".
  - The delivery dropdown's items ("<tool>  [CLI]", "Direct API  [API]") are parsed back by `parseToolSelection` and `restoreToolSelection`, so renaming them risks breaking saved choices. They stay as they are; the dropdown rework is tracked separately.
- **Manage AI Tools** (`ManageAiToolsDialog`): adding or configuring a tool runs `init --tools <id>` through `runCliAction`, which on success says "<title> — done".
- **Tab order:** the tool window adds Browse, Console, then Explore (`OpenSpecToolWindowFactory.createNormalContent`). `ExplorePanelService.getAndActivate` appends the tab again when it creates it lazily. Coordination is added on its own.
- **Placement**, per plugin-ui-specialist:
  - Settings goes last in the menu, after a separator; it's configuration, not a lifecycle step.
  - On the toolbar it goes last, and Manage AI Tools changes from the gear to a plugin icon.
  - The set-up message is a notification, because the modal dialog closes and a notification outlives it.

## Goals / Non-Goals

**Goals:**

- **Settings** can be reached from the OpenSpec menu and the toolbar.
- **Each surface** says what it is and points to the other.
- **Explore** sits beside Browse.

**Non-Goals:**

- Changing the delivery dropdown's items or how a saved preference is shown or reset.
- Renaming upstream tool names, or the provider names in the provider dropdown.
- Deep-linking to the Direct API section; the page has no anchor.

## Decisions

### D1. The Settings action
- **The action:** `OpenSpecOpenSettingsAction` (`OpenSpec.Settings`, "Settings…", `AllIcons.General.Settings`). It's always enabled with a project, like Manage AI Tools, and `DumbAware`. It calls `ShowSettingsUtil.getInstance().showSettingsDialog(project, OpenSpecConfigurable.class)`.
- **Placement:** last in `OpenSpec.MainMenu` after a separator, and last on `OpenSpec.ToolWindowToolbar`.
- **Manage AI Tools' icon** becomes `AllIcons.Nodes.Plugin`.

### D2. The API label
`DeliveryMethodResolver` step 2 returns "Generate via " + provider + " API". The other steps don't change.

### D3. Set-up notification
- **The call:** `runCliAction` takes the tool's name when the command sets a tool up (Add or Configure). On success it raises `toolSetUpNotice(tool)`:
  > `<tool> is set up: OpenSpec wrote its /opsx commands to this project. Prompts are labeled "Copy for <tool>"; paste them into the agent.`
- **When Direct API isn't configured,** the notice appends "To generate inside the IDE instead, set up Direct API." and carries a **Set up Direct API…** action that opens the settings page.
- **Update** keeps its "— done" message; it isn't about one tool.
- **Testing:** the text is a static method, so tests check it without the CLI.

### D4. Dialog subtitle
"AI coding agents that run OpenSpec's /opsx commands. To generate inside the IDE instead, set up Direct API in Settings."

### D5. The Direct API section
- **The help text:** "Generate specs and artifacts inside the IDE with your own API key. Optional: an agent like Claude Code needs no key. Prompts are copied for it to paste."
- **The link:** a `HyperlinkLabel` reading "Manage AI Tools…" opens `ManageAiToolsDialog` for the project. It's a modal dialog over the modal settings dialog, which the platform allows.
- **Testing:** the text is a constant, so a test can check it.

### D6. One place decides the Explore tab's position
- **The helper:** `ExplorePanelService.addExploreTab(ContentManager, Content)` inserts the tab at the index after Browse (`findContent("Browse")`). It falls back to appending when Browse is absent.
- **Both creation paths** use it.
- **The order:** the factory now adds Browse, Console, then Explore through the helper, so the result is Browse, Explore, Console; Coordination is appended later.

## Risks / Trade-offs

- **The label change** alters "Generate via Claude" in the workflow panel's button. Tests that assert it change with it. A saved preference keeps `DeliveryMode`'s own "Generate via API".
- **A modal over the modal settings dialog** is allowed, but Manage AI Tools' "refresh the tool window afterwards" step only runs from its action. The link calls the same refresh after the dialog closes.
