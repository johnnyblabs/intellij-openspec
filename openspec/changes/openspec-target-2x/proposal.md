# Proposal

## Why

Without the OpenSpec CLI, Manage AI Tools can still "add" a tool: it creates the tool's folder and sets it as the preferred tool. For a tool it can only record through the shared folder (Codex and the universal tool; Amp, Antigravity, Zed and GSD get folders of their own instead), it also writes `.agents/skills/.openspec-target` holding that tool's id, as a 1.x release would.

On a project set up by CLI 2.x, that file is the CLI's list of every tool set up in the project. A single line written by the plugin would tell the CLI that this one tool is the project's only tool. On 2.x the folder alone doesn't detect such a tool either: 2.x counts nothing under `.agents` as that tool's own evidence. So on a 2.x project the add writes a misleading list and gets nothing in return.

The reading side of the list shipped with the skills-layout change. This change covers the one remaining writer. It is tracked in the linked tracker entry for the `.openspec-target` contract.

## What Changes

- **On a 2.x project, the built-in add never writes `.openspec-target`.** A project counts as 2.x by the same on-disk rule detection uses with no CLI: a list naming several tools or carrying an `own-files` mark, or a tool folder linking into `.agents/skills/`.
- **A tool only the list can record (Codex, the universal tool) can't be added without the CLI on a 2.x project, or where the list already names another tool.** Manage AI Tools explains that the CLI records the tool in the project's list, and points to installing it, instead of creating a folder that does nothing. (The plugin never overwrote an existing list, so in the second case the add already did nothing visible.) The same holds while another tool holds the shared folder on a 1.x project (a tree whose skills name Codex, or Codex skills in `.codex`): writing the list there handed the folder to the added tool and dropped the other, a defect from before 2.x that this change fixes too.
- **Tools with a folder of their own are added as before on any project.** Creating `.cursor/`, `.claude/`, `.amp/` or `.agent/` gets the tool detected by both 1.x and 2.x rules.
- **1.x projects behave as before**, including the single-id list for a shared-folder tool.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `plugin-core`: new requirement for adding a tool without the CLI, so the plugin never misstates the CLI 2.x tool list.

## Impact

- **Production code:** `AiToolRegistry` gains the decision (`builtInAdd(tool, project)`: create the folder, create it and write the list, or needs the CLI), reusing `DetectionRules.fromDisk`. `ManageAiToolsDialog.addToolBuiltIn` follows it.
- **Tests:** a test over the captured 1.x and 2.x detection layouts (no list write on any 2.x layout, unchanged on 1.x), and the existing "added without the CLI is then detected" test kept for 1.x and for own-folder tools on 2.x.
- **Compatibility:** no CLI-version gate; decided from disk. IntelliJ 2024.2+ is unaffected.
- **Out of scope:** what 2.0's `keep` lines mean for `update` (the update-migration change).
