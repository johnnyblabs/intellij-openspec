# Getting Started: Spec Browser

*For reviewers, team leads, and PMs who want to browse specs and track coverage — no AI setup required.*

---

## Prerequisites

| Requirement | Notes |
|-------------|-------|
| **IntelliJ IDEA 2024.2+** | Community or Ultimate edition |
| **OpenSpec plugin** | Install from [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/30678-openspec) |

That's it — no CLI, no API keys, no AI tools needed.

---

## Step 1: Open the Tool Window

After installing the plugin and restarting IntelliJ, open the OpenSpec tool window:

- Click the **OpenSpec** icon in the right sidebar, or
- Go to **View > Tool Windows > OpenSpec**

If the project already has an `openspec/` directory, the tree populates automatically with **Specs**, **Changes**, and **Archive** nodes.

## Step 2: Browse Specs

Expand the **Specs** node to see all capabilities and their requirements:

```
Specs
├── user-auth
│   ├── Requirement: session-management
│   └── Requirement: password-policy
└── data-export
    └── Requirement: csv-format
```

**Double-click** any item to open its spec file. **Right-click** for context menu actions.

## Step 3: Track Changes

The **Changes** node shows active work. Each change displays its artifact pipeline status:

```
Changes
└── add-greeting [proposed]
    ├── ✓ proposal       ← completed
    ├── ○ design         ← ready for generation
    ├── − specs          ← waiting on dependencies
    └── − tasks
```

This gives you visibility into where each change stands without asking the developer.

## Step 4: Check Coverage

Switch to the **Coverage** tab in the tool window. This shows which spec requirements are referenced in source code and highlights gaps — requirements that exist in specs but have no corresponding code references.

## Step 5: Navigate via Gutter Markers

When reviewing Java source files, look for gutter icons next to `@spec` annotations. Click them to jump directly to the referenced spec file. This lets you trace from implementation back to the requirement it satisfies.

## Step 6: Review with Inspections

The plugin provides real-time [editor inspections](feature-reference.md#inspections) as you read spec files:

- Spec format validation highlights structural issues
- Delta spec validation catches incomplete ADDED/MODIFIED/REMOVED sections
- Config validation flags problems in `openspec/config.yaml`

These inspections appear as yellow/red underlines in the editor, just like any other IntelliJ inspection.

---

## You Might Also Want to Explore

- **[IDE-First Developer Guide](getting-started-copilot.md)** — If you want to start proposing and generating changes yourself using Copilot, Cursor, or another IDE-based AI tool
- **[CLI Companion Guide](getting-started-cli-companion.md)** — If you use Claude Code or another terminal-based AI alongside IntelliJ
- **[Feature Reference](feature-reference.md)** — Complete reference for all plugin features
