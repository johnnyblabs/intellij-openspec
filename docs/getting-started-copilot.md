# Getting Started: OpenSpec Plugin + GitHub Copilot

*Last verified: 2026-03-11 against plugin source*

This guide walks you through setting up the OpenSpec IntelliJ plugin and completing your first spec-driven change using GitHub Copilot as your AI tool. By the end, you'll have proposed, generated, implemented, and archived a change — the full OpenSpec lifecycle.

---

## Table of Contents

- [Prerequisites](#prerequisites)
- [Part 1: Plugin Settings](#part-1-plugin-settings)
  - [Opening Settings](#opening-settings)
  - [OpenSpec CLI](#openspec-cli)
  - [General](#general)
  - [Tools & Delivery](#tools--delivery)
  - [Direct API (Optional)](#direct-api-optional)
- [Part 2: Core Concepts](#part-2-core-concepts)
  - [What is OpenSpec?](#what-is-openspec)
  - [The Artifact Pipeline](#the-artifact-pipeline)
  - [Where OpenSpec Meets AI](#where-openspec-meets-ai)
- [Part 3: Worked Example](#part-3-worked-example)
  - [Setup: Initialize Your Project](#setup-initialize-your-project)
  - [Step 1: Propose a Change](#step-1-propose-a-change)
  - [Step 2: Generate the Proposal](#step-2-generate-the-proposal)
  - [Step 3: Generate the Design](#step-3-generate-the-design)
  - [Step 4: Generate Specs](#step-4-generate-specs)
  - [Step 5: Generate Tasks](#step-5-generate-tasks)
  - [Step 6: Implement](#step-6-implement)
  - [Step 7: Archive](#step-7-archive)
- [What's Next](#whats-next)

---

## Prerequisites

| Requirement | Notes |
|-------------|-------|
| **IntelliJ IDEA 2024.2+** | Community or Ultimate edition |
| **OpenSpec plugin** | Install from JetBrains Marketplace or build from source |
| **GitHub Copilot** | Copilot extension installed and signed in inside IntelliJ |
| **OpenSpec CLI** | `npm install -g openspec-dev` — the plugin auto-detects it |
| **Java 21+** | Required to build the plugin from source (not needed if installed from Marketplace) |

After installing both the OpenSpec plugin and Copilot, restart IntelliJ. You should see an **OpenSpec** tool window icon on the right sidebar and an **OpenSpec** menu in the top menu bar.

---

## Part 1: Plugin Settings

### Opening Settings

Navigate to **Settings > Tools > OpenSpec** (or **Preferences > Tools > OpenSpec** on macOS).

The settings panel has three sections stacked vertically: **OpenSpec CLI**, **General**, and a tabbed pane with **Tools & Delivery** and **Direct API** tabs.

### OpenSpec CLI

| Field | Description |
|-------|-------------|
| **CLI path** | Path to the `openspec` executable. Usually auto-detected. Use the file browser button to select it manually if needed. |
| **Detect** | Click to auto-detect the CLI. The plugin searches your PATH, login shell, and common locations (`/opt/homebrew/bin`, `/usr/local/bin`). On macOS, it resolves your login shell's PATH since GUI apps don't inherit terminal environment variables. |
| **Status indicator** | Shows green "OpenSpec CLI v*X.Y.Z*" when found, or orange "OpenSpec CLI not found" when missing. |
| **Version override** | Force a specific OpenSpec schema version (e.g., `1.2.0`). Leave blank to use the version from your project's `openspec/config.yaml`. Only needed if you want to override the project config. Suggestions: `1.0.0`, `1.1.0`, `1.2.0`. |

> **Tip:** If the CLI isn't detected, make sure you've installed it globally with `npm install -g openspec-dev`, then click **Detect** again.

### General

| Field | Description |
|-------|-------------|
| **Schema profile** | The workflow profile used for new changes. Options: `spec-driven` (full proposal → design → specs → tasks pipeline), `tdd` (test-first), `rapid` (lightweight). Editable — you can type a custom profile name. Most users should start with `spec-driven`. |
| **Auto-refresh tool window on file changes** | When checked (default: on), the tool window tree automatically refreshes when OpenSpec files change on disk. Useful when an external AI tool writes artifact files directly. |
| **Strict validation (warnings become errors)** | When checked (default: off), validation warnings are promoted to errors. Useful for enforcing strict compliance, but can be noisy during early development. |

### Tools & Delivery

This tab controls how the plugin delivers generated prompts to your AI tool.

**Detected tools** — The plugin scans your project root for AI tool configuration directories (`.github/` for Copilot, `.claude/` for Claude Code, `.cursor/` for Cursor, etc.). Detected tools appear in bold, e.g., "Detected: **GitHub Copilot**, **Claude Code**".

**Deliver via** — A unified dropdown that determines what happens when you click the Generate button:

| Option | What it does |
|--------|--------------|
| **Copy for GitHub Copilot** | Copies the AI prompt to your clipboard. You paste it into Copilot Chat. Appears automatically when `.github/` is detected in your project. |
| **Copy for [Other Tool]** | Same clipboard behavior, tailored message for the detected tool. One entry per detected tool. |
| **Open in Editor Tab** | Opens the prompt in a temporary editor tab so you can review it before pasting into any AI tool. |
| **Generate via API** | Sends the prompt directly to an AI provider's API (Claude, OpenAI, or Gemini) and writes the response to disk automatically. Requires an API key on the Direct API tab. |

For this guide, select **"Copy for GitHub Copilot"**. This is the recommended delivery mode when using Copilot.

### Direct API (Optional)

> **Note:** You don't need to configure this section if you're using Copilot. Direct API is for users who want fully automated generation without copy-pasting. Skip ahead to [Part 2](#part-2-core-concepts) if you're following the Copilot workflow.

| Field | Description |
|-------|-------------|
| **Provider** | Select your AI provider: Claude (Anthropic), OpenAI, or Gemini (Google). Select "None" to disable. |
| **API key** | Your API key for the selected provider. Stored securely in your operating system's keychain (macOS Keychain, Windows Credential Manager, or Linux Secret Service) — never saved in project files. Masked with bullet points after entry. |
| **Model** | The model to use for generation. Pre-populated with available models for the selected provider. Editable — you can type a custom model ID. |
| **Test** | Click to verify your API key works. Shows a green success message with the provider and model name, or a red error message with details. |

---

## Part 2: Core Concepts

### What is OpenSpec?

OpenSpec is a **spec-driven development framework**. Instead of jumping straight into code, you describe what you want to build through a structured sequence of artifacts, then implement against that specification.

A **change** is OpenSpec's unit of work — a named directory containing all the artifacts for one feature, fix, or improvement. Changes live in `openspec/changes/<name>/` while active, and move to `openspec/changes/archive/` when complete.

The framework is tool-agnostic: it doesn't care whether you use Copilot, Claude Code, Cursor, or type everything by hand. It provides the structure; you choose the AI.

### The Artifact Pipeline

Every change follows a **dependency pipeline** — a directed acyclic graph (DAG) of artifacts where each builds on the ones before it:

```
proposal  →  design  →  specs  →  tasks
                ↗
```

| Artifact | Purpose | Depends on |
|----------|---------|------------|
| **proposal** | *Why* — the problem, what changes, and impact | Nothing (first artifact) |
| **design** | *How* — technical decisions, architecture, trade-offs | proposal |
| **specs** | *What* — testable requirements in Given-When-Then format | proposal |
| **tasks** | *Do* — implementation checklist with checkboxes | design, specs |

Each artifact has a **status** visible in the plugin's pipeline visualization:

| Status | Icon | Meaning |
|--------|------|---------|
| **Done** | Green checkmark | Artifact file exists with real content |
| **Ready** | Blue filled circle (highlighted) | Dependencies satisfied — ready to generate |
| **Blocked** | Gray empty circle | Waiting on upstream artifacts |

The pipeline chips appear in the **Workflow Action Panel** at the bottom of the OpenSpec tool window. You can click any done chip to open its file, or right-click to regenerate it.

### Where OpenSpec Meets AI

The interplay between OpenSpec and AI is simple:

> **OpenSpec** decides *what to ask*. **AI** decides *what to answer*.

At each step in the pipeline:

1. **OpenSpec generates a prompt** — The plugin assembles context from your project config, completed artifacts, and schema-specific instructions into a detailed prompt.
2. **You deliver the prompt to AI** — With Copilot, this means pasting into Copilot Chat.
3. **AI generates the content** — Copilot produces the artifact (proposal text, design decisions, spec scenarios, task lists).
4. **You save the response** — Copy Copilot's output and save it to the file path shown in the plugin's guidance panel.
5. **OpenSpec advances the pipeline** — The plugin detects the new file, updates the DAG, and shows the next ready artifact.

Throughout this guide, each step is marked to show who's driving:

> **`OPENSPEC`** — The framework or plugin is driving this step (generating prompts, managing state, advancing the pipeline).

> **`AI`** — Copilot (or your AI tool) is generating content at this step.

---

## Part 3: Worked Example

Let's walk through a complete change. We'll add a "greeting message" feature to a hypothetical project — simple enough to follow, complex enough to show every step.

### Setup: Initialize Your Project

> **`OPENSPEC`** — Framework setup

If your project doesn't have OpenSpec yet, initialize it:

1. Open your project in IntelliJ
2. Go to **OpenSpec > Init** in the top menu bar
3. This creates the `openspec/` directory structure:
   ```
   openspec/
   ├── config.yaml       ← Project configuration
   └── specs/            ← Main specs (accumulated over time)
   ```
4. Check the **OpenSpec tool window** (right sidebar) — you should see a tree with **Specs**, **Changes**, and **Archive** nodes

The `config.yaml` defines your project context. Open it and verify it looks reasonable:

```yaml
schema: spec-driven
version: "1.2.0"

profile:
  name: MyProject
  description: A brief description of your project
  language: Java 21
  framework: Spring Boot 3.x
```

> **Tip:** The more specific your `config.yaml` profile is, the better the AI-generated artifacts will be. Add your actual framework, language version, and a meaningful description.

### Step 1: Propose a Change

> **`OPENSPEC`** — Creating the change scaffold

1. Go to **OpenSpec > Propose...** in the menu bar
2. Enter a name in kebab-case: `add-greeting-message`
3. Optionally enter a brief description

The plugin runs `openspec new change "add-greeting-message"` and creates:

```
openspec/changes/add-greeting-message/
├── .openspec.yaml    ← Change metadata (status, schema)
├── proposal.md       ← Scaffolded (placeholder content)
├── design.md         ← Scaffolded
└── tasks.md          ← Scaffolded
```

In the **tool window**, the change appears under **Changes** with the pipeline chips:

```
● proposal  →  ○ design  →  ○ specs  →  ○ tasks
  (ready)      (blocked)    (blocked)    (blocked)
```

The **Generate** button shows: **"Generate proposal → clipboard"**

### Step 2: Generate the Proposal

> **`OPENSPEC`** — Generating the prompt

1. Click the **"Generate proposal → clipboard"** button in the Workflow Action Panel
2. The plugin assembles a prompt containing:
   - Your project context from `config.yaml`
   - The proposal template (sections: Why, What Changes, Capabilities, Impact)
   - Schema-specific instructions for writing a good proposal
3. A confirmation appears: **"Copied to clipboard"** with guidance below:

   ```
   ✓ Copied to clipboard
   Paste into GitHub Copilot — then save to: proposal.md
   ```

> **`AI`** — Copilot generates the proposal

4. Open **Copilot Chat** in IntelliJ (click the Copilot icon in the sidebar or use the keyboard shortcut)
5. Paste the prompt from your clipboard into the chat input
6. Copilot generates a complete proposal with Why, What Changes, Capabilities, and Impact sections
7. **Copy Copilot's response**
8. Open `openspec/changes/add-greeting-message/proposal.md` in the editor
9. Replace the scaffold content with Copilot's response and **save the file**

> **`OPENSPEC`** — Pipeline advances

The plugin's file watcher detects the change. The pipeline updates automatically:

```
✓ proposal  →  ● design  →  ● specs  →  ○ tasks
  (done)       (ready)      (ready)     (blocked)
```

The Generate button now shows: **"Generate design → clipboard"**

### Step 3: Generate the Design

> **`OPENSPEC`** — Generating the prompt (with context)

1. Click **"Generate design → clipboard"**
2. This time, the prompt includes your **completed proposal** as context — the AI will reference your stated goals and capabilities when writing the design
3. Guidance appears: **"Copied to clipboard"**

> **`AI`** — Copilot generates the design

4. Paste into **Copilot Chat**
5. Copilot generates the design document with Context, Goals/Non-Goals, Decisions (with rationale and alternatives), and Risks/Trade-offs
6. Copy the response, save to `openspec/changes/add-greeting-message/design.md`

> **`OPENSPEC`** — Pipeline advances

```
✓ proposal  →  ✓ design  →  ● specs  →  ○ tasks
  (done)       (done)       (ready)     (blocked)
```

Notice how each artifact builds on the previous ones. The design references the proposal's goals. The specs (next) will reference both.

### Step 4: Generate Specs

> **`OPENSPEC`** — Generating the prompt (with accumulated context)

1. Click **"Generate specs → clipboard"**
2. The prompt now includes both the **proposal** and **design** as context
3. Guidance appears: **"Copied to clipboard"**

> **`AI`** — Copilot generates the specs

4. Paste into **Copilot Chat**
5. Copilot generates formal specifications with:
   - Requirements using RFC 2119 keywords (SHALL, SHOULD, MAY)
   - Scenarios in Given-When-Then format
   - One spec file per capability listed in the proposal
6. Copy the response and save to the appropriate spec file(s) under `openspec/changes/add-greeting-message/specs/`

**About delta specs:** The specs generated here are *delta specs* — they describe what's new or changed in *this* change. When you archive the change later, these delta specs get merged into your project's main specs at `openspec/specs/`. This keeps your main specs as a living document that grows with each change.

> **`OPENSPEC`** — Pipeline advances

```
✓ proposal  →  ✓ design  →  ✓ specs  →  ● tasks
  (done)       (done)       (done)      (ready)
```

### Step 5: Generate Tasks

> **`OPENSPEC`** — Generating the prompt (full context)

1. Click **"Generate tasks → clipboard"**
2. The prompt includes the **proposal**, **design**, and **specs** — the AI has full context of what to build, how to build it, and the exact requirements
3. Guidance appears: **"Copied to clipboard"**

> **`AI`** — Copilot generates the task list

4. Paste into **Copilot Chat**
5. Copilot generates a structured task list with:
   - Numbered groups (e.g., "1. Setup", "2. Core Implementation")
   - Checkbox format: `- [ ] 1.1 Task description`
   - Tasks ordered by dependency
6. Copy the response, save to `openspec/changes/add-greeting-message/tasks.md`

> **`OPENSPEC`** — All artifacts complete

```
✓ proposal  →  ✓ design  →  ✓ specs  →  ✓ tasks
  (done)       (done)       (done)      (done)
```

The Generate button is replaced by an **Archive** button, and the panel shows that validation has been run automatically for the completed pipeline state.

Your change now has a complete specification. Four artifacts, each building on the last, transforming a vague idea into a concrete implementation plan.

### Step 6: Implement

> **`OPENSPEC`** — Tasks guide implementation

Now you write the code. Open `tasks.md` and work through the checklist:

```markdown
## 1. Setup

- [ ] 1.1 Create GreetingService class
- [ ] 1.2 Add greeting message configuration

## 2. Core Implementation

- [ ] 2.1 Implement greeting logic
- [ ] 2.2 Add unit tests
```

As you complete each task, check the box:

```markdown
- [x] 1.1 Create GreetingService class
```

The specs tell you *what* to build (the requirements and scenarios). The design tells you *how* to build it (architecture decisions). The tasks break it into small, verifiable steps.

> **`AI`** — Copilot assists with implementation

You can use Copilot throughout implementation — for code completion, asking questions about the design, or generating test cases. The difference is that now Copilot has a clear specification to work against, not just a vague request.

> **Tip:** If you're using Copilot custom prompts (`.github/prompts/`), the OpenSpec plugin provides `/opsx-apply` as a Copilot prompt that reads your tasks and helps implement them one by one.

### Step 7: Archive

> **`OPENSPEC`** — Archiving the completed change

Once all tasks are done:

1. In the Workflow Action Panel, click the **Archive** button that appears automatically once all tasks are complete
2. The plugin checks that all artifacts are complete and all tasks are checked
3. The panel shows the validation results from the automatic pre-archive validation
4. If delta specs exist, it asks whether to **sync them to main specs** (recommended — this merges your change's specs into `openspec/specs/`)
5. The change directory moves to the archive:
   ```
   openspec/changes/archive/2026-03-09-add-greeting-message/
   ```
6. The tool window tree updates — the change moves from **Changes** to **Archive**

Your main specs at `openspec/specs/` now include the requirements from this change. The next change you propose will build on this accumulated knowledge.

---

## What's Next

- **Try Direct API mode** — Configure an API key in Settings > Tools > OpenSpec > Direct API to generate artifacts without copy-pasting. The **Generate All** button chains all artifacts automatically.
- **Explore the tool window** — Click on spec files in the tree to browse requirements. Right-click artifacts to regenerate them. Use the change selector dropdown when working on multiple changes.
- **Read the full reference** — See the [README](../README.md) for complete documentation of all features, menu actions, validation, and troubleshooting.
- **OpenSpec CLI** — Run `openspec --help` to explore the CLI directly. The plugin wraps most CLI commands, but the CLI offers additional flexibility for scripting and automation.
