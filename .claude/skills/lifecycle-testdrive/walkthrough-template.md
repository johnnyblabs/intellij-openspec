# OpenSpec Plugin — Lifecycle Test-Drive

Keep this file open in a split editor and work through the stops in order.
This project was initialized by OpenSpec CLI **1.3.1** on purpose, and carries legacy
`.cursor/commands/openspec-*.md` command files that current CLIs report as leftovers. That is
what makes the Update cleanup flow fire (Stop 3).

> **Automated coverage** (`gradle uiSmoke`, same seeded fixture): presence/wiring of
> Stops 1 (tree + panel), 2 (editor parity incl. the seeded `keyword-in-header` spec),
> 3 (cleanup notice), 4 (Settings schemas), and the Stop 5 archive confirmation guard.
> This manual walkthrough remains the judgment pass —
> wording, layout, feel — not the only safety net.

---

## Stop 1 — Browse tree & workflow chips

Open the **OpenSpec tool window** (right sidebar stripe).

- [ ] Tree shows `Specs ▸ greeting ▸ Requirement: Friendly greeting`
- [ ] Tree shows `Changes ▸ demo-add-farewell` with per-artifact rows
- [ ] The Workflow Action Panel chips read: **✓ proposal · ○ design · ○ specs · − tasks**
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

- [ ] The tree still lists "Friendly greeting" (parsing accepts the casing, like CLI 1.4+)
- [ ] No inspection flags the file; the requirement gutter icon appears
- [ ] Now edit it: move `SHALL` so it appears **only in the header line**
      (e.g. header `### requirement: The system SHALL greet` and body without SHALL).
      Expect the targeted warning — *"move the keyword onto the requirement body line"* —
      with a quick-fix that inserts a body line. Undo when done.

## Stop 3 — Update legacy cleanup

Run **OpenSpec ▸ Update** (menu or tool-window toolbar).

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
- [ ] Run **Update** again → no cleanup notice

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
