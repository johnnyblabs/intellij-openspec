## Context

**How the plugin builds the prompt today.** `ExplorePromptService.buildPrompt(topic)` joins `loadSkillInstructions()`, `ExploreContextService.assembleContext()` and the topic. `loadSkillInstructions()` tries four fixed paths, then a built-in `DEFAULT_EXPLORE_PROMPT`, and logs which at INFO.

**The registry.** `AiToolRegistry` holds every upstream tool:
- its id, `cliId`, `skillsDir`, `globalSkillsDir`, detection paths and type;
- the upstream registry order, used for ties on a shared root;
- `builtInDir`/`builtInMarker` for the shared `.agents` root.

**Which tools apply.** Settings has a preferred tool, `OpenSpecSettings.getPreferredTool()`, and `AiToolDetectionService` reports the detected tools.

**What upstream 1.15.0 generates for Explore.** This was checked by openspec-guru with `openspec init --tools <id>` for all 50 ids under each delivery mode. The templates are byte-identical in 1.14.3.
- **Skill:**
  - **Path:** `<skillsDir>/skills/openspec-explore/SKILL.md`.
  - **When:** written unless `delivery: commands`. Codex and warp always get it.
  - **MiniMax:** writes it under `~/.minimax` (its global dir), not the project.
- **Command:**
  - **When:** written when `delivery` isn't `skills` and the tool has a command adapter. 34 tools have one.
  - **Paths:** irregular. They use five roots outside the skills dir, `prompts` vs `commands`, `opsx/<id>` vs `opsx-<id>`, and the extensions `.md`, `.prompt.md`, `.prompt` and `.toml`.
  - **Body:** differs from the skill's. It adds an Input block and drops the entry-points section.
  - **TOML:** Gemini and EasyCode wrap the body in `prompt = """…"""`.
- **The body is tailored when it's generated:**
  - the handoff spellings change per tool, in seven forms;
  - the delivery mode changes them too: under `skills`, Claude's skill says `/openspec-propose`;
  - so does the installed workflow set: an explore-only profile rewrites five handoffs into "I can capture this as a change".
- **Shared `.agents` root:** amp, antigravity, codex, gsd, zed and agents share `.agents/skills/openspec-explore/SKILL.md`, and `.agents/skills/.openspec-target` names the tool that rendered it.

## Goals / Non-Goals

**Goals:**
- Use the generated explore file for the user's tool whenever one exists, skill or command.
- Make the source visible, especially when it's the built-in default.
- Stop a tool-less Direct API model from acting on tool instructions.

**Non-Goals:**
- Context sources: nested specs and status-driven artifacts. Tracked separately.
- Store-declared roots. Tracked separately.
- MiniMax's global skill under `~/.minimax`. It isn't project state, and reading the user's home is out of scope.
- Multi-turn Explore. Tracked separately.
- Regenerating or editing managed files.

## Decisions

1. **Explore paths live in the registry.**
   - **Data:** `AiToolRegistry` gains `EXPLORE_COMMAND_PATHS`, a `Map<String, String>` from tool id to project-relative path, for the 34 tools with a command adapter, copied from the captured `delivery: commands` listing.
   - **Methods:**
     - `exploreSkillPath(tool)`: `<skillsDir>/skills/openspec-explore/SKILL.md`, or null with no `skillsDir`;
     - `exploreCommandPath(tool)`: the table entry or null.
   - **Why a table:** the command layout has no rule to derive it from, and a guessed rule would be a hand-written assumed shape.
   - **Drift protection:** a contract test checks the table against the captured listings for every tool and delivery mode, so a new CLI version that moves a path fails the build.
   - *Alternative:* a field on the `AiTool` record. Rejected, because it touches all 50 constructor calls for data that only Explore reads.
