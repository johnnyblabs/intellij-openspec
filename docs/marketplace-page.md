# JetBrains Marketplace Page Content

Reference for filling in the [plugin edit page](https://plugins.jetbrains.com/plugin/30678-openspec/edit).

> **Maintenance: Reference** — stable; updated only when the Marketplace listing copy changes (see the [documentation index](README.md)).

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
      navigate specs, and review with real-time inspections.</td>
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
      is your spec dashboard &mdash; browse specs, copy
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
  <li><strong>Spec &amp; change navigator</strong> &mdash; A Changes-focused tree shows your
    changes with per-artifact status; specs, archived changes, and <code>config.yaml</code>
    browse in the standard Project View and open in the editor.</li>
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
  <li>Changes-focused tree view of your changes with per-artifact status; specs, archived changes, and <code>config.yaml</code> browse in the standard Project View</li>
  <li>Master/detail Browse tab &mdash; a read-only, theme-aware rendered-markdown preview pane beside the tree, dedicated to change surfaces (proposal / design / tasks and consolidated deltas), so you can read a change&rsquo;s artifacts without leaving the tool window (single-click to preview, double-click to open the file); main specs open in the editor with the platform&rsquo;s Markdown preview</li>
  <li>Consolidated change-deltas view &mdash; select a change to see everything it modifies at the spec level, grouped by capability and operation (ADDED / MODIFIED / REMOVED / RENAMED) with delta badges, assembled from the OpenSpec CLI</li>
  <li>Artifact-status badges overlaid on tree node icons (done / ready / blocked / not-created), with change nodes showing an X/Y task-progress count</li>
  <li>Spec content search via Search Everywhere &mdash; type a term that appears only inside a requirement body or scenario and it surfaces that requirement, opening its spec in the editor at that requirement (runs off the UI thread over local files, no index, works during indexing)</li>
  <li>Validate from the Project View context menu, scoped to the clicked change or spec</li>
  <li>Grouped, navigable validation console &mdash; results grouped by file with clickable file:line links and per-severity coloring</li>
  <li>Visual artifact pipeline with status chips (done / ready / blocked)</li>
  <li>Fast-Forward: one-click change creation + artifact generation</li>
  <li>Generate All: walk the full artifact DAG with progress reporting</li>
  <li>Direct API support for Claude, OpenAI, and Gemini with secure credential storage; the model list is discovered live from your account (a free, no-token request that also validates the key), with a built-in fallback when offline</li>
  <li>Pick your AI model at generation time &mdash; an editable model dropdown in the tool-window header, shown when Direct API is the active delivery mode and populated from your account&rsquo;s live models, so you can switch models without opening Settings</li>
  <li>Detects AI tool configuration in your project and provides tool-specific delivery guidance</li>
  <li>Real-time editor inspections for spec format, RFC 2119 keywords, and delta spec structure</li>
  <li>Delta spec sync: merge ADDED / MODIFIED / REMOVED sections into main specs with preview</li>
  <li>Bulk archive with conflict detection for multi-change projects</li>
  <li>Setup wizard for first-run onboarding</li>
  <li>Explore panel: assembled project context for AI conversations</li>
  <li>Custom schema management: fork, create, and switch workflow schemas</li>
  <li>Config profile display and workflow management</li>
  <li>Coordination tab: on an OpenSpec 1.4.x CLI, view and create/manage workspaces, context stores, and initiatives (these in-IDE create/set-up actions retire automatically when you upgrade to a 1.5 CLI); on a 1.5.x or newer CLI, browse the newer stores and worksets model</li>
  <li>Verify action: a single pre-archive check with three states &mdash; ready to archive, in progress (valid but unfinished, bypassable), or blocked (a validation failure, the only hard gate)</li>
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
Spec-Driven Development, OpenSpec, AI Integration, Code Generation, Requirements Management, Specifications, Workflow Automation, Claude API, OpenAI API, Gemini API, GitHub Copilot
```

---

## Media / Screenshots

The tour shots live in [`docs/screenshots/`](screenshots/) — regenerate them with `./gradlew screenshotTour`. Recommended 4-5 for the listing (lead with the viewing/intelligence visuals):

1. **Spec + change overview** (`01-spec-browser`) — a spec open in the editor with Markdown preview, beside the Changes-focused Browse tree and its consolidated change-deltas
2. **Change deltas** (`08-change-deltas`) — the consolidated deltas view, grouped by capability/operation with badges
3. **Tree badges** (`09-tree-badges`) — a change expanded showing artifact-status badge overlays (done / ready / blocked, X/Y progress)
4. **Validation console** (`10-validation-console`) — grouped-by-file results with file:line links and per-severity coloring
5. **Workflow / pipeline** (`02-change-workflow`) — pipeline chips showing progress on an active change

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
