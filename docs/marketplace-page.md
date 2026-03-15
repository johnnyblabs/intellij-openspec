# JetBrains Marketplace Page Content

Reference for filling in the [plugin edit page](https://plugins.jetbrains.com/plugin/30678-openspec/edit).

---

## Getting Started

Paste this HTML into the "Getting Started" field:

```html
<h2>Quick Start</h2>

<ol>
  <li><strong>Install the plugin</strong> — Restart IntelliJ IDEA after installation.</li>
  <li><strong>Install the OpenSpec CLI</strong> — Run <code>npm install -g @fission-ai/openspec</code> in your terminal.</li>
  <li><strong>Open the OpenSpec tool window</strong> — Click the OpenSpec icon in the right sidebar, or go to <strong>View > Tool Windows > OpenSpec</strong>.</li>
  <li><strong>Initialize your project</strong> — Go to <strong>OpenSpec > Init</strong> in the menu bar. This creates the <code>openspec/</code> directory structure.</li>
  <li><strong>Configure settings</strong> — Navigate to <strong>Settings > Tools > OpenSpec</strong> to set your CLI path, schema profile, and delivery method.</li>
  <li><strong>Propose your first change</strong> — Go to <strong>OpenSpec > Propose...</strong> and enter a name for your change (e.g., <code>add-user-auth</code>).</li>
</ol>

<h2>How It Works</h2>

<p>OpenSpec follows a <strong>spec-driven workflow</strong>: propose > design > specify > implement > archive.</p>

<ol>
  <li><strong>Propose</strong> — Describe what you want to build and why.</li>
  <li><strong>Design</strong> — Document technical decisions and architecture.</li>
  <li><strong>Specify</strong> — Write formal requirements with Given-When-Then scenarios.</li>
  <li><strong>Implement</strong> — Work through generated tasks with AI assistance.</li>
  <li><strong>Archive</strong> — Merge delta specs into your main specs and archive the change.</li>
</ol>

<p>The plugin works with any AI tool — GitHub Copilot, Claude Code, Cursor, or direct API calls to Claude, OpenAI, and Gemini.</p>

<h2>Delivery Modes</h2>

<ul>
  <li><strong>Clipboard</strong> — Copy prompts and paste into your AI tool's chat (Copilot, Cursor, etc.)</li>
  <li><strong>Editor Tab</strong> — Open prompts in a temporary editor tab for review</li>
  <li><strong>Direct API</strong> — Send prompts directly to Claude, OpenAI, or Gemini and write responses automatically</li>
</ul>

<p>For a complete walkthrough, see the <a href="https://github.com/fission-ai/openspec">OpenSpec documentation</a>.</p>
```

---

## Other Fields

| Field | Value |
|-------|-------|
| **Documentation URL** | `https://github.com/fission-ai/openspec` |
| **Bugtracker** | `https://github.com/fission-ai/openspec/issues` |
| **Forum** | *(leave empty for now)* |
| **Privacy Policy** | *(leave empty — plugin doesn't collect data)* |
| **Copyright** | `Copyright 2026 John Boyce` |

---

## Plugin Features

Comma-separated tags for the "Plugin Features" field:

```
Spec-Driven Development, OpenSpec, AI Integration, Code Generation, Requirements Management, Specifications, Workflow Automation, Claude API, OpenAI API, Gemini API, GitHub Copilot, Coverage Analysis
```

---

## Media / Screenshots

Capture 3-5 screenshots for the listing:

1. **Browse tab** — Tree view showing specs, changes, and archive nodes with an expanded change
2. **Workflow Action Panel** — Pipeline chips (proposal > design > specs > tasks) showing progress on an active change
3. **Coverage panel** — Coverage tab showing which requirements are referenced in code
4. **Settings panel** — Settings > Tools > OpenSpec configuration with the delivery dropdown visible
5. **Gutter markers** — A Java file with `@spec` annotations showing clickable gutter icons

**Tips:**
- Use the default IntelliJ light theme (most recognizable on the marketplace)
- Crop to the relevant panel area, not the full IDE window
- Use a sample project that looks realistic
- Recommended size: 1280x800 or similar 16:10 ratio

---

## Contacts & Resources

| Field | Value |
|-------|-------|
| **Vendor URL** | `https://openspec.johnnyblabs.com` |
| **Email** | Personal email for now (update to `openspec@johnnyblabs.com` once email forwarding is set up) |

---

## Monetization

Free — no monetization settings needed.
