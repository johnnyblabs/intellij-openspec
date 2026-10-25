# Design

## Context

- **The lookup today:** `ExplorePromptService` looks for each tool's generated explore files, the skill then the command, starting with the Settings-preferred tool and then every tool in registry order. It returns `Instructions(text, sourcePath)`, where the source path is null for the built-in default.
- **Delivery today:** `ExploreContextAction` copies or opens `promptService.build(topic, AGENT).text()`, which is instructions plus context plus topic, for clipboard, editor-tab and no-provider delivery.
- **Upstream's rule:** the command a user types for a tool and delivery is decided in `core/init.js:1006-1042` (`startHintLines`), not exported, from exported pieces:
  - `getTransformerForTool(id, delivery, resolveCommandSurfaceCapability(id), resolveCommandInvocation(id))` when the tool gets commands;
  - `getSkillReferenceTransformer(id)` when it gets skills only;
  - each applied to the canonical `/opsx:<id>` (`utils/command-references.js:75,132,178`; `core/command-surface.js:8-29`; `core/command-generation/invocation.js:40,50,64`).
- **The captured matrix (1.15.0):** 49 tools × 3 deliveries = 147 rows. Each row records the capability, whether commands and skills are generated, the explore invocation, and the explore command and skill paths.

## Goals / Non-Goals

**Goals:**

- Copy what upstream itself tells the user to type, so the agent runs its own, current explore.
- Keep the expanded prompt wherever no generated explore file exists.

**Non-Goals:**

- Direct API delivery: unchanged.
- Explore's conversation or context scoping: separate tracked work.

## Decisions

### D1. The lookup reports the tool and its installed explore files
- **New fields:** `Instructions` gains `@Nullable AiTool tool`, plus `boolean hasCommand, hasSkill`, meaning which of that tool's explore files exist. They're set when a candidate is found and null/false for the built-in default.
- **Why both flags:** knowing which file won the lookup isn't enough. With `delivery: both`, the skill is found first, but upstream advertises the command form.

### D2. `ExploreInvocation`: upstream's rule, keyed on what's installed
- **The function:** `ExploreInvocation.of(cliToolId, commandPath, hasCommand, hasSkill)` returns the text to type, or null. In order:
  1. nothing installed → null;
  2. `codex` with its skill → `$openspec-explore` (skills-invocable, in every delivery);
  3. a command, unless it's `devin` with its skill as well → the prefix (`@` for `amazon-q`, otherwise `/`), then `opsx`, then `-` if the command file's name starts with `opsx-` and `:` otherwise, then `explore`;
  4. a skill: `gsd`, `rovodev` and `codeassistant` → the plain-words form; `kimi` → `/skill:openspec-explore`; otherwise `/openspec-explore`.
- **Inference without delivery:** delivery is never read. The installed files are what the CLI wrote for it, so they imply it.
- **The handoff text:** an invocation plus the topic. For the plain-words form, it's "Use the openspec-explore skill: <topic>". With no topic, it's the invocation alone.
- *Alternative rejected:* parsing the invocation out of the generated files. Command files say "The argument after `<inv>`", but skills only carry an example line. The shared `.agents/skills/` file serves six tools and says `$openspec-explore (Codex) or /openspec-explore (other agents)`, so it can't name one tool's form, and the prose changes with upstream template edits.

### D3. Delivery
- **`build(topic, AGENT)` returns a short-handoff prompt** when `ExploreInvocation` yields one. It skips assembling the context, so there's no CLI call and the task finishes at once. Otherwise it returns the expanded prompt as today.
- **`Prompt` gains `@Nullable String handoff`,** set for the short form, so the notice can quote it. The clipboard notice reads "Copied `<handoff>` for <Tool>. Paste it into <Tool>'s chat."
- **The editor tab** opens the same text in its unsaved tab.
- **The no-provider fallback** copies the short handoff too. It's the same agent path.
- **`build(topic, DIRECT_API)` is unchanged.**

### D4. Contract test over the captured matrix
- **The capture:** `scripts/capture-explore-invocations.py` runs the guru-verified `node` snippet, importing upstream's dist modules by absolute path, and writes `fixtures/cli/<version>/explore/invocations.json`.
- **The test:** `ExploreInvocationContractTest` maps each row's tool to the plugin's registry by CLI id, then:
  - for a row with files, seeds exactly that row's explore files in a temp project, makes the tool preferred, and asserts the plugin's lookup plus `ExploreInvocation` yields the row's invocation (plain words for the three tools);
  - for a row with no files, asserts the built-in default and no handoff.
- **Paths:** the row's paths are also checked against `AiToolRegistry`'s explore paths, so registry drift fails here too.

## Risks / Trade-offs

- **Upstream changes its rules.** The matrix is re-captured on each CLI bump (it's part of the explore fixtures' recipe), so a changed row fails the contract test.
- **A user who wanted the full prompt** (an agent without the command loaded) now gets one line. Mitigation: the expanded prompt is still what you get whenever the tool's explore files are absent, and the notice names the command, so a missing one is obvious.
- **The short handoff carries no project context.** That's the point: the agent's own explore gathers it with its own tools, as upstream intends.
