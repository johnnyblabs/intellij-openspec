# Getting Started: Standalone API User

*For developers with an API key who want a fully self-contained workflow — no external AI tool needed.*

---

## Prerequisites

| Requirement | Notes |
|-------------|-------|
| **IntelliJ IDEA 2024.2+** | Community or Ultimate edition |
| **OpenSpec plugin** | Install from [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/30678-openspec) |
| **An API key** | For Claude (Anthropic), OpenAI, or Gemini (Google) |

The OpenSpec CLI is optional — the plugin has built-in scaffolding that handles project init and change creation without it.

---

## How It Works

In this workflow, the plugin handles **everything**: proposing changes, calling the AI API, writing responses to disk, and advancing the pipeline. You don't need a separate AI tool, CLI, or clipboard. The plugin assembles prompts, sends them to your chosen API provider, and writes the generated artifacts directly.

---

## Step 1: Configure Direct API

Go to **Settings > Tools > OpenSpec > Direct API**.

1. **Provider** — Select `Claude`, `OpenAI`, or `Gemini`
2. **API key** — Enter your key. It's stored in your OS keychain via IntelliJ's PasswordSafe — never in project files. See [Direct API settings](feature-reference.md#direct-api) for key format details.
3. **Model** — Pre-populated for your provider. Edit if you want a specific model.
4. **Test** — Click to verify your key and connection

Then go to the **Tools & Delivery** tab and set **Deliver via** to **"Generate via API"**.

## Step 2: Fast-Forward a Change

This is the flagship feature for API users:

1. Go to **OpenSpec > Fast-Forward...**
2. Enter a change name (e.g., `add-user-auth`) and a brief description
3. Click OK

The plugin:
- Creates the change directory
- Generates the **proposal** via your API provider
- Feeds the proposal into the **design** prompt and generates it
- Feeds proposal + design into the **specs** prompt and generates it
- Feeds everything into the **tasks** prompt and generates it

All four artifacts are produced in sequence, each building on the last. The [Workflow Action Panel](feature-reference.md#artifact-pipeline) updates in real-time as each artifact completes.

## Step 3: Review the Generated Artifacts

Open each file from the Browse tab tree and review:

- `proposal.md` — Does the problem statement match your intent?
- `design.md` — Are the technical decisions sound?
- `specs/` — Are the requirements complete and testable?
- `tasks.md` — Is the task breakdown actionable?

If an artifact needs rework, right-click it in the tree and select **Regenerate**, or edit it manually.

## Step 4: Implement

Open `tasks.md` and work through the checklist. Check boxes as you complete each task:

```markdown
- [x] 1.1 Create service class
- [ ] 1.2 Add configuration
```

The specs define *what* to build. The design defines *how*. The tasks break it into steps.

## Step 5: Archive

Once implementation is complete:

1. **OpenSpec > Verify** — checks artifact completeness and task progress
2. Click **Archive** in the Workflow Action Panel
3. Optionally [sync delta specs](feature-reference.md#workflow-actions) into your main specs

---

## Incremental Generation

If you prefer to review each artifact before generating the next, use **Continue** instead of Fast-Forward:

1. **OpenSpec > Propose...** — create the change
2. Click **Generate** — generates the next ready artifact via API
3. Review the output
4. Click **Generate** again for the next artifact
5. Repeat until all artifacts are complete

This is the same as Fast-Forward but one artifact at a time. See [Workflow Actions](feature-reference.md#workflow-actions) for details.

---

## You Might Also Want to Explore

- **[Spec Browser Guide](getting-started-browser.md)** — The browsing features work the same way regardless of delivery mode
- **[IDE-First Developer Guide](getting-started-copilot.md)** — If you also use Copilot or Cursor and want to mix clipboard and API workflows
- **[Feature Reference](feature-reference.md)** — Complete reference for all plugin features
