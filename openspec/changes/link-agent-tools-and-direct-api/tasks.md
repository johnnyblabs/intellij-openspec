# Tasks

## 1. Settings

- [x] 1.1 Add `OpenSpecOpenSettingsAction` (`OpenSpec.Settings`), last in the OpenSpec menu after a separator and last on the tool window toolbar, and give Manage AI Tools the plugin icon (design D1). Verify:
  - the action is registered in both groups at the end;
  - it's enabled with a project and DumbAware;
  - its icon differs from Manage AI Tools';
  - performing it asks `ShowSettingsUtil` for `OpenSpecConfigurable`.

## 2. Names and cross-links

- [x] 2.1 Change the API delivery label to "Generate via <provider> API" (design D2). Verify: the resolver tests expect "Generate via Claude API" and "Generate via OpenAI API"; a saved preference still shows "Generate via API".
- [x] 2.2 Add the set-up notification with its Direct API action, and the dialog subtitle (design D3, D4). Verify:
  - `toolSetUpNotice` names the tool and "Copy for <tool>";
  - it includes the Direct API sentence and action only when no provider is configured;
  - a successful Add raises it, while Update still says "— done".
- [x] 2.3 Rewrite the Direct API help text and add the Manage AI Tools… link (design D5). Verify: the help text says Direct API is optional and that an agent needs no key; the section contains the link.

## 3. Explore tab position

- [x] 3.1 Add `ExplorePanelService.addExploreTab` and use it from the tool window factory and the lazy path (design D6). Verify:
  - with the tool window, the tabs read Browse, Explore, Console;
  - lazy creation after Browse, Console and Coordination inserts Explore second;
  - with no Browse, it appends.

## 4. Docs and gates

- [x] 4.1 Update the feature reference (Settings in the menu, the cross-links, the tab order) and add CHANGELOG lines. Verify: `DocumentationHygieneTest` passes.
- [x] 4.2 Mutation-check: drop the Settings toolbar entry, append the Explore tab instead of inserting it, and drop the Direct API action. Each must fail a test, then be reverted. Run `./gradlew build`, `./gradlew verifyPlugin` and the full uiSmoke suite (`caffeinate -dimsu`). Verify: all green, 5/5 IDEs Compatible.
