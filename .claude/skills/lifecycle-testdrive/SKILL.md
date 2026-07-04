---
name: lifecycle-testdrive
description: Seed a disposable OpenSpec demo project and launch the plugin in a sandbox IDE (runIde) for a manual walkthrough of the change lifecycle. Use when the user wants to test-drive the plugin UI, verify a change works in the real IDE, or asks for a sandbox/manual walkthrough.
---

# Lifecycle test-drive: seeded sandbox + in-IDE walkthrough

Launches the plugin in a sandbox IDE with a **pre-seeded demo project** that exercises
the change lifecycle end-to-end, and puts the walkthrough checklist **inside the project**
(`WALKTHROUGH.md`) so it can be read in a split editor next to the tool window.

The demo project is deliberately seeded to trigger real behavior:

- Initialized by an **older OpenSpec CLI (1.3.1)** with the `junie` tool → leaves legacy
  `.junie/commands/opsx-*.md` files, so the Update action's legacy-cleanup flow fires.
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
   <repo>/scripts/seed-lifecycle-demo.sh "$DEMO"
   ```

   (It refuses an existing directory — see step 1. Note: `openspec validate` on the
   seeded change reports "no deltas" — that is authentic mid-lifecycle state, not a
   seeding bug; the workflow chips read `openspec status`, which shows 1/4 done.)

3. **Copy the walkthrough into the project** so it is readable inside the sandbox:

   ```bash
   cp "<this skill's directory>/walkthrough-template.md" "$DEMO/WALKTHROUGH.md"
   ```

4. **Apply the sandbox workaround (IDEA 2024.2 target only).** The bundled Gradle
   plugin's startup activity downloads a Gradle↔JVM compatibility table that now contains
   Java 25 entries; the 2024.2 parser throws `IllegalArgumentException: 25` as an error
   balloon on every launch. It is platform noise, unrelated to this plugin (which depends
   only on `platform` + `yaml`) — disable the Gradle plugins in the sandbox:

   ```bash
   SANDBOX_CONFIG="<repo>/.intellijPlatform/sandbox/intellij-openspec/IC-2024.2/config"
   mkdir -p "$SANDBOX_CONFIG"
   printf 'com.intellij.gradle\norg.jetbrains.plugins.gradle\norg.jetbrains.plugins.gradle.maven\n' \
     > "$SANDBOX_CONFIG/disabled_plugins.txt"
   ```

   Do this while the sandbox is NOT running (the IDE rewrites its config on exit).

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