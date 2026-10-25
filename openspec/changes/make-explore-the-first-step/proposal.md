# Proposal

## Why

OpenSpec's quickstart starts with Explore: explore, propose, review, apply, archive. The upstream `/opsx:explore` command is a thinking step that ends with "Run `/opsx:propose` and this becomes a change". The plugin's UI points the other way. Tracked by the linked tracker entry.

- **Menus:** the OpenSpec menu lists **Explore...** after **Propose...** and **Fast-Forward...**, with no icon. The tool window toolbar has Propose and Fast-Forward and no Explore at all.
- **Empty states and the change menu:** every place that invites a new change offers only Propose:
  - the "Create your first change" card;
  - the Browse pane's empty state;
  - the workflow panel's "No changes yet" card;
  - the `Changes` node's right-click menu.

  There's also no way to explore a change from the change itself, though upstream's explore accepts a change name as its input.
- **Indexing:** Explore isn't available while the IDE is indexing, so an in-panel Explore link would be skipped silently. It reads no indexes.

## What Changes

- **Menu order and icon.**
  - The OpenSpec menu lists **Explore...**, then **Propose...**, then **Fast-Forward...**.
  - Explore gets a magnifier icon and the description "Think through an idea before proposing; nothing is created".
  - The tool window toolbar shows **Explore** right before **Propose**.
  - There's no keyboard shortcut and no IDE main-toolbar entry.
- **"Explore an idea first" beside every Propose invitation.** A secondary link sits next to Propose:
  - on the "Create your first change" card;
  - in the Browse pane's empty state;
  - on the workflow panel's "No changes yet" card;
  - in the `Changes` node's right-click menu.

  Each runs the Explore action through the same action pipeline as the Propose links.
- **"Explore this change..."** on an active change's right-click menu, first in its group. It starts Explore with the change's name as the topic:
  - **with the topic dialog:** the name is filled in;
  - **in the Explore tab:** the name is put in the tab's input.

  It's hidden for specs and archived changes, and gated on the `explore` workflow like the menu action.
- **Explore runs while indexing,** like Propose.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `explore-context`:
  - adds **Explore entry points**: the menu and toolbar placement, the "Explore an idea first" links, and "Explore this change...";
  - **Topic input dialog** gains a pre-filled topic.
- `workflow`: **In-panel links and buttons launch actions through the platform action pipeline** now covers the Explore links too, including while indexing.

## Impact

- **Code:**
  - `plugin.xml`: action order, icon, description, toolbar entry and the new `OpenSpec.ExploreChange` action;
  - `ExploreContextAction`: `DumbAware`, plus a pre-filled-topic entry point;
  - `ExploreTopicDialog`: a pre-fill parameter;
  - a new `ExploreChangeAction`;
  - `GettingStartedPanel`, `SpecPreviewRenderer`/`OpenSpecToolWindowPanel` and `WorkflowActionPanel`: the Explore links;
  - the tree's right-click menus.
- **Tests:**
  - the action order and registration;
  - the links launching Explore;
  - the change-menu action's gating and pre-fill;
  - a uiSmoke journey covering the empty-state link and the toolbar order.

  The workflow parity test now sees a second action gated on `explore`, which is allowed.
- **Docs:** the feature reference, the Marketplace page and the CHANGELOG.
- **Platform compatibility:** IntelliJ IDEA 2024.2 and later stays supported. The icon (`AllIcons.Actions.Find`) and the APIs used exist on 2024.2.
