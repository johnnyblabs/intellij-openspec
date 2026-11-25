# Getting Started: Standalone API User

*For developers with an API key who want a fully self-contained workflow — no external AI tool needed.*

> **Maintenance: Reference** — stable; updated only when the described setup flow changes (see the [documentation index](README.md)).

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
3. **Workspace ID (optional)** — Claude only. Needed when your Claude API key isn't scoped to a single workspace (for example, a personal key that can use any of your workspaces). Copy the ID, which starts with `wrkspc_`, from **Console → Settings → Workspaces**. Leave it blank for a key that belongs to one workspace. It's a regular setting, not a secret.
4. **Model** — Pre-populated for your provider. Edit if you want a specific model.
5. **Test** — Click to verify your key and connection. If Claude reports that the key isn't scoped to a workspace, fill in the Workspace ID and test again.

Then go to the **Tools & Delivery** tab and set **Deliver via** to **"Generate via API"**.

## Step 2: Propose a Change

This is the flagship feature for API users:

1. Go to **OpenSpec > Propose...**
2. Describe what you want to build (e.g., "Add user authentication with email and password"). Optionally give the change a name; otherwise it's derived from the description.
3. Click OK

The plugin:
- Creates the change directory
- Generates the **proposal** via your API provider
- Feeds the proposal into the **specs** prompt and generates them
- Feeds proposal + specs into the **design** prompt and generates it
- Feeds everything into the **tasks** prompt and generates it

All four artifacts are produced in sequence, each building on the last, with your description sent as the request. The run stops once the change is ready to implement. The [Workflow Action Panel](feature-reference.md#artifact-pipeline) updates in real-time as each artifact completes.

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

If you prefer to review each artifact before generating the next, create the change without artifacts and generate them one at a time:

1. **OpenSpec > New Change...** — create the change (this needs the `new` workflow, so switch to a custom profile that includes it in **Settings → Tools → OpenSpec → Config Profile**)
2. Click **Generate** — generates the next ready artifact via API
3. Review the output
4. Click **Generate** again for the next artifact
5. Repeat until all artifacts are complete

This is the same as Propose but one artifact at a time. See [Workflow Actions](feature-reference.md#workflow-actions) for details.

---

## You Might Also Want to Explore

- **[Spec Browser Guide](getting-started-browser.md)** — The browsing features work the same way regardless of delivery mode
- **[IDE-First Developer Guide](getting-started-copilot.md)** — If you also use Copilot or Cursor and want to mix clipboard and API workflows
- **[Feature Reference](feature-reference.md)** — Complete reference for all plugin features