2. **The lookup order.**
   - **Candidates:** the preferred tool, if set and known, then every registry tool in registry order, de-duplicated.
   - **For each:** the skill path, then the command path. The first regular file wins. After the last candidate comes the built-in default.
   - **Why the whole registry and not the detected tools:**
     - A generated explore file creates the directory detection looks for, so a detected-tools group would only repeat the registry order.
     - Detection can't see a commands-only tool: under `delivery: commands`, Cline writes only `.clinerules/` and Kilo Code only `.kilo/`, neither of them a detection path.
     - Only existing files count, so the lookup costs about a hundred `stat`s and needs no detection pass.
   - **The old fixed paths are gone:** Claude's skill and command, and Augment's and Copilot's commands. Each is some tool's registry path, so the registry covers them.
   - **The shared `.agents` file:** it's the same file whichever sharing tool reads it, so reading it is faithful. Its body carries the owner's handoffs, which is what upstream wrote.
   - The result is a `Prompt(String instructions, @Nullable String sourcePath)`, where null means the built-in default.
3. **Extracting the body.**
   - **Markdown:** keep today's leading-`---` frontmatter strip.
   - **`.toml`:** take the text between the first `prompt = """` and the next `"""`, then strip it. If the file has no prompt block, fall through to the next candidate.
   - **Testing:** both are tested on real captured files.
4. **The source is shown to the user.**
   - **The panel:** `ExplorePanel.showResult`, `showError` and `showNotice` take the `Prompt` source. The panel renders one muted line under the topic header: "Instructions: `.cursor/commands/opsx-explore.md`" or "Instructions: built-in default".
   - **The fallback:** when the source is the built-in default, the line adds "No generated explore instructions were found. **Update OpenSpec…** generates them." Clipboard and editor-tab delivery add the same sentence to the balloon.
   - **Why:** item 2 of the tracker entry asked for the fallback to be visible, not just logged.
5. **The Direct API preamble.**
   - **Where:** `ExplorePromptService.buildPrompt(topic, Delivery.DIRECT_API)` puts `DIRECT_API_PREAMBLE` first.
   - **What it says:** it's a plugin-authored constant, clearly labelled as coming from the IDE plugin:
     - "you are answering inside an IDE plugin over an API, with no tools";
     - "you cannot run commands, read files or write anything";
     - "the project context below replaces `openspec list`/`status`/`show`";
     - "if the user wants to capture a change, tell them to use the plugin's Propose action";
     - "don't claim to have run or changed anything".
   - **Other deliveries:** `buildPrompt(topic)` keeps the agent form, so clipboard and editor tab are byte-identical to today apart from the lookup.
   - **Why a preamble:** it is the only on-model place for this. The managed file must never be edited.
6. **The small items.**
   - **Profile check:** `ExplorePanel.submitTopic` checks `WorkflowProfileService.isWorkflowEnabled("explore")`. When explore is off it shows a notice in the panel ("Explore isn't in your workflow profile. Change it in Settings → Tools → OpenSpec.") and sends nothing.
   - **Editor tab:** delivery opens `new LightVirtualFile("openspec-explore-prompt.md", prompt)` through `OpenFileDescriptor.navigate` (the plugin's hygiene test forbids `FileEditorManager.openFile`). There's no temp-dir write, so nothing collides across projects and nothing is left on disk.
   - **Wording:** the `plugin.xml` description changes, and the `explore-context` spec's version line is fixed.

## Risks / Trade-offs

- **The command table can go stale on a CLI bump.** → The contract test fails when it's re-captured for the new CLI, which is the existing CLI-bump playbook step. A missing entry only means the lookup falls through to the next candidate or the default, which is now visible.
- **The preferred tool may have no explore file** (for example a global-only tool). → The lookup moves on to the detected tools. The source line shows what was used.
- **The preamble changes Direct API answers.** → That's intended. It is short, and it's covered by a test that asserts its position and the absence of tool claims in its text, not by model output.
- **More files read per Explore:** at most two stats per candidate tool, with only a handful of candidates. → Negligible, and done off the EDT where the prompt is already built.
