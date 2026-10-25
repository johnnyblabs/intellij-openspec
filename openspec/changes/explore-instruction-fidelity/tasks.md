## 1. Fixtures

- [x] 1.1 Add `scripts/capture-explore-fixtures.py`. It runs `openspec init --tools <id>` for every registry tool under `delivery` both, skills and commands, with isolated `HOME`/`XDG_*` and telemetry off. It writes each tool's explore files as `<id> <relative path>` listings to `src/test/resources/fixtures/cli/1.15.0/explore/listing-<delivery>.txt`. It also copies these real files, with paths sanitized:
  - the Claude skill under both and skills;
  - the Claude skill for an explore-only custom profile;
  - the Claude, Cursor and Gemini (TOML) commands;
  - the shared `.agents` skill and its `.openspec-target`.

  Add a README with the recipe.

## 2. Registry

- [x] 2.1 Add `AiToolRegistry.exploreSkillPath(tool)`, `EXPLORE_COMMAND_PATHS` (34 entries) and `exploreCommandPath(tool)`. Contract-test them against the listings, for every tool:
  - the skill path appears under both and skills, except for the tools whose skill is global;
  - the command path appears exactly under both and commands;
  - no listed explore file is missing from the registry.

## 3. Prompt

- [x] 3.1 Change `ExplorePromptService` to return a `Prompt(instructions, sourcePath)`, with the lookup order from design decision 2. Verify on a temp project tree, through a seam for the candidate tools:
  - the preferred tool wins over one earlier in the registry;
  - a skill wins over its own command;
  - a command is used when there's no skill;
  - a commands-only Cline or Kilo Code file, which detection misses, is still found;
  - with nothing found, the source is null and the default is used.
- [x] 3.2 Add TOML `prompt` extraction, and keep the frontmatter strip. Verify on the captured Gemini TOML and Claude files: the bodies equal the expected text, and a TOML file without a prompt falls through.
- [x] 3.3 Add `DIRECT_API_PREAMBLE` and `buildPrompt(topic, delivery)`. Verify:
  - a Direct API prompt starts with the preamble, then instructions, context and topic;
  - clipboard and editor-tab prompts don't contain it;
  - the preamble names the Propose action and says that commands and writes aren't possible.

## 4. Surfaces

- [x] 4.1 Add the panel source line, and the fallback sentence in the panel and in the clipboard and editor-tab balloons. Verify with light platform tests through the real Send path:
  - the line names the file;
  - the built-in default shows the Update OpenSpec sentence;
  - clipboard delivery's balloon carries it too.
- [x] 4.2 Add the profile check on the panel's Send. Verify that with explore disabled there's no request, a notice is shown, and the input is enabled.
- [x] 4.3 Make editor-tab delivery open a `LightVirtualFile`, and update the `plugin.xml` description. Verify that the opened file's content is the prompt and that nothing is written to the temp directory.
- [x] 4.4 Mutation-check by hand. Each must make a test fail:
  - drop the command fallback;
  - swap the skill/command order;
  - ignore the preferred tool;
  - skip the TOML extraction;
  - add the preamble to clipboard delivery;
  - drop the fallback sentence;
  - skip the profile check.

## 5. Docs and verify

- [x] 5.1 Docs:
  - `docs/feature-reference.md` Explore section: where the instructions come from, the built-in default notice, and the Direct API note;
  - a CHANGELOG `## Unreleased` Changed entry.
- [x] 5.2 `./gradlew build` and `./gradlew verifyPlugin` are green, and `openspec validate explore-instruction-fidelity --strict` is clean. Run the new panel tests headless as well (`JAVA_TOOL_OPTIONS=-Djava.awt.headless=true`).
- [x] 5.3 Skipped by maintainer decision; the platform tests drive the real Send, clipboard and editor-tab paths. Manual check in a sandbox IDE, in a project initialised with `--tools cursor` under `delivery: commands`:
  - clipboard Explore copies the Cursor command's body, and the balloon names it;
  - Direct API Explore shows the source line and an answer that doesn't claim to have run commands.

## Workflow follow-up

- Archive the change, then close the tracker entry.
