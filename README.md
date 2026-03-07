# OpenSpec IntelliJ Plugin

An IntelliJ IDEA plugin for the [OpenSpec](https://github.com/openspec-dev) spec-driven development framework. Browse specs, manage changes, generate artifacts with AI, and validate your project — all from within your IDE.

---

## Requirements

- **IntelliJ IDEA 2024.2 or later** (Community or Ultimate)
- **Java 21 JDK** — Required for development and plugin runtime (IntelliJ 2024.2+ runs on JBR 21)

---

## Installation

### Plugin

Install from the JetBrains Marketplace (search for **OpenSpec**), or build from source:

```bash
git clone <repo-url>
cd OpenSpecPlugin
./gradlew build
# Install from: build/distributions/OpenSpec-*.zip
```

### OpenSpec CLI (recommended)

The plugin works in **built-in mode** without the CLI, but the full experience — artifact DAGs, AI instruction generation, and validation — requires the CLI:

```bash
npm i -g openspec-dev
```

The plugin auto-detects the CLI at startup. If it can't find it, configure the path manually in **Settings > Tools > OpenSpec**.

---

## Quick Start

If you've never used OpenSpec before, here's the entire workflow in five steps:

### Step 1: Initialize your project

**OpenSpec menu > Init** (or use the toolbar)

This creates the `openspec/` directory structure in your project root with a `config.yaml` and a starter spec. If the CLI is installed, it runs `openspec init`; otherwise, the plugin scaffolds the files directly.

After init, you'll see the **OpenSpec tool window** on the right side of your IDE with three sections:
- **Specs** — your domain specifications
- **Changes** — active work in progress
- **Archive** — completed changes

### Step 2: Propose a change

**OpenSpec menu > Propose...** (or right-click **Changes** in the tree)

A dialog asks for a **change name** and **description**. Use a slug-style name like `add-user-auth` or `fix-payment-flow`.

This creates a change directory under `openspec/changes/<your-change-name>/` with a `proposal.md` artifact. The tree updates automatically — you'll see your new change appear with a **[proposed]** label.

### Step 3: Generate artifacts

This is where AI comes in. Each change has a **dependency graph (DAG)** of artifacts — proposal, design, specs, tasks — that need to be generated in order.

**Right-click your change > Generate Artifact...**

The tree shows each artifact's status:
- **&#x2713; proposal** (green) — done, already exists
- **&#x25CB; design** (blue, bold) — ready to generate
- **&#x2212; tasks (needs: design, specs)** (gray, italic) — blocked until dependencies are done

When you choose "Generate Artifact...", you pick a **delivery mode**:

| Mode | What happens |
|------|-------------|
| **Copy to Clipboard** | Copies the AI-ready prompt to your clipboard. Paste it into Claude, ChatGPT, Cursor, or any AI tool you prefer. |
| **Open in Editor Tab** | Opens the prompt in a scratch editor tab so you can review or edit it before sending to an AI tool. |
| **Generate via API** | Calls the AI API directly (Claude or OpenAI), generates the content, and writes the file. Requires an API key configured in settings. |

**To generate all remaining artifacts at once:**
Right-click your change > **Generate All Artifacts** (requires API key configured).

This walks the DAG automatically: generates ready artifacts, re-checks status, generates newly unblocked artifacts, and repeats until the change is complete.

### Step 4: Apply the change

Once all artifacts are generated and you've implemented the work:

**OpenSpec menu > Apply** (or right-click the change)

This marks the change as **[applied]**, indicating the work described in the change has been implemented in your codebase.

### Step 5: Archive the change

When the change is fully complete and merged:

**OpenSpec menu > Archive** (or right-click the change)

This moves the change from **Changes** to **Archive**, keeping a historical record while keeping your active workspace clean.

---

## The Tool Window

The OpenSpec tool window (right sidebar) is your command center.

### Tree structure

```
OpenSpec
├── Specs
│   ├── actions          ← domain spec files
│   │   └── Requirement: handle-payments
│   └── users
│       └── Requirement: user-auth
├── Changes
│   └── add-user-auth [proposed]
│       ├── ✓ proposal       ← done (green)
│       ├── ○ design         ← ready to generate (blue)
│       ├── − specs (needs: design)   ← blocked (gray)
│       ├── − tasks (needs: design, specs)
│       └── feature-delta.yaml   ← delta spec
└── Archive
    └── initial-setup
```

### Interacting with the tree

| Action | How |
|--------|-----|
| Open a file | Double-click any node with a file path |
| See available actions | Right-click a node |
| Generate an artifact | Right-click a ready artifact (&#x25CB;) or a change |
| Open a completed artifact | Right-click a done artifact (&#x2713;) > Open File |
| Refresh | Toolbar button, or **OpenSpec menu > Refresh Tree** |

### Status bar

The bottom of the tool window shows:
- **CLI status** — green if detected, red if missing (with version number)
- **AI tools** — detected AI tool configurations in your project (Claude Code, Copilot, Cursor, etc.)

---

## Settings

**Settings > Tools > OpenSpec**

### General

| Setting | Purpose |
|---------|---------|
| **Version override** | Force a specific OpenSpec schema version |
| **CLI path** | Manual path to the `openspec` binary. Click **Detect** to auto-find it. |
| **Schema profile** | Workflow profile: `spec-driven`, `tdd`, or `rapid` |
| **Auto-refresh** | Automatically refresh the tree when OpenSpec files change |
| **Strict validation** | Treat warnings as errors during validation |

### AI Configuration

| Setting | Purpose |
|---------|---------|
| **AI provider** | `None`, `Claude`, or `OpenAI` — for direct API generation |
| **API key** | Your API key, stored securely in the OS keychain via IntelliJ's PasswordSafe |
| **Model** | Which model to use (e.g., `claude-sonnet-4-5-20250514`, `gpt-4o`) |
| **Test** | Verifies your API key and connection work |
| **Detected AI tools** | Shows which AI tool configs exist in your project (`.claude/`, `.cursor/`, etc.) |

> **Note:** An API key is only needed for "Generate via API" mode. You can always use "Copy to Clipboard" or "Open in Editor Tab" without any API key — just paste the prompt into your preferred AI tool.

---

## Workflow Patterns

### Pattern 1: Clipboard workflow (no API key needed)

Best for teams using external AI tools like Claude Code, Cursor, or Copilot Chat:

1. Propose a change
2. Right-click a ready artifact > **Generate Artifact...** > **Copy to Clipboard**
3. Paste into your AI tool
4. Save the AI's output to the artifact file path shown in the tree
5. The tree auto-refreshes and shows the artifact as done
6. Repeat for the next ready artifact

### Pattern 2: Direct API workflow (API key required)

Best for solo developers who want a fully integrated experience:

1. Configure an API key in **Settings > Tools > OpenSpec**
2. Propose a change
3. Right-click the change > **Generate All Artifacts**
4. The plugin walks the DAG, generates each artifact, and writes the files
5. Review the generated artifacts in your editor
6. Apply and archive when done

### Pattern 3: Mixed workflow

Use clipboard mode for artifacts that need careful prompting or review, and direct API for routine artifacts:

1. Generate the proposal and design via clipboard (review the AI's output carefully)
2. Once design is solid, use **Generate All** for specs and tasks

---

## Validation

**OpenSpec menu > Validate** (or toolbar button)

Validates all specs, changes, and config files. Results appear as IDE notifications. If the CLI is available, it runs `openspec validate --all`; otherwise, the plugin uses built-in validation rules.

Validation also runs as background inspections in the editor — you'll see warnings and errors inline as you edit spec files.

---

## Menu Reference

All actions are available from the **OpenSpec** top-level menu and the tool window toolbar:

| Action | Description |
|--------|-------------|
| **Init** | Initialize OpenSpec in the current project |
| **Propose...** | Create a new change with name and description |
| **Apply** | Mark a change as applied |
| **Archive** | Move a completed change to the archive |
| **Validate** | Run validation on all specs and changes |
| **List** | List all specs and changes |
| **Generate Artifact...** | Fetch AI instructions for an artifact and deliver them |
| **Generate All Artifacts** | Generate all remaining artifacts via direct API |
| **Refresh Tree** | Manually refresh the tool window tree |

---

## Troubleshooting

### "CLI: not found" in the status bar

The plugin works without the CLI but artifact DAG features require it.

1. Install: `npm i -g openspec-dev`
2. If installed but not detected: **Settings > Tools > OpenSpec > Detect**, or set the path manually
3. On macOS, GUI apps don't inherit your terminal's PATH — the plugin tries your login shell, Homebrew paths, and `/usr/local/bin` automatically

### Tree shows old artifacts instead of DAG status

The artifact DAG view (&#x2713;/&#x25CB;/&#x2212; icons) requires the CLI. Without it, the tree falls back to showing raw artifact files and missing artifacts. Install the CLI to get the full DAG view.

### "Generate All" is grayed out

This action requires an AI provider and API key configured in **Settings > Tools > OpenSpec**. Individual "Generate Artifact..." still works — it offers clipboard and editor tab modes that don't need an API key.

### API test fails

- Verify your API key is correct
- Claude keys start with `sk-ant-`; OpenAI keys start with `sk-`
- Check that your network allows outbound HTTPS to `api.anthropic.com` or `api.openai.com`

### Tree doesn't auto-refresh

Check that **Auto-refresh** is enabled in **Settings > Tools > OpenSpec**. You can always manually refresh with the toolbar button.
