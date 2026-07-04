# OpenSpec Plugin — Lifecycle Test-Drive

Keep this file open in a split editor and work through the stops in order.
This project was initialized by OpenSpec CLI **1.3.1** on purpose — it carries
legacy `.junie/commands/opsx-*.md` files so the new Update cleanup flow fires.

> **Automated coverage** (`gradle uiSmoke`, same seeded fixture): presence/wiring of
> Stops 1 (tree + panel), 2 (editor parity incl. the seeded `keyword-in-header` spec),
> 3 (cleanup notice), 5 (Settings schemas), and the Stop 6 archive confirmation guard. This manual walkthrough remains the judgment pass —
> wording, layout, feel — not the only safety net.

---

## Stop 1 — Browse tree & workflow chips

Open the **OpenSpec tool window** (right sidebar stripe).

- [ ] Tree shows `Specs ▸ greeting ▸ Requirement: Friendly greeting`
- [ ] Tree shows `Changes ▸ demo-add-farewell` with per-artifact rows
- [ ] The Workflow Action Panel chips read: **✓ proposal · ○ design · ○ specs · − tasks**
      (tasks blocked by design + specs — this is the CLI's DAG, not a hardcoded one)

## Stop 2 — Validator parity (case-insensitive headers)

Open `openspec/specs/greeting/spec.md`. The requirement header is deliberately
written lowercase: `### requirement:`.

- [ ] The tree still lists "Friendly greeting" (parsing accepts the casing, like CLI 1.4+)
- [ ] No inspection flags the file; the requirement gutter icon appears
- [ ] Now edit it: move `SHALL` so it appears **only in the header line**
      (e.g. header `### requirement: The system SHALL greet` and body without SHALL).
      Expect the targeted warning — *"move the keyword onto the requirement body line"* —
      with a quick-fix that inserts a body line. Undo when done.

## Stop 3 — Update legacy cleanup (the flow you reported)

Run **OpenSpec ▸ Update** (menu or tool-window toolbar).

- [ ] Console shows the CLI output as before (migration block, exit 0)
- [ ] Instead of silent success: a **"Review legacy cleanup…"** notification appears
- [ ] Open the dialog: 4 files, each with a checkbox + clickable link,
      the CLI's *"No user content to preserve"* quote, and the
      *"never runs --force"* note
- [ ] Click **Remove Selected** → files are deleted (one undo step), then a
      verification `openspec update` runs automatically
- [ ] Expect the **regeneration-loop notice**: this CLI version's junie integration
      re-creates the files it flags — the notice explains it and suppresses future nags
- [ ] Run **Update** again → no re-nag (suppressed while the CLI reports the same set)

## Stop 4 — Send OpenSpec Feedback

Run **OpenSpec ▸ Send OpenSpec Feedback…**

- [ ] Try submitting with an empty message → inline validation blocks it
- [ ] (Optional) A real message actually submits to the maintainers — cancel unless you mean it

## Stop 5 — Schema tooling (Settings)

Open **Settings ▸ Tools ▸ OpenSpec**, Schemas section.

- [ ] `spec-driven` row carries an origin tag: `(built-in)  [package]`
- [ ] Select it → **Validate** → inline "Schema 'spec-driven' is valid."
- [ ] **Open Templates** → four template files open as editor tabs
- [ ] (Optional) **Fork** it as `my-flow`, then Validate the fork ( `[project]` tag appears
      after Refresh); delete one of its `templates/*.md` files and Validate again to see
      the inline error list

## Stop 6 — Finish the lifecycle

Back in the tool window, on `demo-add-farewell`:

- [ ] Create `design.md` by hand (or use Continue/Fast-Forward with Direct API) →
      watch the chips flip as artifacts complete
- [ ] **Verify** → report dialog (completeness gate; semantic checks need an AI bridge)
- [ ] **Archive** → change moves to the tree's *Archive* node

---

*This project is scratch — nothing here touches your real config or repos.
When done, close the sandbox window.*
