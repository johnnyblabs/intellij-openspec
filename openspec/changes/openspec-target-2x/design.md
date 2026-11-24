# Design

## Context

`ManageAiToolsDialog.addToolBuiltIn` runs when no CLI is available. It creates `AiToolRegistry.builtInDir(tool)` and, when `builtInMarker(tool)` is non-null (a tool whose skills folder is the shared `.agents`), writes that marker with the tool's id if none exists. It never overwrites an existing list. The damage is therefore limited to a 2.x project that has no list file yet, or whose list was deleted.

## Decisions

### 1. One decision function, decided from disk

`AiToolRegistry.builtInAdd(AiTool, Path project)` returns one of:
- `CREATE_DIR`: create `builtInDir`;
- `CREATE_DIR_AND_LIST`: also write the single-id list (1.x only);
- `NEEDS_CLI`: write nothing.

A project counts as 2.x when `DetectionRules.fromDisk(project).is2x()`. That is the rule detection uses without a CLI, so the two can't disagree. The CLI version isn't consulted: the add runs only without a CLI.

The list matters only for a tool whose `builtInMarker` is non-null: today Codex and the universal tool, whose only home is `.agents/skills`. Amp, Antigravity, Zed and GSD resolve to folders of their own (`.amp`, `.agent`, `.zed`, `.gsd`), which 2.x detects. For a list-only tool:
- on a 2.x layout, the answer is `NEEDS_CLI`, because 2.x counts nothing under `.agents` as such a tool's evidence, so neither the folder nor the list would help;
- where a list exists and names another tool, also `NEEDS_CLI` (on any line). The plugin never overwrites the list, so the add could not record the tool;
- while another tool sharing `.agents` is detected (by the line `fromDisk` reads), also `NEEDS_CLI`: writing the list would hand the folder to the added tool and drop the other one. The captures show this on 1.x for an unmarked tree whose skills name Codex, and for Codex skills in `.codex`;
- otherwise (no list, or a list naming it) `CREATE_DIR_AND_LIST`, writing the list only if it's absent, as before.

Every other tool gets `CREATE_DIR`.

The decision is pure nio, so plain JUnit covers it. The dialog shows a notice for `NEEDS_CLI` and otherwise keeps its current write path.

*Alternative rejected:* on 2.x, append the tool to the existing list. The plugin would then write the CLI's own file, which is exactly the misstatement this change prevents. Upstream writes the list only through `init` and `update`.

### 2. Tests

- **`BuiltInAddContractTest`** replays every captured detection layout (1.x and 2.x) and checks two things for every registry tool. First, the decision never writes the list on a layout that `fromDisk` reads as 2.x. Second, applying the decision to a copy of the layout makes the tool detected by the line's rules, unless the decision is `NEEDS_CLI`.
- **No tool lost:** every tool detected before the add is still detected after it. One exception is allowed: a tool given a folder of its own may take over the shared folder by the CLI's own rule from those files (1.x `gsd`, whose list already names it).
- **Derived "list deleted" layouts:** each 2.x link layout with its list removed. Only a captured file is removed.
- **Captured outcomes** (`detect/builtin-add-*`, from the real CLI) back the decision:
  - Cursor's folder beside Claude is detected;
  - Codex's list on an empty project is detected;
  - on 2.x, Codex's folder alone is not, which is why the add refuses there.
- **The existing `whatBuiltInAddCreatesIsDetected`** stays as the 1.x and empty-project check.
- **Mutation:** making the decision ignore the 2.x layout must turn the contract test red.
