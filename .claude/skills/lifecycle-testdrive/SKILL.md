---
name: lifecycle-testdrive
description: Seed a disposable OpenSpec demo project and launch the plugin in a sandbox IDE (runIde) for a manual walkthrough of the change lifecycle. Use when the user wants to test-drive the plugin UI, verify a change works in the real IDE, or asks for a sandbox/manual walkthrough.
---

# Lifecycle test-drive: seeded sandbox + in-IDE walkthrough

Launches the plugin in a sandbox IDE with a **pre-seeded demo project** that exercises
the change lifecycle end-to-end, and puts the walkthrough checklist **inside the project**
(`WALKTHROUGH.md`) so it can be read in a split editor next to the tool window.

The demo project is deliberately seeded to trigger real behavior:

- Initialized by an **older OpenSpec CLI (1.3.1)** with the `junie` tool, plus **legacy
  `.cursor/commands/openspec-*.md` command files**. Current CLIs (1.13, 1.14, …) list the Cursor
  files under `openspec update`'s "Files to remove", so the Update action's legacy-cleanup flow
  fires. The Junie files are regenerated in place instead.
- A main spec written with a **lowercase `### requirement:` header** → exercises the
  CLI-1.4 case-insensitive parsing parity.
- A change with **proposal done, design/specs ready, tasks blocked** → the workflow
  chips show all three DAG states.

**Steps**

1. **Pick a fresh demo directory.** Always a NEW directory per test-drive (per-project
   sandbox state like the cleanup-dismissal memory is keyed to the project path; a fresh
   path guarantees a clean run). Use the session scratchpad or `$TMPDIR`:

   ```bash
   DEMO="${TMPDIR:-/tmp}/openspec-lifecycle-demo-$(date +%s)"
   ```

   (Do not create the directory — the seeding script insists on a fresh path.)

2. **Seed the project with the shared script** — `scripts/seed-lifecycle-demo.sh` is the
   single source of truth for the demo shape, shared with the automated UI smoke
   journeys (uiTest fixtures) so the manual and automated environments cannot drift:

   ```bash
   <repo>/scripts/seed-lifecycle-demo.sh "$DEMO" --deltas-demo
   ```

   `--deltas-demo` adds a second change, `demo-modify-greeting`, with a MODIFIED delta on
   `greeting`, for the walkthrough's Change Deltas stop. It is opt-in because the UI smoke
   fixture is captured without it: with two active changes, Archive opens a change chooser.

   (It refuses an existing directory — see step 1. Note: `openspec validate` on the
   seeded change reports "no deltas" — that is authentic mid-lifecycle state, not a
   seeding bug; the workflow chips read `openspec status`, which shows 1/4 done.)

3. **Copy the walkthrough into the project** so it is readable inside the sandbox:

   ```bash
   cp "<this skill's directory>/walkthrough-template.md" "$DEMO/WALKTHROUGH.md"
   ```

4. **No sandbox workaround is needed.** The bundled Gradle plugins' Java 25 error balloon on
   the 2024.2 target is handled by the build: `prepareSandbox` disables those plugins in the
   `runIde` sandbox (`disabledPlugins` in `build.gradle.kts`). Don't hand-write
   `disabled_plugins.txt`, because `prepareSandbox` overwrites it on every launch. If the
   balloon still appears, the build setting has regressed; it's harmless platform noise either way.

   Run only **one** sandbox at a time. A second `runIde` while one is open boots a second IDE
   on the same sandbox. Quit the running sandbox, or stop its background task, before relaunching.

5. **Launch, opening the demo project directly** (run in background; first boot takes a
   minute or two):

   ```bash
   ./gradlew runIde --args="$DEMO"
   ```

6. **Hand over.** Tell the user: the IDE opens straight into the demo project; open
   `WALKTHROUGH.md` (project root), drag its tab into a right split, and work down the
   stops. Warn that the walkthrough's feedback stop REALLY submits to the OpenSpec
   maintainers if a message is sent — test the empty-message validation and cancel.

**Guardrails**

- The demo project is scratch — never seed it inside the plugin repo or any real project.
- Never reuse a demo directory across test-drives (stale per-project sandbox state).
- The sandbox is isolated (`.intellijPlatform/sandbox/`); it never touches the user's
  real IDE config.
- If the walkthrough content drifts from the plugin's features, update
  `walkthrough-template.md` in this skill — it is the single source for the checklist.