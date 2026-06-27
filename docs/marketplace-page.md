# JetBrains Marketplace Page Content

Reference for filling in the [plugin edit page](https://plugins.jetbrains.com/plugin/30678-openspec/edit).

---

## Short Description (search results line)

For `build.gradle.kts` `description` field (max ~80 chars, shown in marketplace search):

```
IDE-native client for the OpenSpec spec-driven development framework by Fission AI
```

---

## Description (main marketplace page)

Paste this HTML into the "Description" field on the plugin edit page:

```html
<p>
  An IDE-native client for the <a href="https://github.com/fission-ai/openspec">OpenSpec</a>
  spec-driven development framework. Browse your specs, orchestrate the
  propose &rarr; generate &rarr; implement &rarr; archive lifecycle, and route
  AI-generated artifacts through the tool of your choice &mdash; all without
  leaving IntelliJ.
</p>

<h3>Four Ways to Use It</h3>

<table>
  <tr>
    <td><strong>Spec Browser</strong></td>
    <td>Just want to read specs? Zero AI setup required. Browse the tree,
      track coverage, navigate via gutter markers, and review with
      real-time inspections.</td>
  </tr>
  <tr>
    <td><strong>IDE-First Developer</strong></td>
    <td>Use Copilot, Cursor, Windsurf, or Cline? The plugin orchestrates
      the full workflow and routes prompts to your clipboard with
      tool-specific guidance on where to paste.</td>
  </tr>
  <tr>
    <td><strong>CLI Companion</strong></td>
    <td>Use Claude Code, Gemini CLI, or another terminal AI? The plugin
      is your spec dashboard &mdash; browse specs, track coverage, copy
      prompts with save-path hints for your CLI tool.</td>
  </tr>
  <tr>
    <td><strong>Standalone API User</strong></td>
    <td>Have an API key but no external AI tool? The plugin provides the
      complete workflow &mdash; Fast-Forward from idea to fully generated
      artifacts in one click via Direct API (Claude, OpenAI, or Gemini).</td>
  </tr>
</table>

<h3>What It Does</h3>

<ul>
  <li><strong>Spec viewer &amp; navigator</strong> &mdash; Tree view of specs, changes, and
    archives with search, filtering, gutter markers in your source code, and a
    coverage panel that tracks which requirements are referenced in code.</li>
  <li><strong>Workflow orchestrator</strong> &mdash; Walk through Init, Propose, Generate,
    Apply, and Archive from menus and toolbar buttons. A visual artifact
    pipeline shows what&rsquo;s done, what&rsquo;s ready, and what&rsquo;s blocked.
    Fast-Forward creates a change and generates all artifacts in one click.
    Continue advances one artifact at a time. Verify checks completeness.</li>
  <li><strong>AI bridge</strong> &mdash; Route generation prompts to your preferred AI tool:
    <em>Clipboard</em> (paste into Copilot, Cursor, Claude Code, etc.),
    <em>Editor Tab</em> (review before sending), or <em>Direct API</em>
    (call Claude, OpenAI, or Gemini and write the result automatically).</li>
  <li><strong>Works with or without the CLI</strong> &mdash; When the OpenSpec CLI is
    installed, the plugin delegates to it. When it isn&rsquo;t, built-in
    scaffolding handles project init and change creation so you can work
    entirely from the IDE.</li>
</ul>

<h3>Key Features</h3>

<ul>
  <li>Hierarchical tree view of specs, changes, and archives</li>
  <li>Visual artifact pipeline with status chips (done / ready / blocked)</li>
  <li>Fast-Forward: one-click change creation + artifact generation</li>
  <li>Generate All: walk the full artifact DAG with progress reporting</li>
  <li>Direct API support for Claude, OpenAI, and Gemini with secure credential storage</li>
  <li>Detects AI tool configuration in your project and provides tool-specific delivery guidance</li>
  <li>Real-time editor inspections for spec format, RFC 2119 keywords, and delta spec structure</li>
  <li>Gutter markers linking <code>@spec</code> annotations in code back to spec files</li>
  <li>Coverage panel showing which requirements are referenced in source</li>
  <li>Delta spec sync: merge ADDED / MODIFIED / REMOVED sections into main specs with preview</li>
  <li>Bulk archive with conflict detection for multi-change projects</li>
  <li>Setup wizard for first-run onboarding</li>
  <li>Explore panel: assembled project context for AI conversations</li>
  <li>Custom schema management: fork, create, and switch workflow schemas</li>
  <li>Config profile display and workflow management</li>
  <li>Verify action: check artifact completeness and requirement coverage</li>
  <li>Continue action: incremental one-artifact-at-a-time generation</li>
  <li>CLI update action: refresh agent instruction files from the IDE</li>
</ul>

<h3>What It Is Not</h3>

<ul>
  <li>Not an AI itself &mdash; it assembles prompts and routes them to your AI tool or API provider.</li>
  <li>Not a replacement for the OpenSpec CLI &mdash; it is a companion with built-in fallback for CLI-less workflows.</li>
  <li>Not a general-purpose spec tool &mdash; it is specifically for the
    <a href="https://github.com/fission-ai/openspec">OpenSpec</a> framework by Fission AI.</li>
</ul>
```

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
| **Bugtracker** | `https://github.com/johnnyblabs/intellij-openspec/issues` |
| **Forum** | *(leave empty for now)* |
| **Privacy Policy** | *(leave empty — plugin doesn't collect data)* |
| **License / EULA** | Apache License 2.0 |
| **Source Code URL** | `https://github.com/johnnyblabs/intellij-openspec` |
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
4. **Explore tab** — Assembled project context panel with copy/editor buttons
5. **Settings panel** — Settings > Tools > OpenSpec configuration with the delivery dropdown visible
6. **Gutter markers** — A source file with `@spec` annotations showing clickable gutter icons

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
