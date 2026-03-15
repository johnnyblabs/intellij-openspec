## Context

The CLI supports 24 AI tools. Each tool has a directory (e.g., `.claude/`), skills (`skills/openspec-*/SKILL.md`), and commands (tool-specific format). The plugin currently only detects directories — it doesn't know if skills are actually installed.

`openspec init --tools <comma-list>` is idempotent — it creates/updates skills and commands for the specified tools without touching others. `openspec update` refreshes all existing tool files.

## Goals / Non-Goals

**Goals:**
- Let users add any AI tool at any time, not just during setup
- Show clear status: what's configured, detected, or available
- Delegate to CLI for skill generation when available
- Searchable for quick access to 24 tools

**Non-Goals:**
- Managing tool-specific settings (API keys, models) — that stays in Settings
- Removing tool directories entirely (too destructive)

## Decisions

### Three-tier tool status with visual indicators

Each tool has one of three states, determined by scanning the filesystem:
- **Configured** (green check) — tool directory exists AND contains `skills/openspec-*/SKILL.md`
- **Detected** (yellow warning) — tool directory exists but no OpenSpec skills
- **Available** (gray circle) — no directory

The detection uses `AiToolDetectionService.TOOL_DIRS` for directory paths and checks for the `skills/` subdirectory with OpenSpec skill files.

### JBList with search using SpeedSearchSupply

Use IntelliJ's `JBList` with built-in speed search (type to filter) rather than a custom search field. This is native IntelliJ UX — users already know to start typing in a list to filter. Add a `ListSpeedSearch` for the filter behavior.

Alternatively, use `SearchTextField` above the list for a persistent visible search bar, since 24 tools is enough to warrant always-visible filtering.

### Categorized list model with section headers

The list model groups tools into three sections with non-selectable header rows:
- "CONFIGURED" — tools with skills installed
- "DETECTED" — tool directories without skills
- "AVAILABLE" — remaining tools

Each tool row shows: status icon, tool name, type badge (CLI/IDE), and an action button.

### Action buttons per row using custom cell renderer

Each tool row renders an action button on the right:
- Configured → "Update" (runs `openspec update`)
- Detected → "Configure" (runs `openspec init --tools <id>`)
- Available → "Add" (runs `openspec init --tools <id>`)

Actions run via `CliRunner` on a background thread with progress indicator. If CLI unavailable, show a message suggesting CLI install for full tool setup.

### Dialog accessible from three places

1. **Settings panel** — "Manage AI Tools..." button in the AI/Delivery section
2. **OpenSpec menu** — "Manage AI Tools..." action after Setup Wizard
3. **Workflow panel** — link when no delivery method configured

### VFS refresh after tool changes

After any Add/Configure/Update action, synchronous VFS refresh ensures the new files appear in the project tree immediately.

## Risks / Trade-offs

- **[Low] CLI dependency for Add/Configure** — Without CLI, we can create the tool directory but can't generate skills. Show a clear message: "Install the OpenSpec CLI to generate skill files for this tool."
- **[Low] Stale state after external changes** — If someone runs `openspec init` in terminal, the dialog won't refresh automatically. Add a "Refresh" button in the dialog.
