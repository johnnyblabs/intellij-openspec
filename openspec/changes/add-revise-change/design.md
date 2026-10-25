# Design

## Context

- **Upstream's `update` workflow** (core since CLI 1.6) is the `openspec-update-change` skill and the `/opsx:update` command.
  - **Input:** "Optionally specify a change name after `/opsx:update`" (the generated command's Input line).
  - **A specific request** in the user's message is "the starting edit".
  - **With no request** it runs a coherence review: it reads the artifacts and checks them against each other.
  - **Each revision** is written only after the user confirms it. It revises existing artifacts only.
- **Explore's handoff** (`ExploreInvocation`) already reproduces upstream's per-tool invocation rules (`openspec init`'s `startHintLines`) for `explore`. It's contract-tested against a matrix captured from upstream's dist for 49 tools × 3 deliveries.
- **The same functions give `update`** the same shape. Only the workflow id and skill name differ: `/opsx:update`, `/opsx-update`, `@opsx-update`, `/openspec-update-change`, `$openspec-update-change`, `/skill:openspec-update-change`, and "the openspec-update-change skill".
- **The command paths follow the explore ones:** `opsx/update.md`, `opsx-update.md`, `update.toml`.
- **`WorkflowParityContractTest`** lists `update` as NOT_SURFACED. It's the only core workflow without an action.

## Goals / Non-Goals

**Goals:**

- Hand the user's agent its own update workflow for a change, with an optional note, in exactly the form upstream tells users to type.
- Never deliver a plugin copy or re-implementation of the workflow.

**Non-Goals:**

- A Direct API revise. One completion can't run the skill's status, instructions and confirm-each-write loop.
- Store-rooted changes, and offering Revise from Regenerate's dialog. Both are tracked separately.
- Persisting notes.

## Decisions

### D1. Generalize the invocation rule to any workflow
- **The rule:** `ExploreInvocation` becomes `WorkflowInvocation.of(toolId, workflowId, skillName, commandPath, hasCommand, hasSkill)`, with the same rule:
  - Codex with the skill → `$<skillName>`;
  - a command, except Devin when the skill also exists → `<prefix>opsx<: or -><workflowId>`, with the `@` prefix for Amazon Q;
  - a skill → plain words for gsd, Rovo Dev and Code Assistant (`the <skillName> skill`), Kimi `/skill:<skillName>`, otherwise `/<skillName>`.
- **The handoff text:** `handoff(invocation, skillName, args)` adds the arguments, or "Use the <skillName> skill: <args>" for the plain-words form.
- **Explore** now calls it with `explore` and `openspec-explore`. Its behaviour is unchanged, and its contract rows stay green.

### D2. Paths for any workflow
- **Command path:** `AiToolRegistry.commandPath(tool, workflowId)` is the tool's explore command path with the `explore` segment replaced by the workflow id (`opsx/explore.md` → `opsx/update.md`, `opsx-explore.md` → `opsx-update.md`, `explore.toml` → `update.toml`).
- **Skill path:** `skillPath(tool, skillName)` is `<skillsDir>/skills/<skillName>/SKILL.md`.
- **Both are checked against the captured upstream paths for `update`,** so a tool whose naming differs fails the contract test.

### D3. One capture, two workflows
- **The capture:** `scripts/capture-explore-invocations.py` records rows for `explore` and `update`, each with a `workflow` field. The file is renamed `fixtures/cli/1.15.0/workflow-invocations.json`.
- **The test:** `ExploreInvocationContractTest` becomes `WorkflowInvocationContractTest` and checks both workflows' rows (294). For each row it seeds that row's files and asserts the plugin's invocation and paths.
- **The paths come from a real `init`:** the explore-fixtures capture also lists each update file `openspec init` wrote, per tool and delivery (`fixtures/cli/1.15.0/update/listing-<delivery>.txt`), and the contract test requires the matrix's update paths to equal them.

### D4. The action
- **`ReviseChangeAction`** (`OpenSpec.ReviseChange`) is an `OpenSpecBaseAction`, `DumbAware`, gated on `update`.
- **Which change:**
  - `CHANGE_NAME` from the data context (the tree, the panel header);
  - otherwise, from the main menu, the only active change, or a chooser when there are several.
  - It isn't offered for specs, delta specs or archived changes.
- **`ReviseChangeDialog(project, changeName)`:**
  - title "Revise Change: <name>";
  - an optional multi-line field labelled "What should change?", with the placeholder "Leave blank to review the whole change for coherence";
  - a note collapsed to one line.

### D5. Delivery
- **Find the files:** look up the delivery tool's update files the way Explore finds its files: the Settings-preferred tool, then the registry order, the first tool with either file.
- **Neither file:** nothing is delivered. A notification says "Revise Change needs your AI tool's update workflow. Run Update OpenSpec… (or add update to your profile), then retry", with an Update OpenSpec… action.
- **Found:** the handoff is `handoff(invocation, "openspec-update-change", "<change> <note>")`, delivered by the resolved mode:
  - **Clipboard:** copied, with a notice like Explore's ("Copied `/opsx:update add-auth …` for Claude Code. Paste it into Claude Code's chat.");
  - **Editor Tab:** an unsaved tab;
  - **Direct API:** clipboard, with "Revise runs in your AI agent, which reviews and confirms each write, so it's copied for you to paste instead of sent."
- **Nothing is persisted.**

### D6. Placement and names
- **Change right-click menu:** Explore This Change..., Revise Change..., a separator, then Apply.
- **Workflow panel change header:** an `AllIcons.Actions.Edit` icon button next to Apply, launched through `ActionLauncher` with the change in the data context. Its tooltip is "Revise Change... — have your AI agent revise this change's planning artifacts and check they still agree".
- **OpenSpec menu:** after Continue, before Apply.
- **Descriptions:**
  - Revise: "Revise a change's proposal, specs, design and tasks with your AI agent; it confirms each change";
  - **Update OpenSpec…** becomes "Refresh generated skills and commands from the installed CLI".

### D7. The parity gate enforces it
`update` is removed from `WorkflowParityContractTest.NOT_SURFACED`. Its stale-entry check would fail if the entry stayed, and its unmapped check would fail if the action were removed.

## Risks / Trade-offs

- **The note's position.** Upstream documents only the change name after the command. A note after it reads as the user's request, which is what the skill's steps expect. Revisit if upstream ever adds structured arguments.
- **Main-menu use with several active changes** needs a chooser. It reuses the platform list popup, and the action does nothing if the chooser is cancelled.
- **The renamed fixture and test** touch the Explore handoff's files. It's the same data plus `update` rows, and the explore rows' assertions are unchanged.
