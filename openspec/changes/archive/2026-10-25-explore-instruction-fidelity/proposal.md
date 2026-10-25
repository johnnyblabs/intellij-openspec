# Proposal

## Why

Upstream Explore is only an agent prompt: OpenSpec generates an explore skill and/or command file for each tool, with no CLI command and no state. The plugin delivers that prompt but drifts from it. Tracked by the linked tracker entry.

- **It only finds Claude's skill.** The lookup checks four fixed paths. OpenSpec 1.15 writes the explore skill to `<tool dir>/skills/openspec-explore/SKILL.md` for each tool. With `delivery: commands`, it writes only a per-tool command file instead:
  - 34 tools have one, at irregular paths (`.clinerules/workflows/opsx-explore.md`, `.kilo/command/opsx-explore.md`, `.gemini/commands/opsx/explore.toml` and so on);
  - two of them are TOML.

  Users of Cursor, Junie, Codex, Gemini, Copilot skills and the rest get the built-in default without any sign of it.
- **The built-in default can't be right.**
  - **Stale:** it's a subset of the real instructions, missing store selection, the root check, capture with confirmation and ASCII-only diagrams.
  - **Tailored:** the generated file's next-step wording depends on the tool (seven spellings of "run propose"), the delivery mode and the installed workflows. An explore-only profile says "I can capture this as a change".

  Only the generated file is faithful, and the fallback is used without telling the user.
- **Direct API sends instructions a model can't follow.** The skill tells the agent to run `openspec list/status/show`, read files and capture artifacts. A Direct API model has no tools, so it may claim it ran commands or changed files.
- **Smaller drift:**
  - the Explore tab's Send ignores the workflow profile, though the menu action checks it;
  - the action's description still says "Copy project context to clipboard";
  - the spec says the context includes `version`, which the code deliberately leaves out;
  - editor-tab delivery writes one shared temp file for every project.

## What Changes

- **Find the generated instructions for the user's tools.** In order:
  1. the preferred tool from Settings;
  2. every tool, in registry order. Only files that exist count, so this also finds a commands-only Cline or Kilo Code file, which detection can't see.

  For each tool, the skill comes first (`<skillsDir>/skills/openspec-explore/SKILL.md`), then its explore command file from a new per-tool command-path table in `AiToolRegistry`. The built-in default comes last.
  - **Markdown files:** frontmatter is stripped.
  - **TOML command files:** the `prompt = """…"""` body is taken.
- **Say where the instructions came from.**
  - **The Explore tab:** shows the source on a muted line, naming the file or "built-in default".
  - **The built-in default:** the panel or the clipboard balloon says no generated explore instructions were found, and suggests **Update OpenSpec…** to generate them.
- **A Direct API preamble.** The plugin adds a short preamble before the instructions, only for Direct API. It never edits the managed file. The preamble says:
  - this conversation can't run commands, read files or write anything;
  - the project context below stands in for `openspec list`/`show`;
  - capturing a change means the plugin's **Propose** action.

  Clipboard and editor-tab prompts go to a real agent, so they stay as they are.
- **Small items:**
  - **Profile check:** the tab's Send checks `isWorkflowEnabled("explore")` like the action does. When explore is off it shows a notice in the panel.
  - **Wording:** the action description becomes "Explore an idea with your AI tool or Direct API".
  - **Spec:** the `version` line is fixed to match the code.
  - **Temp file:** editor-tab delivery opens a `LightVirtualFile` per request instead of writing to the temp directory.
- **Unchanged:**
  - context assembly, which is tracked separately: nested specs and status-driven artifacts;
  - roots that point at a store, also tracked separately;
  - the delivery modes themselves.
- **Off-model, never done:** the plugin never edits or regenerates the managed skill or command files. It never saves explore transcripts.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `explore-context`:
  - "Explore prompt assembly": the per-tool lookup order, TOML bodies, and the source and fallback notice;
  - "Direct API submit from panel": the preamble and the profile check;
  - "Context assembly": the config scenario no longer lists `version`.

## Impact

- **Code:**
  - `services/AiToolRegistry`: the explore command path per tool, with a lookup method;
  - `services/ExplorePromptService`: the lookup, TOML extraction, a `Prompt(text, source)` result, and the preamble;
  - `actions/ExploreContextAction`: the profile check, `LightVirtualFile` delivery, and the fallback balloon text;
  - `toolwindow/ExplorePanel`: the source line;
  - `plugin.xml`: the action description.
- **Fixtures:** under `src/test/resources/fixtures/cli/1.15.0/explore/`:
  - captured `openspec init --tools <id>` explore-file listings for all 50 tools, under `delivery` both, skills and commands;
  - real generated files: the Claude skill under both and skills, the explore-only profile skill, the Claude, Cursor and Gemini (TOML) commands, and a shared `.agents` skill.

  There is also a capture script.
- **Tests:**
  - contract tests that the registry's skill and command paths match the captured listings for every tool and delivery mode;
  - a lookup-order test;
  - frontmatter and TOML extraction on the real files;
  - a preamble test, Direct API only;
  - a source and fallback notice test;
  - profile gating on the panel.
- **Platform:** `LightVirtualFile` is long-standing public API. The plugin still supports IntelliJ IDEA 2024.2 and later. `verifyPlugin` runs because new `com.intellij.*` references are added.
- **Docs:**
  - `docs/feature-reference.md` Explore section: where the instructions come from, the fallback, and the Direct API note;
  - a CHANGELOG `## Unreleased` Changed entry.
