# OpenSpec Plugin — Feature Reference

Complete reference for all plugin features, organized by functional area.

> **Maintenance: Living** — updated as part of every relevant change (see the [documentation index](README.md)). For canonical CLI/plugin version facts, see the [Version support](openspec-support.md#version-support) block.

---

## Browsing & Navigation

### Tool Window

The OpenSpec tool window (right sidebar, or **View > Tool Windows > OpenSpec**) has two permanent tabs, plus conditional Explore and Coordination tabs:

| Tab | Purpose |
|-----|---------|
| **Browse** | Master/detail view of specs, changes, and archives: a tree beside a read-only rendered-markdown preview pane. Single-click to preview, double-click to open the file, right-click for context menu actions. Search matches requirement/scenario content, not just labels. |
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

Actions run off the UI thread; on failure the CLI's diagnostic message (and its `fix` suggestion) is surfaced, never raw error output. A `store doctor`-driven **health strip** at the top of the panel shows the highest-severity diagnostic with its `fix` as an inline action.

### Browse Tab Tree Structure

```
OpenSpec
├── Specs
│   ├── user-auth
│   │   └── Requirement: session-management
│   └── data-export
│       └── Requirement: csv-format
├── Changes
│   └── add-greeting [proposed]
│       ├── ✓ proposal       ← done (green)
│       ├── ○ design         ← ready to generate (blue)
│       ├── − specs          ← blocked (gray)
│       └── − tasks
└── Archive
    └── 2026-03-01-initial-setup
```

The requirement and scenario counts under **Specs** are recovered by a line-oriented scanner that
mirrors the OpenSpec CLI's own parsing, so the tree agrees with `openspec show` / `openspec validate`.
The recognition rules:

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

### Rendered Preview Pane (master/detail)

The Browse tab is a **master/detail** view: the tree on the left, a read-only rendered-markdown
**preview pane** on the right, in a horizontal splitter whose width is remembered and whose preview
can be collapsed.

- **Single-click** a node to render its document in the pane; **double-click** still opens the real
  file in the editor. The pane is always read-only — editing stays in the editor.
- **Per node type.** The preview renders a **main capability spec** (`specs/<capability>/spec.md`), a
  change's **proposal / design / tasks**, or a change's **delta spec**
  (`changes/<change>/specs/<capability>/spec.md`). A main spec and a delta spec are interpreted
  according to their own structure and never conflated.
- **Delta operation badges.** In a delta-spec preview, the `ADDED` / `MODIFIED` / `REMOVED` /
  `RENAMED` operation headers are visually badged so a change's proposed deltas read at a glance.
- **Requirement anchoring.** Selecting a **Requirement** node scrolls the preview to that
  requirement's section rather than resetting to the top.
- **On-model.** The preview renders the source markdown of your own files. It does not synthesize,
  score, or show per-spec status/coverage; change-owned state (delta assembly, task progress) is
  sourced from the OpenSpec CLI, never recomputed from files.

Rendering runs off the UI thread (the file is read and rendered on a background thread, then the pane
is updated on the UI thread), so selection stays responsive.

### Full-Text Search

The Browse **search box** (Ctrl/Cmd+F to focus) filters the tree in real time, auto-expanding
matches and restoring the tree when cleared. Matching reaches beyond node labels into **requirement
body text and scenario text**, so a term that appears only inside a requirement's prose — "find where
rate-limiting is specified" — surfaces that requirement and its spec. Content matching happens during
the off-UI-thread model build over your local `openspec/` files; nothing is indexed or persisted.

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
| **Done** | Green checkmark | Artifact file exists with real content |
| **Ready** | Blue filled circle | Dependencies satisfied — ready to generate |
| **Blocked** | Gray empty circle | Waiting on upstream artifacts |

The panel includes a **change selector** dropdown for switching between multiple active changes, and a **Generate** button that targets the next ready artifact.

### Workflow Actions

| Action | Menu | Description |
|--------|------|-------------|
| **Init** | OpenSpec > Init | Initialize OpenSpec in the current project. Creates `openspec/` directory structure. |
| **Propose** | OpenSpec > Propose... | Create a new change with name and description. Scaffolds artifact files. |
| **Fast-Forward** | OpenSpec > Fast-Forward... | Create a change and generate all artifacts in one step via Direct API. |
| **Continue** | OpenSpec > Continue | Generate the next ready artifact in the active change. Requires Direct API. |
| **Explore** | OpenSpec > Explore... | Prompt for an optional topic, assemble the explore prompt (skill instructions + project context + topic), and deliver via configured delivery mode. With Direct API configured, the Explore tab appears in the tool window for inline input and rendered responses. Clipboard and Editor Tab modes use the topic dialog. |
| **Apply** | OpenSpec > Apply | Mark a change as applied. |
| **Verify** | OpenSpec > Verify | Check artifact completeness and task progress. Opens report dialog. |
| **Archive** | OpenSpec > Archive | Move a completed change to `openspec/changes/archive/`. Optionally syncs delta specs. |
| **Bulk Archive** | OpenSpec > Bulk Archive... | Archive multiple completed changes at once with conflict detection. |
| **Sync Specs** | OpenSpec > Sync Specs | Merge delta specs from a completed change into main specs at `openspec/specs/`. Shows preview dialog. |
| **Update** | OpenSpec > Update | Run `openspec update` to refresh agent instruction files. Requires CLI. When the CLI reports leftover legacy files from its skills migration (it exits 0 but asks for `--force` or an interactive run — neither reachable from the console), the plugin raises a **Review legacy cleanup** notice instead of bare success: every CLI-listed file appears with a checkbox and an open-to-inspect link, removal happens through the IDE as a single undoable step (Local History covers it), and a follow-up `openspec update` verifies the result. If that verification shows the CLI *regenerated* the files (a known inconsistency in some CLI versions' tool integrations), the plugin says so plainly and stops re-nagging until the CLI reports something different. Escape hatches: run interactively in the terminal, or **Not now** (no re-nag while the pending set is unchanged). **The plugin never runs `openspec update --force` on your behalf.** |
| **Manage AI Tools** | OpenSpec > Manage AI Tools... | View and configure AI tool integrations detected in your project. |
| **List** | OpenSpec > List | List all specs and changes. |
| **Refresh Tree** | OpenSpec > Refresh Tree | Manually refresh the tool window tree. |
| **Setup Wizard** | OpenSpec > Setup Wizard... | Guided onboarding for new projects. |

### Profile-Aware Action Visibility

Workflow actions respect the active OpenSpec profile. Actions whose workflow is not enabled in the current profile (e.g., `core` vs expanded) appear **visible but disabled** with a tooltip: *"Requires expanded profile. Change in Settings → Tools → OpenSpec."*

| Profile | Enabled Actions |
|---------|----------------|
| **core** | Propose, Explore, Apply, Archive |
| **expanded/custom** | All of the above + Fast-Forward, Continue, Verify, Sync Specs, Bulk Archive |

Utility actions (Init, Validate, List, Refresh, Update, Manage AI Tools, Setup Wizard) are always enabled regardless of profile.

The profile is configured globally via `openspec config profile` or in **Settings → Tools → OpenSpec**. After changing the profile, action enablement updates immediately.

### Scaffolding Detection

The plugin distinguishes placeholder/scaffold content from real content. An artifact file containing only template comments is treated as "not done," preventing false pipeline progression.

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

The plugin scans your project for AI tool configuration directories — **30 tools are detected** in total; a representative subset is shown below:

| Directory | Tool | Type |
|-----------|------|------|
| `.github/` | GitHub Copilot | IDE Panel |
| `.cursor/` | Cursor | IDE Panel |
| `.windsurf/` | Windsurf | IDE Panel |
| `.cline/` | Cline | IDE Panel |
| `.claude/` | Claude Code | CLI |

Detected tools appear in bold in the settings panel and are available as clipboard delivery targets. **CLI** tools receive save-path hints in the guidance text; **IDE Panel** tools receive paste-target guidance.

### Direct API Providers

| Provider | API endpoint | Key format |
|----------|-------------|------------|
| **Claude** (Anthropic) | `api.anthropic.com` | Starts with `sk-ant-` |
| **OpenAI** | `api.openai.com` | Starts with `sk-` |
| **Gemini** (Google) | `generativelanguage.googleapis.com` | Google API key |

API keys are stored securely in your operating system's keychain via IntelliJ's PasswordSafe — never saved in project files.

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

Validates all specs, changes, and config files. Results appear as IDE notifications.

| Mode | Behavior |
|------|----------|
| **Built-in** | Always available. Validates spec format, delta spec structure, and config. |
| **CLI-enhanced** | When CLI is installed: runs `openspec validate --all` for full validation including schema rules. |

Validation also runs automatically at phase transitions (e.g., before archive).

---

## Settings & Configuration

Access via **Settings > Tools > OpenSpec** (or **Preferences > Tools > OpenSpec** on macOS).

### CLI

| Setting | Purpose |
|---------|---------|
| **CLI path** | Path to the `openspec` binary. Click **Detect** to auto-find it. |
| **Status** | Shows CLI version when detected, or "not found". |
| **Version override** | Force a specific OpenSpec schema version. Leave blank to use your project's `config.yaml`. |

### General

| Setting | Purpose |
|---------|---------|
| **Schema profile** | Workflow profile: `spec-driven`, `tdd`, `rapid`, or custom. |
| **Auto-refresh** | Automatically refresh the tree when OpenSpec files change on disk. |
| **Strict validation** | Treat warnings as errors during validation. |

### Config Profile

Read-only display of the current profile name and description from `config.yaml`, plus available workflows from the CLI.

### Schemas

| Setting | Purpose |
|---------|---------|
| **Schema list** | Available workflow schemas, each tagged with where it resolves from (`[project]`, `[user]`, `[package]`); a copy that shadows another source says so (e.g. `[project, shadows package]`). Select one as default for new changes. |
| **Fork** | Fork an existing schema to customize it. |
| **New** | Create a new schema with selected artifact types. |
| **Validate** | Check the selected schema's structure and templates via `openspec schema validate` — structure errors, missing templates, and circular dependencies appear inline below the list. |
| **Open Templates** | Open the selected schema's artifact templates in the editor (paths resolved via `openspec templates`); missing template files are reported, never created. |

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
| **Test** | Verify your API key and connection. |

> An API key is only needed for Direct API delivery. Clipboard and Editor Tab modes work without any API key.

---

## Troubleshooting

### "CLI: not found" in the status bar

The plugin works without the CLI, but some features (schema management, CLI-enhanced validation, Update action) require it.

1. Install: `npm i -g @fission-ai/openspec`
2. If installed but not detected: **Settings > Tools > OpenSpec > Detect**, or set the path manually
3. On macOS, GUI apps don't inherit your terminal's PATH — the plugin tries your login shell, Homebrew paths, and `/usr/local/bin` automatically

### "Fast-Forward", "Continue", or other actions are grayed out

**Profile check:** If the tooltip says *"Requires expanded profile"*, your active profile doesn't include that workflow. Switch to an expanded or custom profile in **Settings → Tools → OpenSpec → Config Profile**.

**API check:** Fast-Forward and Continue also require an AI provider and API key configured in **Settings → Tools → OpenSpec → Direct API**. Use the clipboard workflow instead if you prefer not to configure API keys.

### API test fails

- Verify your API key is correct (Claude: `sk-ant-`, OpenAI: `sk-`, Gemini: Google API key)
- Check that your network allows outbound HTTPS to the provider's API endpoint

### Tree doesn't auto-refresh

Check that **Auto-refresh** is enabled in **Settings > Tools > OpenSpec**. You can always manually refresh with the toolbar button.

### Schema management options are disabled

Schema management (fork, new, validate, templates, default selection) requires the OpenSpec CLI v1.3.0 or later.
