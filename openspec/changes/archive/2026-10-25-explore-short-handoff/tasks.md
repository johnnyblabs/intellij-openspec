# Tasks

## 1. Fixture

- [x] 1.1 Add `scripts/capture-explore-invocations.py` (design D4). It loads the installed CLI package's dist modules by absolute path with `node`. For every tool with a skills dir and each delivery (`both`, `skills`, `commands`), it records the capability, whether commands and skills are generated, the explore invocation (upstream's `startHintLines` rules applied to `/opsx:explore`), and the explore command and skill paths. It writes `src/test/resources/fixtures/cli/1.15.0/explore/invocations.json` and adds the recipe to the explore fixtures README. Verify: 147 rows, including `/opsx:explore` (claude/both), `/opsx-explore` (cursor/commands), `@opsx-explore` (amazon-q/commands), `$openspec-explore` (codex/commands), `/skill:openspec-explore` (kimi/skills), `/openspec-explore` (devin/both) and "the openspec-explore skill" (rovodev/skills).

## 2. The rule

- [x] 2.1 Extend `ExplorePromptService.Instructions` with the tool and its `hasCommand` and `hasSkill` flags, set by the lookup (design D1). Verify: on a project with both of Claude's explore files, the lookup reports Claude, the skill as source, and both flags true. On a project with none, it reports no tool.
- [x] 2.2 Add `ExploreInvocation` (design D2) and the handoff text, including the plain-words form and the no-topic case. Verify with `ExploreInvocationContractTest` over every row of `invocations.json`: seed that row's files, make the tool preferred, and assert the plugin's invocation equals the row's (or no handoff when the row has no files). Also check the row's paths against `AiToolRegistry`.

## 3. Delivery

- [x] 3.1 Make the agent `build` return the short handoff when one applies, without assembling context, and add `Prompt.handoff` (design D3). Clipboard, editor-tab and no-provider delivery use it, and the notice quotes it and names the tool. Verify with delivery tests:
  - with Cursor's command installed, the clipboard holds `/opsx-explore <topic>` and the notice says "for Cursor";
  - with nothing installed, the clipboard holds the expanded prompt, as before;
  - the editor tab shows the short text;
  - Direct API still sends the expanded prompt with its preamble.
- [x] 3.2 Update the existing Explore delivery tests that expected the expanded prompt with an installed tool file, so they assert the short handoff instead. Verify: `./gradlew test --tests '*Explore*'` passes.

## 4. Docs and verification

- [x] 4.1 Update the feature reference's Explore row and add a CHANGELOG `### Changed` line. Verify: `DocumentationHygieneTest` passes.
- [x] 4.2 Mutation-check `ExploreInvocation`. Each of these must fail the contract test, then be reverted:
  - drop the Codex rule;
  - drop the Devin exception;
  - use `:` for flat commands;
  - drop the Amazon Q prefix;
  - drop the plain-words set.
- [x] 4.3 Run `./gradlew build`. Verify: it passes, including the coverage floor. Run `verifyPlugin` only if `com.intellij.*` references are added in `src/main`.
