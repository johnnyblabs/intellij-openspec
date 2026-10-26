# Proposal

## Why

The plugin has two unrelated ways to "use Claude", and they share a name:
- **Manage AI Tools → Claude Code** sets up an agent: OpenSpec writes its `/opsx` commands into the project, and the plugin copies prompts for that agent ("Copy for Claude Code").
- **Settings → Direct API → Claude** makes the plugin call the provider's API with the user's key ("Generate via Claude").

A user who adds Claude Code expects to generate in the IDE. Nothing says that's a different feature, the two surfaces never point at each other, and the OpenSpec menu has no way to reach Settings at all. Separately, the tool window lists Explore after Console, although Explore is the first step. Tracked by the linked tracker entry.

## What Changes

- **OpenSpec → Settings…** at the end of the OpenSpec menu and on the tool window toolbar, opening the OpenSpec settings page. Manage AI Tools gets its own icon on the toolbar, so the two aren't both gears.
- **Names that say what each thing is.**
  - The API delivery label becomes "Generate via Claude API" (likewise OpenAI and Gemini).
  - The Manage AI Tools dialog's subtitle says these are coding agents that run OpenSpec's `/opsx` commands, and that generating inside the IDE is Direct API in Settings.
  - Tool names (from OpenSpec's registry) and the delivery dropdown's items are unchanged; reworking that dropdown is tracked separately.
- **Cross-links between the surfaces.**
  - **After adding or configuring a tool** in Manage AI Tools, the notification says what happened ("Claude Code is set up: OpenSpec wrote its /opsx commands to this project. Prompts are labeled 'Copy for Claude Code'; paste them into the agent."). When Direct API isn't configured, it offers **Set up Direct API…**, which opens Settings.
  - **The Direct API section in Settings** says it's optional, that an agent like Claude Code needs no key, and links **Manage AI Tools…**.
- **The Explore tab comes right after Browse**: Browse, Explore, Console, then Coordination when present. This holds when the tab is created with the tool window and when it's created later, after a provider is configured.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `ai-integration`: adds "Settings reachable from the OpenSpec menu" and "Agent tools and Direct API are told apart and linked".
- `explore-context`: adds "Explore tab position".

## Impact

- **Code:**
  - a new `OpenSpecOpenSettingsAction`, registered in `plugin.xml` in the menu and the toolbar; Manage AI Tools' toolbar icon;
  - `DeliveryMethodResolver`'s API label;
  - `ManageAiToolsDialog` (subtitle, set-up notification);
  - `OpenSpecSettingsPanel` (Direct API help text and link);
  - `OpenSpecToolWindowFactory` and `ExplorePanelService` (one helper that places the Explore tab).
- **Tests:** menu and toolbar registration; the resolver label; the set-up notification text and action; the settings help text; tab order on both creation paths.
- **Docs:** the feature reference and the CHANGELOG.
- **Platform compatibility:** IntelliJ IDEA 2024.2 and later stays supported. No new platform APIs beyond `ShowSettingsUtil`, which the plugin already uses.
- **Out of scope:** the delivery dropdown's contents, resetting a saved delivery preference, and a Setup Wizard question about generation. All are tracked separately.
