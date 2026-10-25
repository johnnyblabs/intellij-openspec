# Proposal

## Why

For clipboard and editor-tab delivery, Explore hands the user's agent about 350 lines: the generated explore instructions, the project context and the topic. It does that even when the agent already has OpenSpec's explore command or skill installed, which `openspec update` keeps current. The agent then runs a pasted copy instead of its own command, and the user pastes a wall of text where upstream's quickstart says to type `/opsx:explore <topic>`. Tracked by the linked tracker entry.

Upstream already defines exactly what a user types to start a workflow, per tool and per delivery mode. It's used in `openspec init`'s "getting started" hint:
- **A generated command:** `/opsx:explore` (namespaced) or `/opsx-explore` (flat), from the generated file's name, with the adapter's prefix: `@` for Amazon Q.
- **A skill only:** `/openspec-explore`. Kimi Code uses `/skill:openspec-explore`. Codex uses `$openspec-explore`, even when it has commands.
- **Devin with both:** `/openspec-explore`.
- **No slash invocation for skills:** gsd, Rovo Dev, and SourceCraft Code Assistant under skills-only delivery. The skill is named in plain words ("the openspec-explore skill").

## What Changes

- **A short handoff when the tool has explore installed.** When the instructions came from a tool's generated explore command or skill, clipboard and editor-tab delivery copy what the user would type: the tool's invocation and the topic, for example `/opsx:explore why caching` or `$openspec-explore why caching`. For the plain-words tools, it's `Use the openspec-explore skill: <topic>`.
- **The invocation follows upstream's rules,** decided by the tool and which of its explore files exist (command, skill or both). It's a small rule contract-tested against a matrix captured from upstream's own functions for every tool and delivery mode.
- **The expanded prompt stays the fallback.** It's used when no generated explore file exists and the built-in default is in use, exactly as today.
- **Direct API is unchanged.** The model has no tools, so it still gets the expanded prompt with its preamble.
- **The notice says what was copied.** For example: "Copied `/opsx-explore why caching` for Cursor. Paste it into Cursor's chat." The editor tab shows the same short text.
- **A stale spec line is corrected.** The editor-tab scenario still described writing a temporary file, which the plugin no longer does; the prompt opens in an unsaved tab.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `explore-context`:
  - adds **Short handoff to an installed explore command or skill**;
  - **Delivery mode routing**: clipboard and editor tab carry the short handoff when one applies, and the editor-tab scenario describes the unsaved tab.

## Impact

- **Code:**
  - `ExplorePromptService`: the instructions also report which tool they came from and which of its explore files exist;
  - a new pure `ExploreInvocation` rule;
  - `ExploreContextAction`'s clipboard, editor-tab and no-provider deliveries.
- **Tests:** a contract test over the captured `fixtures/cli/1.15.0/explore/invocations.json`. It covers every tool and delivery mode, captured by `scripts/capture-explore-invocations.py` from upstream's dist functions. For each row it seeds that row's explore files and checks the plugin's choice. Delivery tests cover the short handoff and the fallback.
- **Docs:** the feature reference's Explore row and the CHANGELOG.
- **Platform compatibility:** IntelliJ IDEA 2024.2 and later stays supported; no platform APIs change.
