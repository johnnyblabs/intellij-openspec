# Design

## Context

Upstream's router is the `openspec` skill (`dist/core/templates/workflows/router.js`, from 2.2.0). It takes free-form intent: it answers a question, picks a workflow skill, or runs several in order, and asks when a request is ambiguous. It has no command template and no argument syntax.

Installation:
- every profile with at least one workflow gets it (`profiles.js` `withRouter`), machine-wide installs included;
- `--tools none` and an empty profile get none.

Per tool, `command-references.js` maps `router` to `openspec`, with a prefix of `/`, `$` for Codex or `/skill:` for Kimi, and plain words for gsd, rovodev and codeassistant. The plugin already has:
- `WorkflowInvocation.find` and `of` (the per-tool spelling from what's installed);
- `handoff(invocation, skill, text)`, which writes the invocation then the request, unquoted. Upstream's quotes appear only around the `"your idea"` placeholder in its `init` hint (`init.js:1117`); the router has no argument syntax: "type `/openspec`, say what you want". The plain-words form, "Use the openspec skill: …", differs from upstream's `ask <tool> to use the … skill with "…"`. That difference is shared by every handoff, and tracked with the invocation-wording parity entry, not here;
- `WorkflowHandoffDelivery` (editor tab, clipboard).

## Decisions

### D1. Spellings come from upstream

`scripts/capture-workflow-invocations.py` adds the router for releases that ship `router.js`. On 2.2.0 that's 87 rows, from upstream's own `getSkillReferenceTransformer`, and the existing rows are unchanged. `WorkflowInvocationContractTest` already replays every row against `WorkflowInvocation.of`, so the router's spelling for every tool is held to upstream's.

### D2. Availability

| State | When | How shown |
|---|---|---|
| Hidden | the project isn't an OpenSpec project, or the detected CLI is older than 2.2.0 | not in the menu or toolbar |
| Disabled | CLI 2.2.0+, but `WorkflowInvocation.find(project, "router", "openspec")` finds no tool with the skill | tooltip: "The OpenSpec router skill isn't installed for your AI tool. Run openspec update, or set a tool up in Manage AI Tools." |
| Enabled | otherwise | |

A pure `availability(cliVersion, routerFound)` decides it. `update()` reads only the cached CLI version and a cached `find` result, refreshed off the UI thread, so it never touches the disk. With no CLI detected, a found skill means the router exists, so the action is enabled.

### D3. The input

`AskAgentDialog` follows `ExploreTopicDialog`:
- a `JBTextField` with the placeholder "e.g. where does add-auth stand?  ·  verify add-auth, then archive it";
- empty: not prefilled with the selected change's name, which would be plugin-invented, and a bare name is a request the router can only ask about;
- OK disabled while the field is blank;
- the text is trimmed, and otherwise sent as typed.

### D4. Delivery

The handoff is `WorkflowInvocation.handoff(found.invocation(), "openspec", text)`, delivered through `WorkflowHandoffDelivery`:
- **Clipboard:** a notification saying what was copied and where to paste it.
- **Editor tab:** a light file.
- **Direct API:** clipboard, with a note that the router runs in the AI tool. No model is called: a skill needs an agent that runs the CLI and reads files.

### D5. Surfaces

- `OpenSpec.AskAgent`, right after `OpenSpec.Explore` in the OpenSpec menu, with icon `AllIcons.Actions.IntentionBulb`.
- The same action in `OpenSpec.ToolWindowToolbar`, beside Explore.
- Not in the per-change workflow panel, because the router isn't tied to one change.
- No shortcut.

## Risks

- **The skill check proves `SKILL.md`, not the router's `references/` folder.** That is acceptable: `openspec update` repairs reference files. The check doesn't claim the router is complete.

- **Upstream renames the router or changes its spelling.** The re-capture at the next CLI bump fails `WorkflowInvocationContractTest`, and the parity inventory's invocation row gets updated.
