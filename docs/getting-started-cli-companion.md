# Getting Started: CLI Companion

*For developers using Claude Code, Gemini CLI, or other terminal-based AI tools alongside IntelliJ.*

---

## Prerequisites

| Requirement | Notes |
|-------------|-------|
| **IntelliJ IDEA 2024.2+** | Community or Ultimate edition |
| **OpenSpec plugin** | Install from [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/30678-openspec) |
| **OpenSpec CLI** | `npm install -g @fission-ai/openspec` |
| **A CLI-based AI tool** | Claude Code, Gemini CLI, or similar |

No API key or plugin-side AI configuration is needed — you'll use your existing terminal AI tool.

---

## How It Works

In this workflow, the plugin is your **spec dashboard** while your terminal AI does the generation work. You use IntelliJ for browsing specs and copying prompts. You use the terminal for running AI commands that generate artifacts.

---

## Step 1: Configure Delivery Mode

Go to **Settings > Tools > OpenSpec > Tools & Delivery**.

The plugin auto-detects AI tool configurations in your project (e.g., `.claude/` for Claude Code). Select **"Copy for Claude Code"** (or your tool) from the **Deliver via** dropdown.

This ensures that when you click Generate, the copied prompt includes **save-path hints** — guidance telling your terminal AI where to write the output file.

## Step 2: Initialize and Propose

If your project isn't already initialized:

1. **OpenSpec > Init** — creates the `openspec/` directory structure
2. **OpenSpec > Propose...** — enter a change name (e.g., `add-user-auth`)

The plugin scaffolds the change directory. The [Workflow Action Panel](feature-reference.md#artifact-pipeline) shows your artifact pipeline.

## Step 3: Generate with Your Terminal AI

1. Click **Generate** in the Workflow Action Panel
2. The plugin copies the prompt to your clipboard with a guidance message:

   ```
   ✓ Copied to clipboard
   Paste into Claude Code — save to: openspec/changes/add-user-auth/proposal.md
   ```

3. Switch to your terminal and paste the prompt into Claude Code (or your CLI tool)
4. The AI generates the artifact content
5. The output is saved to the path indicated in the guidance

The plugin's [auto-refresh](feature-reference.md#general) detects the new file and advances the pipeline automatically. The next artifact becomes ready.

## Step 4: Browse and Track

While your terminal AI works, use the plugin to:

- **Browse tab** — See the tree of specs, changes, and archives
- **Explore tab** — View assembled project context (config, changes, specs, tools) in Markdown. Use the **Copy** button to feed this context into your CLI tool

See the [Tool Window reference](feature-reference.md#tool-window) for details on each tab.

## Step 5: Validate and Archive

Once all artifacts are generated and implementation is complete:

1. **OpenSpec > Verify** — checks artifact completeness and task progress
2. Click **Archive** in the Workflow Action Panel
3. Optionally sync delta specs into your main specs

See [Validation](feature-reference.md#validation) for details on built-in vs CLI-enhanced validation.

---

## You Might Also Want to Explore

- **[Standalone API User Guide](getting-started-api.md)** — If you want to skip copy-pasting and have the plugin call AI APIs directly
- **[Spec Browser Guide](getting-started-browser.md)** — If team members just need to read specs without generating anything
- **[Feature Reference](feature-reference.md)** — Complete reference for all plugin features
