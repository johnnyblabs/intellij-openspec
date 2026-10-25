# Tasks

## 1. Menu, toolbar and dumb mode

- [x] 1.1 In `plugin.xml`:
  - move `OpenSpec.Explore` before `OpenSpec.Propose` in `OpenSpec.MainMenu`;
  - give it `AllIcons.Actions.Find` and the new description;
  - add it to `OpenSpec.ToolWindowToolbar` just before `OpenSpec.Propose` (design D1).

  Verify with a light test:
  - in the main menu group's children, Explore comes before Propose and Propose before Fast-Forward;
  - the toolbar group has Explore immediately before Propose;
  - Explore's presentation has an icon.
- [x] 1.2 Make `ExploreContextAction` `DumbAware` (design D2). Verify: a test asserts the action is `DumbAware`, alongside Propose.

## 2. Explore this change

- [x] 2.1 Add `ExploreTopicDialog(project, initialTopic)` and `ExplorePanel.setInput(String)`. Add `ExploreContextAction.exploreTopic(project, topic)` (design D3). Verify with light tests:
  - with Direct API delivery, the Explore tab's input holds the topic, focused and not sent;
  - otherwise the dialog's field holds the topic and stays editable, and OK delivers the edited text.
- [x] 2.2 Add and register `ExploreChangeAction` (`OpenSpec.ExploreChange`). It's gated on `explore` and visible only when the data context carries `CHANGE_NAME`. Add it first to active change nodes' right-click menu, followed by a separator (design D3). Verify:
  - without `CHANGE_NAME` it's hidden;
  - with a profile that excludes `explore` it's disabled;
  - with a change name it starts Explore with that topic;
  - the context menu for a change node starts with it;
  - the menu for a spec or delta-spec node doesn't contain it;
  - `WorkflowParityContractTest` still passes.

## 3. Explore an idea first

- [x] 3.1 Add the "Explore an idea first" links to the first-change card, the workflow panel's "No changes yet" card and the Browse empty state (`EXPLORE_HREF` and its hyperlink handler). Add `OpenSpec.Explore` before Propose in the `Changes` node's menu (design D4). Verify:
  - the empty state with no changes contains the Explore link after the Propose link, and with changes contains neither;
  - activating `EXPLORE_HREF` in the real panel launches Explore, as the Propose link test does for Propose;
  - the `Changes` node's menu lists Explore before Propose.
- [x] 3.2 Add a uiSmoke journey (Journey 15). On a seeded project with no active changes and no Direct API, the tool window offers "Explore an idea first" beside "Propose a change". The toolbar and menu order are checked against the real registered groups by `ExploreEntryPointsTest` (task 1.1), so the journey doesn't repeat that through remote refs. Verify: the journey passes under `caffeinate -dimsu`, and the full uiSmoke suite still passes.

## 4. Docs and verification

- [x] 4.1 Update `docs/feature-reference.md` (the Explore row and the menu/toolbar description), `docs/marketplace-page.md` where it lists actions, and add a CHANGELOG `### Changed` line for users. Verify: `DocumentationHygieneTest` and `MarketplaceListingHygieneTest` pass.
- [x] 4.2 Run `./gradlew build` and `./gradlew verifyPlugin`. The diff adds `com.intellij.*` references. Verify: both pass, with all five IDEs Compatible and only the two known deprecated usages.

## Workflow follow-up

- Retake the Getting Started and Browse empty-state screenshots for the Marketplace listing (post-release checklist).
