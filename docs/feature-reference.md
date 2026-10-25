# OpenSpec Plugin — Feature Reference

Complete reference for all plugin features, organized by functional area.

> **Maintenance: Living** — updated as part of every relevant change (see the [documentation index](README.md)). For canonical CLI/plugin version facts, see the [Version support](openspec-support.md#version-support) block.

---

## Browsing & Navigation

### Tool Window

The OpenSpec tool window (right sidebar, or **View > Tool Windows > OpenSpec**) has two permanent tabs, plus conditional Explore and Coordination tabs:

| Tab | Purpose |
|-----|---------|
| **Browse** | Changes-focused master/detail view: a tree of your changes (each with per-artifact status) beside a read-only rendered-markdown preview pane dedicated to change surfaces. Single-click to preview, double-click to open the file, right-click for context menu actions. Specs, archived changes, and `openspec/config.yaml` browse in the standard Project View and open in the editor; spec content search is via Search Everywhere. |
| **Console** | Output panel for CLI commands (init, validate, update, etc.). |
| **Explore** | *(Only when Direct API is configured)* Thinking-space panel with inline topic input, markdown-rendered AI responses, and Copy/Clear toolbar. Appears automatically when you configure a Direct API provider in Settings. |
| **Coordination** | *(Only when OpenSpec 1.4 coordination state or a coordination mode is detected)* Lists workspaces, context stores, and initiatives. See [Coordination Tab](#coordination-tab) below. |

### Coordination Tab

Surfaces the OpenSpec 1.4 coordination layer — **workspaces**, **context stores**, and **initiatives**. It is sourced from the OpenSpec CLI (`workspace`/`context-store`/`initiative list` and `doctor`) with a built-in fallback that reads OpenSpec's global data dir directly (`$XDG_DATA_HOME/openspec`, `~/.local/share/openspec`, or `%LOCALAPPDATA%\openspec`), so a listing still appears without the CLI.

These commands exist only in the OpenSpec CLI **`[1.4.0, 1.5.0)`** window. **CLI 1.5.0 removed them** (replaced by a store/workset model), so on a 1.5.0+ CLI the plugin never invokes them: the tab stands down to read-only Awareness when legacy on-disk state exists, and Hidden otherwise.

The tab presents at one of three tiers:

| Tier | When | Behavior |
|------|------|----------|
| **Hidden** | No coordination state and not in a coordination mode (or CLI ≥ 1.5.0 with no legacy state) | The tab is not shown. |
| **Awareness** | Coordination state detected, but no CLI in the `[1.4.0, 1.5.0)` window (missing, below 1.4, or ≥ 1.5.0) | Read-only listing with status indicators; action buttons disabled. |
| **Full** | Coordination state present and OpenSpec CLI in `[1.4.0, 1.5.0)` | Listing plus initiative-artifact navigation and create/set-up actions. |

- **Workspaces** show a resolution indicator (resolves locally / unresolved).
- **Context stores** show their id and root, plus metadata/git health when `doctor` detail is available.
- **Initiatives** show a lifecycle status badge (`exploring` / `active` / `complete` / `archived`). Each initiative's artifacts — `initiative.yaml`, `requirements.md`, `design.md`, `decisions.md`, `questions.md`, `tasks.md` — open in the editor (double-click; an uncreated artifact reports that it does not exist yet).
- **Actions** (Full tier): **New Initiative**, **Set Up Context Store**, **Set Up Workspace** delegate to the CLI and refresh the listing on success; CLI errors are surfaced. These actions are **version-gated to the `[1.4.0, 1.5.0)` window and self-retiring** — CLI 1.5.0 removed the underlying `initiative` / `context-store` / `workspace` commands, so upgrading to a 1.5 CLI automatically retires them (the tab switches to the store/workset model below).

### Stores & Worksets (OpenSpec 1.5)

OpenSpec CLI **1.5.0** replaced the 1.4 coordination model with **stores** and **worksets**:

- **Stores** are standalone OpenSpec repos you register on your machine.
- **Worksets** are purely local, composed working views over one or more stores.

When the detected CLI is at or above the **`1.5.0`** store floor, the Coordination tab makes stores and worksets the **lead model**, presented **read-only**:

- **Stores** list each store's **id** and **root**, plus health from `store doctor`: whether store metadata is present and valid, whether the store root is a **git repository** (non-git stores are handled without error), and whether its OpenSpec root is **healthy**. Health comes solely from the CLI's own report — on OpenSpec CLI 1.6+, a **fresh store** whose planning directories (`specs`, `changes`, `changes/archive`) don't exist yet is reported healthy and listed **without any error marker**. Health lookups are lazy and run off the UI thread.
- **Worksets** list each workset's **name** with its **members** shown as child rows (member `name` + `path`).
- **Diagnostics** — every 1.5 command returns a uniform diagnostic envelope (`severity`, `code`, `message`, `target`, `fix`). The plugin retains each diagnostic, including the CLI's ready-made **`fix`** suggestion, and shows it as read-only guidance against the affected store. The suggested fix is displayed, never executed.
- **Legacy demotion (no migration).** When both new (stores/worksets) and legacy (workspaces/context-stores/initiatives) state exist on disk at CLI ≥ `1.5.0`, the legacy state is demoted to a muted, read-only **"Legacy (pre-1.5)"** group, shown only when such state actually exists. The plugin never converts legacy state into stores or worksets — it only reflects what the CLI owns.

Sourcing mirrors the rest of the tab: listings come from the OpenSpec CLI (`store list` / `store doctor` / `workset list`), with a **built-in fallback** that reads the global data dir directly (`stores/registry.yaml` and `worksets/worksets.yaml`) when the CLI is unavailable or a payload fails to parse — a parse failure degrades to the on-disk fallback and never throws into the UI. The store gate derives solely from the detected CLI version (the checked-in config-format version is a separate, independent axis and is not consulted).

**Write actions (Full tier).** When the CLI is at or above `1.5.0` and the surface is at the Full tier, a toolbar and a tree right-click menu expose CLI-delegated actions:

- **New Store…** — a dialog collecting a store id and a **folder location** (a path is required), running `store setup`.
- **Register Existing Store…** — registers an already-existing store folder. On OpenSpec CLI 1.6+ a folder that was never a store can be registered even before its planning directories exist; a folder whose `openspec/config.yaml` points at an external store (a `store:` line) is refused, with the CLI's remediation shown.
- **Unregister** — forgets a store's registration without deleting files.
- **Remove…** — *destructive*: forgets the registration **and deletes the store's local folder**; guarded by an explicit confirmation that says so.
- **Open Store Root** — opens the store's OpenSpec root.
- **New Workset…** — a dialog collecting a name and a member list (member folders are never modified), running `workset create`.
- **Open Workset** — reveals the workset's member folders in your file manager, behind an explicit "opens N folders" confirmation; never auto-opens on tab load. (Deeper in-IDE multi-folder integration is a follow-up — the platform's attach-to-project API isn't available across the plugin's minimum supported IDE build.)
- **Remove Workset** — deletes the saved workset; member folders are untouched.

Actions run off the UI thread; on failure the CLI's diagnostic message (and its `fix` suggestion) is surfaced, never raw error output. A `store doctor`-driven **health strip** at the top of the panel shows the highest-severity diagnostic with its `fix` as an inline action. If a **Store Doctor** check can't run or returns nothing readable, it says the store's health couldn't be determined, never that the store is healthy.

### Browse Tab Tree Structure

```
OpenSpec
└── Changes
    └── add-greeting  2/4                ← name-first, dimmed task progress X/Y
        ├── proposal        ● done badge
        ├── design          ● ready badge
        ├── specs (needs: design)  ✕ blocked badge
        └── tasks           ▲ not-created badge
```

> Specs, archived changes, and `openspec/config.yaml` are browsed in the standard Project View (which lists `openspec/**`) and open in the editor — the tool-window tree is dedicated to Changes.

**Artifact status badges.** A change artifact's status is shown as a small badge overlaid on the
node's icon — **done**, **ready**, **blocked**, or **not-created** — rather than as a glyph glued to
the label (labels are now the plain artifact id, keeping a subtle foreground tint as reinforcement).
Badges use distinct shapes, are theme- and HiDPI-correct, and each badged node names its status in
its tooltip. A blocked artifact keeps a `(needs: …)` suffix naming the unmet dependencies. When the
OpenSpec CLI is unavailable, badges degrade honestly to done-versus-not-done (the ready/blocked split
needs the CLI's dependency graph). A **change** node itself shows one of three stages, one badge at a time:

- **No badge:** some artifacts are still missing.
- **Apply-ready (green dot):** all artifacts are complete; tasks may still be open.
- **All tasks done (check mark):** all artifacts are complete **and** every task is done. The label adds
  `· all tasks done` after the count, so finished changes stand out without reading every `X/Y`. This is
  a *suggestion* that the change is ready to archive, not a guarantee: archiving can still be refused,
  for example by spec validation. Run **Verify** to confirm. The label deliberately says "all tasks done"
  rather than "ready to archive", because Verify's "Ready to archive" also checks validation.

The dot and the check differ in shape, so the stages don't rely on color. Without the OpenSpec CLI,
neither badge appears (artifact completion comes from the CLI), while the `X/Y` count still shows.

The change node also shows its task progress as an `X/Y` count in the label when a tasks artifact exists. Tasks are counted exactly as the OpenSpec CLI counts them: any bullet
or numbered marker (`-`, `*`, `+`, `1.`, `1)`), empty or padded boxes (`[]`, `[ x ]`), and done only
when the box holds `x`. The same counts drive the workflow panel, Apply, and Verify.

**On-model boundary.** Only **change**, **change-artifact**, and **missing-artifact** nodes carry a
status badge — these are the nodes with client-owned status (apply-readiness, task progress, and their
combination, all-tasks-done). **Spec,
requirement, delta-spec, and config nodes are never badged**, because the OpenSpec model attaches no
status to a spec or requirement; a badge there would repeat the removed `@spec` coverage scorecard.

The plugin recovers a spec's requirement and scenario counts with a line-oriented scanner that
mirrors the OpenSpec CLI's own parsing, so its reading of a spec agrees with `openspec show` /
`openspec validate`. (The Changes-focused tree no longer renders a Specs section, but this scanner
still backs spec parsing wherever the plugin reads a spec — the consolidated-deltas view and spec
content search.) The recognition rules:

- **Code fences are excluded.** Requirement headers, scenario headers, and normative keywords that
  appear only inside a fenced code block (` ``` ` or `~~~`) are not counted — they are examples, not
  structure.
- **Any level-4 header is a scenario.** Every non-fenced `####` header under a requirement counts as a
  scenario, not only a `#### Scenario:`-labelled one; the bold `**Scenario:**` form is not a scenario.
- **Normative keyword is `SHALL`/`MUST` on the body.** The requirement's normative state is the
  whole-word, case-sensitive keyword `SHALL` or `MUST`, evaluated on the requirement **body** (the lines
  between the header and the first scenario) — not on the header line and not inside fences. `SHOULD` and
  `MAY` are not normative.
- **Requirement headers stay case-insensitive** (see the Spec Format inspection below).

### Change Deltas Pane (master/detail)

The Browse tab is a **master/detail** view: the Changes tree on the left, a read-only **Change
Deltas** pane on the right, in a horizontal splitter whose width is remembered and whose pane can be
collapsed. `Changes` is expanded the first time the tree loads, so your changes are visible at once;
if you collapse it, it stays collapsed when the tree refreshes.

- **What the pane shows.** Every state of the pane starts with a **Change Deltas** header, naming the
  selected change, and one line: *what this change will add, modify or remove in your specs when it's
  archived*. With nothing selected, the pane explains what a change is and how to use it; when the
  project has no changes yet, it offers **Propose a change**.
- **Single-click** a change, or any of its artifacts (proposal, design, tasks or specs, done or not
  yet created), to see that change's deltas. When the artifact is a file that exists, an **Open
  `<file>` in editor** link sits under the header; **double-click** also opens the file. The pane is
  always read-only, and editing stays in the editor.
- **Consolidated change-deltas view.** A read-only, at-a-glance view of everything the change modifies
  at the spec level: a one-line summary (capabilities touched · counts per operation), then each
  capability grouped together, then each operation (`ADDED` / `MODIFIED` / `REMOVED` / `RENAMED`)
  with the same badges as a delta-spec preview, then each requirement's text and scenarios. The
  assembled delta set is sourced from the **OpenSpec CLI** (`openspec show <change>`), never
  hand-assembled from files, and capability groups are shown in a stable alphabetical order. A
  `RENAMED` delta shows its from→to without a requirement body. Each capability section offers a
  **Preview diff** link that opens that capability's delta-vs-current-main diff, so the consolidated
  *reading* view and the per-capability *machine diff* complement each other. A change with no spec
  deltas shows an informative empty state.
- **Delta specs.** Selecting a change's delta spec (`changes/<change>/specs/<capability>/spec.md`)
  shows that file, with its operation headers badged. Main capability specs are not previewed in the
  pane; they open in the editor with the platform's Markdown preview.
- **Without the CLI.** Selecting a change or one of its artifacts shows a placeholder saying the
  consolidated deltas need the CLI, still with the header and the open-in-editor link. Delta-spec
  files still preview without the CLI.
- **On-model.** The pane renders the delta set the CLI reports, or your own delta-spec markdown. It
  does not synthesize a post-apply "effective" spec, and it does not score or show per-spec/per-delta
  status, progress, or coverage; change-owned state follows the OpenSpec CLI: delta assembly comes
  from the CLI, and task progress is counted with the CLI's own task-line grammar, so it matches
  `openspec list --json`.

Rendering runs off the UI thread (the file is read and rendered on a background thread, then the pane
is updated on the UI thread), so selection stays responsive.

### Spec Content Search (Search Everywhere)

Spec content search lives in **Search Everywhere** (double-Shift). Type a term that appears only
inside a requirement's **body or scenario text** — "find where rate-limiting is specified" — and the
contributor surfaces the matching requirement by name, opening its spec in the editor scrolled to
that requirement. It runs off the UI thread over your local `openspec/` files without building or
persisting an index, so it also works while the IDE is indexing.

---

## Workflow Orchestration

### Artifact Pipeline

Each change follows a dependency graph (DAG) of artifacts:

```
proposal  →  design  →  specs  →  tasks
```

The **Workflow Action Panel** (below the tree) displays this as a row of status chips:

| Status | Icon | Meaning |
|--------|------|---------|
| **Done** | Green checkmark | `openspec status` reports the artifact done (its file exists). A done file that still looks like an unfilled template reads `<name> · template` in muted text, with a tooltip pointing to **Regenerate**; nothing is disabled by it |
| **Ready** | Blue filled circle | Dependencies satisfied — ready to generate |
| **Blocked** | Gray empty circle | Waiting on upstream artifacts |

Statuses are exactly what `openspec status` reports. When `openspec status` rejects the selected change (for example, it was renamed or deleted, or its `.openspec.yaml` is invalid), the panel says **Can't read change '<name>'** with the CLI's reason (the full message is in the tooltip), plus **Open .openspec.yaml** and **Refresh** links (Refresh saves your open edits first, since OpenSpec reads the files on disk). The change stays selected, and the card clears on the next refresh once OpenSpec accepts the change again. It doesn't keep showing the old chips. If the CLI couldn't be run at all, the last known status stays. The panel includes a **change selector** dropdown for switching between multiple active changes (the panel acts on the change selected there, not the one selected in the tree), and a **Generate** button that targets the next ready artifact.

### Workflow Actions

| Action | Menu | Description |
|--------|------|-------------|
| **Init** | OpenSpec > Init | Initialize OpenSpec in the current project. Creates `openspec/` directory structure. |
| **Propose** | OpenSpec > Propose... | Create a new change with name and description. With the CLI it runs `openspec new change`; without it, the plugin writes the same `.openspec.yaml` plus your proposal and an empty `specs/` folder, and no template design or task files. |
| **Fast-Forward** | OpenSpec > Fast-Forward... | Create a change and generate all artifacts in one step via Direct API. |
| **Continue** | OpenSpec > Continue | Generate the next ready artifact in the active change. Requires Direct API. |
| **Explore** | OpenSpec > Explore... | Prompt for an optional topic, assemble the explore prompt (skill instructions + project context + topic), and deliver via configured delivery mode. With Direct API configured, the Explore tab appears in the tool window for inline input and rendered responses. Clipboard and Editor Tab modes use the topic dialog. |
| **Apply** | OpenSpec > Apply | Mark a change as applied. |
| **Verify** | OpenSpec > Verify | Check artifact completeness and task progress. Opens report dialog. |
| **Archive** | OpenSpec > Archive | Archive a completed change: with OpenSpec CLI 1.5+ through `openspec archive` (its validation, rules and messages; "Archive Without Syncing" adds `--skip-specs`), otherwise by the plugin, which applies the delta specs and moves the change to `openspec/changes/archive/YYYY-MM-DD-<name>/` (refused if that folder exists). Runs in the background; a refusal shows the reason and leaves the change active. |
| **Bulk Archive** | OpenSpec > Bulk Archive... | Archive multiple completed changes at once with conflict detection, one `openspec archive` call per change with CLI 1.5+. A change OpenSpec would refuse is marked Blocked (or Failed with the CLI's reason) and left untouched. |
| **Sync Specs** | OpenSpec > Sync Specs | Merge delta specs from a completed change into main specs at `openspec/specs/`, by the same rules as `openspec archive`: fenced examples are left alone, names match exactly, and a change OpenSpec would refuse is refused with its reason in the preview, with nothing written. A new capability starts with `# <name> Specification` and OpenSpec's "TBD - created by archiving change …" Purpose, or the delta's own Purpose when it has one. Shows preview dialog. |
| **Update** | OpenSpec > Update OpenSpec… | Run `openspec update` to refresh agent instruction files. Requires CLI. **On CLI 1.15 or later, Update first shows what it will change** (see [Update preview](#update-preview)) and runs only when you click **Apply**. When the CLI reports leftover legacy files from its skills migration (it exits 0 but asks for `--force` or an interactive run — neither reachable from the console), the plugin raises a **Review legacy cleanup** notice instead of bare success: every CLI-listed file appears with a checkbox and an open-to-inspect link (the review quotes what the CLI said and starts with no files ticked, since OpenSpec warns to back up custom content first), removal happens through the IDE as a single undoable step (Local History covers it), and a follow-up `openspec update` verifies the result (if that run fails, the plugin says the cleanup couldn't be verified rather than reporting a clean state). If that verification shows the CLI *regenerated* the files (a known inconsistency in some CLI versions' tool integrations), the plugin says so plainly and stops re-nagging until the CLI reports something different. Escape hatches: run interactively in the terminal, or **Not now** (no re-nag while the pending set is unchanged). **The plugin never runs `openspec update --force` on your behalf.** |
| **Manage AI Tools** | OpenSpec > Manage AI Tools... | View and configure AI tool integrations detected in your project. |
| **List** | OpenSpec > List | List all specs and changes. |
| **Refresh Tree** | OpenSpec > Refresh Tree | Manually refresh the tool window tree. |
| **Setup Wizard** | OpenSpec > Setup Wizard... | Guided onboarding for new projects. |

### Profile-Aware Action Visibility

Workflow actions respect the active OpenSpec profile. Actions whose workflow is not enabled in the current profile (e.g., `core` vs expanded) appear **visible but disabled** with a tooltip: *"Requires expanded profile. Change in Settings → Tools → OpenSpec."*

The plugin works out the active workflows the way the OpenSpec CLI does, so it enables exactly what the CLI installs:

| Profile | Active workflows |
|---------|----------------|
| **core** (also when no profile is set) | `propose, explore, apply, update, sync, archive` on CLI 1.6 and later. CLI 1.4–1.5 has no `update`, and CLI 1.3 also has no `sync`. Any workflow list left over in your config is ignored for core, as the CLI ignores it. |
| **custom** | The workflows you picked. On CLI 1.10 and later, `sync` is included whenever you picked `archive` or `bulk-archive`, because archiving needs it. |

Workflow-bound actions are enabled when their workflow is active: Propose, Explore, Apply, Archive, Fast-Forward, Continue, Verify and Bulk Archive.

Utility actions (Init, Validate, List, Refresh, Update, Manage AI Tools, Setup Wizard) are always enabled regardless of profile.

The profile is configured globally via `openspec config profile` or in **Settings → Tools → OpenSpec**. After changing the profile, action enablement updates immediately.

### Scaffolding Detection

Artifact status comes from `openspec status`, so a file OpenSpec considers done is done. If a done file still looks like an unfilled template, its chip reads `<name> · template` with a tooltip pointing to **Regenerate**. Nothing is disabled because of it.

---

## AI Integration & Delivery

### Delivery Modes

When you click Generate, the plugin delivers the assembled prompt via your configured method:

| Mode | What happens |
|------|-------------|
| **Copy for [Tool]** | Copies the prompt to clipboard with tool-specific guidance (e.g., "Paste into GitHub Copilot"). One entry per detected tool. |
| **Open in Editor Tab** | Opens the prompt in a temporary editor tab for review before pasting into any AI tool. |
| **Generate via API** | Sends the prompt directly to an AI provider's API and writes the response to disk automatically. Requires an API key. |

### AI Tool Detection

The plugin knows every AI tool the OpenSpec CLI can set up (**50 tools** as of OpenSpec 1.14) and detects them the way the CLI does, using the same names as OpenSpec's tool picker. A representative subset is shown below:

| Detected from | Tool | Type |
|---------------|------|------|
| `.github/copilot-instructions.md`, `.github/prompts/`, `.github/instructions/`, … | GitHub Copilot | IDE Panel |
| `.cursor/` | Cursor | IDE Panel |
| `.devin/` (or the older `.windsurf/`) | Devin Desktop (formerly Windsurf) | IDE Panel |
| `.cline/` | Cline | IDE Panel |
| `.claude/` | Claude Code | CLI |
| `.agents/skills/` (owner recorded in `.agents/skills/.openspec-target`) | Codex, or another tool sharing `.agents` | CLI |

A few rules come straight from the CLI:
- **Copilot is detected only from its own files.** A plain `.github/` folder, which most repositories have, doesn't count.
- **Tools sharing `.agents/`** (Codex, Amp, Antigravity, Zed and others) are told apart by OpenSpec's ownership marker, then by the generated skills.
- **Older directories still count:** `.windsurf/`, `.codex/` and `.kimi/`, for example.
- **Tools that install into your home directory** (MiniMax Code) are detected there.
- **Newer tools are hidden from older CLIs.** The plugin only offers or passes a tool to `openspec init` when your installed CLI accepts it.

Detected tools appear in bold in the settings panel and are available as clipboard delivery targets. **CLI** tools receive save-path hints in the guidance text; **IDE Panel** tools receive paste-target guidance.

### Direct API Providers

| Provider | API endpoint | Key format |
|----------|-------------|------------|
| **Claude** (Anthropic) | `api.anthropic.com` | Starts with `sk-ant-` |
| **OpenAI** | `api.openai.com` | Starts with `sk-` |
| **Gemini** (Google) | `generativelanguage.googleapis.com` | Google API key |

API keys are stored securely in your operating system's keychain via IntelliJ's PasswordSafe — never saved in project files.

**Specs over Direct API.** Generating `specs` writes one delta spec per capability, at `specs/<capability-path>/spec.md`. The model marks each file with its path, the plugin checks every path, and either all the files are written or none are. Prompts for artifacts that depend on the specs, such as `tasks`, include them.

**Claude Workspace ID.** When Claude is the provider, the settings show a **Workspace ID (optional)** field. Fill it in when your Claude API key isn't scoped to a single workspace: such keys must name a workspace on every request, and Claude rejects them otherwise (the plugin then tells you to enter a Workspace ID). Copy the ID, which starts with `wrkspc_`, from **Console → Settings → Workspaces**. Leave it blank for a key that belongs to one workspace. The ID is a regular per-project setting, not a secret, and is never written to OpenSpec project files. Test uses the value currently in the field, even before you click Apply.

**Model discovery.** The Model dropdown is populated from your provider account, not a hardcoded list. Clicking **Test** fetches your account's available models with your key — a free request that costs no tokens — and fills the dropdown with the models you can actually use; it also validates the key (a `401` means "check your API key"). If you're offline or haven't entered a key, the dropdown falls back to a built-in default list, and it stays editable so you can type any model name manually.

**Choosing a model at operation time.** You don't have to open Settings to switch models: the OpenSpec tool window shows a model dropdown in its header (next to the delivery selector) whenever Direct API is the active delivery mode. It's populated from the same live model list, stays editable, shows the active model in the status strip, and your selection is what generation, Fast-Forward, Continue, and Explore use. It's hidden for Clipboard and Editor-Tab delivery, where no model is sent. If you configure a provider while the tool window is already open — in Settings or the Setup Wizard — the Direct API delivery option and this dropdown appear immediately, with no IDE restart, and your current delivery choice is left unchanged.

---

## Editor Integration

### Inspections

Three editor inspections run in real-time as you edit:

| Inspection | Validates |
|------------|----------|
| **Spec Format** | Spec YAML structure (headings, requirement format, scenario format) |
| **Delta Spec Structure** | ADDED/MODIFIED/REMOVED section headers and content |
| **Config Validation** | `openspec/config.yaml` structure and values |

Requirement headers (`### Requirement:`) are recognized **case-insensitively**, matching OpenSpec CLI 1.4+ parsing. When a requirement carries its RFC 2119 keyword only in the header line, the Spec Format inspection reports a targeted warning — *move the keyword onto the requirement body line* — with a quick-fix that inserts the keyword-bearing sentence as the first body line.

### Annotations

- **Spec block syntax highlighting** — Visual distinction for spec content blocks
- **Given-When-Then scenario annotation** — Highlights scenario structure in spec files

### Line Markers

- **Generic line markers** — On spec definitions in spec files

---

## Validation

**OpenSpec menu > Validate** (or toolbar button)

Validates all specs, changes, and config files. A summary notification names the target and its error/warning counts; the full, navigable report lands in the OpenSpec **Console** (see below).

**OpenSpec menu > Validate (Strict)** (or the toolbar Validate dropdown, or Find Action). A per-run strict validation that mirrors the CLI's `openspec validate --strict` — warnings count as failures. Strict is a per-invocation choice, not a persistent setting: the default Validate is unaffected, and a strict run is always disclosed (the summary reads `Validate (strict)`, adds `— strict: warnings count as failures` on a warnings-only failure, and the console echoes `--strict`), so a strict verdict is never a hidden mode. It means the same thing whether or not the CLI is available.

| Mode | Behavior |
|------|----------|
| **CLI available** | The CLI's own verdict is authoritative for specs and changes (`openspec validate --all`) — the plugin never reports a spec/change error the CLI wouldn't. The built-in validator still surfaces `config.yaml` guidance (which the CLI's `validate` does not inspect), but only as non-failing warnings — config never reds a project the CLI reports clean. When the CLI answers with something that isn't a verdict (an error response, malformed or truncated JSON, or JSON without results), the built-in validator gives the verdict instead, as if the CLI were absent; such a run is never reported as a pass. |
| **CLI absent** | The built-in validator is the full fallback: spec format, delta-spec structure, and config. Its severities match the CLI's default-mode verdict, so it is never stricter than the CLI — e.g. a missing spec title is a warning, a scenario's missing `WHEN`/`THEN` is an info hint, a requirement with no scenario is an error, a main spec that declares the same requirement name twice is an error (matching OpenSpec 1.8's duplicate-requirement check, scoped to main specs so change deltas are unaffected), a delta spec placed at a change's `specs/` root instead of under a capability folder is an error and a change that requires specs but has none is an error (both matching the CLI's change-delta discovery, unless the change declares `skip_specs`), and (matching OpenSpec 1.8) a body-carrying requirement without `SHALL`/`MUST` is a non-failing warning that `--strict` re-promotes, while a requirement with no body at all is an error. A delta whose only content is a rename written with bare `FROM:`/`TO:` names (without `### Requirement:` headers) is invalid ("no requirement entries parsed"), as in OpenSpec, and an unpaired rename is reported with OpenSpec's wording. |

**Results in the console (grouped, colored, navigable).** The console report opens with a verdict line naming the target (e.g. `Validation FAILED — Change x`) and an error/warning/info count line, then groups issues under a per-file header — files containing an error first, then warning-only, then info-only, and within a file by line. Each issue's location renders as a clickable `file:line` link that opens the file at that line when the path resolves on disk; issues the CLI reports against a non-filesystem identifier degrade to plain, still-colored text rather than a dead link. ERROR, WARNING, and INFO each render in a distinct, theme-driven color, and a clean run shows a concise `✓ Validation PASSED` confirmation. This is the shared report for every Validate surface (toolbar and the Project-View scoped Validate below); file headers are grouping keys only — no per-file valid/invalid verdict is invented.

**Project View context menu > Validate OpenSpec.** Right-click a file under `openspec/` in the standard Project view to validate *its* item in place, scoped to what you clicked: a file under `openspec/specs/<capability>/` validates that spec; a file under an active `openspec/changes/<name>/` validates that change; a file under `openspec/changes/archive/`, the `openspec/` root, or `config.yaml` (and any selection spanning multiple items) falls back to validating the whole project. The menu item appears only when the selection is under `openspec/`. It reuses the same CLI-authoritative pipeline (the CLI call is scoped, e.g. `openspec validate <id> --type change|spec`) and reports to the same console — no per-file valid/invalid verdict is invented.

Validation also runs automatically at phase transitions (e.g., before archive).

---

## Settings & Configuration

Access via **Settings > Tools > OpenSpec** (or **Preferences > Tools > OpenSpec** on macOS).

### CLI

| Setting | Purpose |
|---------|---------|
| **CLI path** | Path to the `openspec` binary. Click **Detect** to auto-find it. |
| **Status** | Shows CLI version when detected, or "not found". While detection is still running, the tool window shows "CLI: detecting…". |

The plugin re-checks the CLI automatically when the project opens, when the OpenSpec tool window is shown, and
when you switch back to the project's IDE window. So a CLI you upgrade while the IDE is running is picked up when
you return to the IDE, without reopening the project. Re-checks happen at most once every 30 seconds.

### General

| Setting | Purpose |
|---------|---------|
| **Schema profile** | Workflow profile: `spec-driven`, `tdd`, `rapid`, or custom. |
| **Auto-refresh** | Automatically refresh the tree when OpenSpec files change on disk. |

### Config Profile

Read-only display of the current profile name and description from `config.yaml`, plus available workflows from the CLI.

### Schemas

| Setting | Purpose |
|---------|---------|
| **Schema list** | Available workflow schemas, each tagged with where it resolves from (`[project]`, `[user]`, `[package]`); a copy that shadows another source says so (e.g. `[project, shadows package]`). Select one as default for new changes. |
| **Fork** | Fork an existing schema to customize it; the new schema's `schema.yaml` opens in the editor. |
| **New** | Create a new schema with the description and artifact types you choose; its `schema.yaml` opens in the editor. |
| **Validate** | Check the selected schema's structure and templates via `openspec schema validate` — structure errors, missing templates, and circular dependencies appear inline below the list. |
| **Open Templates** | Open the selected schema's artifact templates in the editor (paths resolved via `openspec templates`); missing template files are reported, never created. |

Fork, Validate and Open Templates act on the schema selected in the list, and stay disabled until you select one.

> Schema management requires the OpenSpec CLI v1.3.0 or later.

### Tools & Delivery

| Setting | Purpose |
|---------|---------|
| **Detected tools** | AI tools found in your project (shown in bold). |
| **Deliver via** | Unified dropdown: Copy for [Tool], Open in Editor Tab, or Generate via API. |

### Direct API

| Setting | Purpose |
|---------|---------|
| **Provider** | `None`, `Claude`, `OpenAI`, or `Gemini`. |
| **API key** | Stored securely in the OS keychain — never in project files. |
| **Model** | Model to use (pre-populated per provider, editable). |
| **Workspace ID (optional)** | Claude only. Needed when your Claude API key isn't scoped to a single workspace; see [Claude Workspace ID](#direct-api-providers). |
| **Test** | Verify your API key and connection. |

> An API key is only needed for Direct API delivery. Clipboard and Editor Tab modes work without any API key.

---

## Update preview

On OpenSpec CLI 1.15 or later, every update the plugin starts shows you what it will do first: the **Update OpenSpec…** action, the update offered after you switch workflow profile, and the **Update** button in **Manage AI Tools**. The plugin runs `openspec update --dry-run --json`, which writes nothing, and shows its plan in the **OpenSpec Update Preview** dialog:

- **A summary**, such as "24 changes: 12 deletes, 12 adds".
- **OpenSpec's notes.** A note that the update will move files out of a renamed tool folder (for example `.windsurf/` to `.devin/`) is shown as a warning: run from the IDE, the CLI makes that move without asking.
- **Failures,** for example a file the update can't write. **Apply** is disabled until they're fixed, because `openspec update` stops at the first tool it can't update.
- **The changes, grouped by tool,** with deletes first. Anything outside your project, such as `~/.config/openspec/config.json` (written the first time you update), is in its own **Outside this project** group.

**Apply** runs `openspec update` once. **Cancel** changes nothing. When the plan deletes anything, Cancel is the default button.

- **Nothing to update:** when the plan is empty, the plugin says the project is already up to date and doesn't run the update.
- **The preview fails:** for example there's no `openspec/` folder, or the output can't be read. The plugin shows the CLI's error and changes nothing. It never runs an update you haven't seen.
- **Manage AI Tools:** `openspec update` always updates every configured tool, so the **Update** button there shows the whole plan, with that tool's changes listed first.
- **Legacy cleanup:** when the plan says legacy files are waiting, the plugin takes the list of files to remove from `openspec update --dry-run --force --json` (read-only) after you apply, and offers it in the usual **Review legacy cleanup** flow. It never runs `--force` itself.
- **Older CLIs:** below 1.15, which has no `--dry-run`, Update runs directly as before.

The plan isn't saved anywhere: each Update asks the CLI again.

## Troubleshooting

### "CLI: not found" in the status bar

The plugin works without the CLI, but some features (schema management, CLI-enhanced validation, Update action) require it.

1. Install it with OpenSpec's standalone installer, which needs no Node.js (`curl -fsSL https://raw.githubusercontent.com/Fission-AI/OpenSpec/main/scripts/binary/install.sh | sh` on macOS and Linux, `irm https://raw.githubusercontent.com/Fission-AI/OpenSpec/main/scripts/binary/install.ps1 | iex` on Windows), or with `npm install -g @fission-ai/openspec@latest` or `brew install openspec`. See [OpenSpec's installation page](https://openspec.dev/docs/installation)
2. If installed but not detected: **Settings > Tools > OpenSpec > Detect**, or set the path manually
3. On macOS, GUI apps don't inherit your terminal's PATH — the plugin tries your login shell, Homebrew paths (including Homebrew on Linux), `/usr/local/bin`, and `~/.local/bin` (where OpenSpec's standalone installer puts it) automatically. On Windows it also checks `%LOCALAPPDATA%\Programs\openspec\openspec.exe`. If you installed the standalone binary with `OPENSPEC_INSTALL_DIR`, the plugin checks that folder only when the IDE's environment carries the variable; otherwise set the path manually
4. Just installed or upgraded the CLI? Switch away from the IDE and back (after 30 seconds) and the status updates on its own
5. If the tool window shows "CLI: detecting…", detection is still running; it updates on its own when it finishes

### Which OpenSpec CLI the plugin uses

OpenSpec can be installed three ways, and each puts the `openspec` program in a different place:

| Route | Where it installs |
|---|---|
| Standalone installer (`install.sh`, `install.ps1` or `openspec install`) | `~/.local/bin/openspec`; on Windows `%LOCALAPPDATA%\Programs\openspec\openspec.exe`. `OPENSPEC_INSTALL_DIR` changes the folder |
| npm (`npm install -g @fission-ai/openspec@latest`) | npm's global folder, e.g. `/opt/homebrew/bin` with Homebrew's Node, `/usr/local/bin`, or `%APPDATA%\npm` on Windows |
| Homebrew (`brew install openspec`) | `/opt/homebrew/bin` (Apple Silicon), `/usr/local/bin` (Intel Macs) or `/home/linuxbrew/.linuxbrew/bin` (Linux) |

The plugin uses the first copy it finds, in this order:
1. the CLI path in Settings;
2. `openspec` on the IDE's PATH;
3. your login shell's `which openspec`;
4. the Homebrew, system and npm locations;
5. the standalone locations.

If you have more than one copy, the terminal and the IDE can run different versions. Keep one, and upgrade it the way you installed it: re-run the installer (or `openspec install`), `npm install -g @fission-ai/openspec@latest`, or `brew upgrade openspec`. To pin a copy, set its full path in **Settings > Tools > OpenSpec > CLI path**.

The **Update** action runs `openspec update` (on CLI 1.15+, after showing you the [preview](#update-preview)). That refreshes your project's generated instruction files (skills and commands) to match the CLI you have; it doesn't upgrade the CLI itself.

### "Fast-Forward", "Continue", or other actions are grayed out

**Profile check:** If the tooltip says *"Requires expanded profile"*, your active profile doesn't include that workflow. Switch to an expanded or custom profile in **Settings → Tools → OpenSpec → Config Profile**.

**API check:** Fast-Forward and Continue also require an AI provider and API key configured in **Settings → Tools → OpenSpec → Direct API**. Use the clipboard workflow instead if you prefer not to configure API keys.

### API test fails

- Verify your API key is correct (Claude: `sk-ant-`, OpenAI: `sk-`, Gemini: Google API key)
- Claude says the key isn't scoped to a workspace, or the workspace wasn't found: enter or correct the **Workspace ID** (starts with `wrkspc_`, from **Console → Settings → Workspaces**)
- Check that your network allows outbound HTTPS to the provider's API endpoint

### Tree doesn't auto-refresh

Check that **Auto-refresh** is enabled in **Settings > Tools > OpenSpec**. You can always manually refresh with the toolbar button.

### Schema management options are disabled

Schema management (fork, new, validate, templates, default selection) requires the OpenSpec CLI v1.3.0 or later.
