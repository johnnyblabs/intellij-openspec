# OpenSpec Plugin — Lifecycle Test-Drive

Keep this file open in a split editor and work through the stops in order.
This project was initialized by OpenSpec CLI **1.3.1** on purpose, and carries legacy
`.cursor/commands/openspec-*.md` command files that current CLIs report as leftovers. That is
what makes the Update cleanup flow fire (Stop 3).

> **Automated coverage** (`gradle uiSmoke`, same seeded fixture, without `--deltas-demo`): presence and wiring of
> Stops 1 (tree + panel, Journey 1), 1b (a change's badged deltas, Journey 9, which seeds its own ADDED delta),
> 2 (editor parity incl. the seeded `keyword-in-header` spec, Journey 4; Search Everywhere, Journey 8),
> 3 (cleanup notice, Journey 2), 4 (Settings schemas, Journey 3), and the Stop 5 archive guard (Journey 5).
> This manual walkthrough remains the judgment pass —
> wording, layout, feel — not the only safety net.

---

## Stop 1 — Browse tree & workflow chips

Open the **OpenSpec tool window** (right sidebar stripe).

- [ ] The tree's only top-level node is `Changes`: specs aren't listed here (open them from the
      Project View, or find them with Search Everywhere, Stop 2)
- [ ] Tree shows `Changes ▸ demo-add-farewell` and `demo-modify-greeting`, each with per-artifact rows
- [ ] Select `demo-add-farewell`: the Workflow Action Panel chips read **✓ proposal · ○ design · ○ specs · − tasks**
      (tasks blocked by design + specs — this is the CLI's DAG, not a hardcoded one)

## Stop 1b — Change Deltas pane

The pane right of the tree shows a change's **deltas**: what the change will add, modify or
remove in the specs when it's archived.

- [ ] With nothing selected, the pane explains itself under a **Change Deltas** header
- [ ] `Changes` was already expanded when the tool window opened
- [ ] Click `demo-modify-greeting` → **Change Deltas — demo-modify-greeting**, with a
      **MODIFIED** badge on *Friendly greeting* and both of its scenarios
- [ ] Click its **Preview diff vs current main spec** link → the greeting diff opens
- [ ] Click its `proposal` → the same deltas, plus **Open proposal.md in editor** under the
      header; the link opens the file
- [ ] Click a `demo-add-farewell` artifact that doesn't exist yet (e.g. `tasks`) → that
      change's deltas view, with no open link (this change has no spec deltas yet)

## Stop 2 — Validator parity (case-insensitive headers)

Open `openspec/specs/greeting/spec.md`. The requirement header is deliberately
written lowercase: `### requirement:`.

- [ ] **Search Everywhere** (double Shift), type `greet the user by name` → it lists the requirement
      **Friendly greeting**, parsed from the lowercase header (the casing is accepted, like CLI 1.4+)
- [ ] No inspection flags the file; the requirement gutter icon appears
- [ ] Now edit it: move `SHALL` so it appears **only in the header line**
      (e.g. header `### requirement: The system SHALL greet` and body without SHALL).
      Expect the targeted warning — *"move the keyword onto the requirement body line"* —
      with a quick-fix that inserts a body line. Undo when done.

## Stop 3 — Update legacy cleanup

Run **OpenSpec ▸ Update OpenSpec…** (menu or tool-window toolbar).

**CLI 1.15 or later: the update preview comes first.**
- [ ] The **OpenSpec Update Preview** dialog opens: a summary line, OpenSpec's note that legacy artifacts were
      found, and the changes grouped by tool (deletes first). If this is the first update in a fresh HOME,
      `~/.config/openspec/config.json` appears under **Outside this project**
- [ ] Click **Cancel** → nothing changes (the three `.cursor/commands/openspec-*.md` files are still there)
- [ ] Run **Update OpenSpec…** again and click **Apply**

- [ ] Console shows the CLI output, including a **"Files to remove"** block listing the three
      `.cursor/commands/openspec-*.md` files, exit 0
- [ ] Instead of silent success: a **"Review legacy cleanup…"** notification appears ("listed 3 legacy
      file(s) for removal"; it no longer claims the files are safe to remove)
- [ ] Open the dialog: 3 files, each with a checkbox and a clickable link (opens the file), the CLI's
      own guidance line quoted, and the *"never runs --force"* note
- [ ] **CLI 1.14 or later:** the CLI warns *"These files will be deleted entirely. Back up any custom
      content before proceeding"*, so **no box starts ticked** and the dialog says to choose. On older
      CLIs it says *"No user content to preserve"* and all start ticked
- [ ] Tick the files and click **Remove Selected** → files are deleted (one undo step), then a
      verification `openspec update` runs automatically and reports no remaining cleanup
- [ ] Run **Update** again → no cleanup notice; on CLI 1.15+ it says the project is already up to date instead of
      showing the preview

## Stop 4 — Schema tooling (Settings)

Open **Settings ▸ Tools ▸ OpenSpec**, Schemas section.

- [ ] The list shows `spec-driven` with its origin tag `(built-in)  [package]` straight away,
      with no reopen needed; the default-schema dropdown is filled too
- [ ] With nothing selected, **Fork**, **Validate** and **Open Templates** are greyed out
      (hover: "Select a schema in the list first"); **New** and **Refresh** are enabled
- [ ] Select `spec-driven` → **Validate** → inline "Schema 'spec-driven' is valid."
- [ ] **Refresh** → the list reloads, the validation result disappears, and the selection clears
- [ ] Select `spec-driven` → **Open Templates** → four template files open as editor tabs
      (behind the modal Settings dialog; they show once you close it)
- [ ] (Optional) **Fork** it as `my-flow` → `my-flow` appears with a `[project]` tag and its
      `schema.yaml` opens; delete one of its `templates/*.md` files and Validate it to see the
      inline error list
- [ ] (Optional) **New...** with a description and two artifacts → its `schema.yaml` opens with
      that description and only those artifacts

## Stop 4b — Revise Change

Revise hands a change back to your AI agent's own update workflow. The demo project may not have that
workflow's files yet; if not, the first check shows what happens without them.

- [ ] Right-click `demo-add-farewell` → **Revise Change...** sits right under **Explore This Change...**
- [ ] With no update workflow installed: nothing is copied, and the notice offers **Update OpenSpec…**
- [ ] Run **Update OpenSpec…** (apply the preview), then **Revise Change...** again → the dialog asks
      **What should change?**; type `drop the export feature` → OK → the clipboard holds your tool's form,
      e.g. `/opsx:update demo-add-farewell drop the export feature`, and the notice names the tool
- [ ] Repeat with the note blank → the clipboard holds just the invocation and the change name
- [ ] The edit icon in the workflow panel's header opens the same dialog for the selected change
- [ ] **OpenSpec > Revise Change...** sits between Continue and Apply; its description differs from
      **Update OpenSpec…**'s

## Stop 5 — Finish the lifecycle

Back in the tool window, on `demo-add-farewell`:

- [ ] Create `design.md` by hand (or use Continue/Fast-Forward with Direct API) →
      watch the chips flip as artifacts complete
- [ ] **Verify** → report dialog (completeness gate; semantic checks need an AI bridge)
- [ ] **Archive** → pick `demo-add-farewell` in the change chooser (there are two active
      changes) → the change moves to the tree's *Archive* node

---

*This project is scratch — nothing here touches your real config or repos.
When done, close the sandbox window.*
