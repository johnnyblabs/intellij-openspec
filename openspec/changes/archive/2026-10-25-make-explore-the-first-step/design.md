# Design

## Context

- **The Explore action today:** `OpenSpec.Explore` (`ExploreContextAction`) is gated on the `explore` workflow.
  - **With Direct API delivery:** `actionPerformed` focuses the Explore tab's input.
  - **Otherwise:** it opens `ExploreTopicDialog`.
  - `runExplore(project, topic)` skips the dialog when a topic is given; tests use it for that.
  - The action isn't `DumbAware`.
- **The four Propose invitations:**

| Where | Code | How it launches Propose |
|---|---|---|
| "Create your first change" card | `GettingStartedPanel`, `NO_CHANGES` state | `ActionLauncher` |
| Browse pane's empty state (no changes) | `SpecPreviewRenderer.emptyState`, href `PROPOSE_HREF`, handled by `OpenSpecToolWindowPanel`'s hyperlink listener | `ActionLauncher` |
| Workflow panel's "No changes yet" card | `WorkflowActionPanel` | `ActionLauncher` |
| `Changes` node's right-click menu | `OpenSpecToolWindowPanel.showContextMenu` | adds `OpenSpec.Propose` |

- **The Getting Started panel** is created only for an uninitialized project. After Initialize it switches in place to the first-change card, and there the project is initialized, so Explore works.
- **The change's right-click menu** (`CHANGE`, `CHANGE_DONE`, `CHANGE_ARCHIVE_READY`) adds actions by id. The panel provides the selected change through `OpenSpecDataKeys.CHANGE_NAME`.

## Goals / Non-Goals

**Goals:**

- Explore comes first wherever Propose is offered, and from the change itself.
- One code path: every entry runs the registered Explore actions through `ActionLauncher` or the action system, so gating, dumb-mode support and profile checks stay in one place.

**Non-Goals:**

- An Explore tab for agent (non-Direct-API) delivery. Rejected: with no response to show, it would duplicate the dialog.
- Changing what Explore sends, how the context is scoped, or the conversation. That's separate tracked work.
- Changing Propose's own behaviour. That's separate tracked work too.

## Decisions

### D1. Menu, toolbar and action metadata
- **In `plugin.xml`:**
  - `OpenSpec.Explore` moves before `OpenSpec.Propose` in `OpenSpec.MainMenu`;
  - it gets `icon="AllIcons.Actions.Find"`, which exists on 2024.2. `Preview` was avoided because it reads as Browse;
  - its description becomes "Think through an idea before proposing; nothing is created";
  - `<reference ref="OpenSpec.Explore"/>` goes into `OpenSpec.ToolWindowToolbar` just before `OpenSpec.Propose`.
- **Toolbar behaviour:** the button behaves like the menu action. It focuses the Explore tab with Direct API and opens the dialog otherwise.

### D2. Explore runs while indexing
`ExploreContextAction` implements `DumbAware`. Context assembly reads files with `java.nio` and runs the CLI, never indexes, so nothing breaks in dumb mode. Without it, `ActionLauncher`'s availability check skips the action while indexing, which the in-panel link requirement forbids.

### D3. "Explore this change..." is a registered action
- **The action:** `OpenSpec.ExploreChange` (`ExploreChangeAction extends OpenSpecBaseAction implements DumbAware`).
  - **Gating:** `getWorkflowId()` returns `explore`. The parity test allows two actions on one workflow.
  - **`update`:** runs the base gating, then shows the action only when the event's data context carries `CHANGE_NAME`.
  - **`actionPerformed`:** calls `ExploreContextAction.exploreTopic(project, changeName)`.
- **Registration:** in `plugin.xml` with no group, so it appears only where code adds it. `showContextMenu` adds it first for active change nodes, then a separator, then Apply and Archive.
- **`exploreTopic(project, topic)`**, a new entry point:
  - **With Direct API delivery:** it puts `topic` into the Explore tab's input (a new `ExplorePanel.setInput(String)`) and focuses it, without sending.
  - **Otherwise:** it opens `ExploreTopicDialog` with the field pre-filled (a new constructor parameter), and delivers on OK as today.
  - `runExplore(project, topic)` keeps its skip-the-dialog meaning for existing callers and tests.

### D4. "Explore an idea first" links
- **Behaviour:** each link is secondary, link-styled and placed after its Propose entry, and runs `OpenSpec.Explore` through `ActionLauncher` with the clicked component as data context, like the Propose links.
- **Where:**
  - **The first-change card:** a `HyperlinkLabel` under the Propose button.
  - **The Browse pane's empty state:** `emptyState(false)` adds `<a href="openspec-action:explore">Explore an idea first</a>` after the Propose link (`EXPLORE_HREF`), and the panel's hyperlink listener launches Explore for it.
  - **The workflow panel's "No changes yet" card:** a `HyperlinkLabel` after "Propose a change".
  - **The `Changes` node's right-click menu:** `OpenSpec.Explore` before `OpenSpec.Propose`. The menu item uses the action's own text.

## Risks / Trade-offs

- **The toolbar is one button wider.** It's acceptable; it now matches the quickstart's order.
- **Two actions on `explore`.** `WorkflowParityContractTest` groups actions by workflow, so this needs no change. `NOT_SURFACED` is untouched.
- **The Explore tab pre-fill doesn't send.** With Direct API, "Explore this change..." only fills the input, so the user can add a question first; sending a bare change name would rarely be useful. This matches the menu action, which focuses the input without sending.
