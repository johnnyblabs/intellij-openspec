# Explore fixtures (OpenSpec CLI 1.15.0)

These are captured with `scripts/capture-explore-fixtures.py`. For every tool in `../ai-tools.json` and every delivery mode (`both`, `skills`, `commands`), the script runs `openspec init --tools <id>` in a fresh project with an isolated HOME and XDG dirs.

- `listing-<delivery>.txt`: each explore file init wrote, one per line.
  - `<tool> P <path>` is a file in the project.
  - `<tool> H <path>` is a file in HOME. Only MiniMax writes one.
- `claude-skill-{both,skills,explore-only}.md`: Claude's generated explore skill under `delivery: both`, under `delivery: skills`, and for a custom profile with only the explore workflow. Their handoff wording differs.
- `claude-command.md`, `cursor-command.md`, `gemini-command.toml`: generated explore commands. Gemini's is TOML, with the body in `prompt = """…"""`.
- `agents-skill.md`, `agents-openspec-target.txt`: the shared `.agents` skill, here generated for codex, and the marker naming the tool that owns it.

Machine paths are replaced with `/fixture/home`. Re-capture when the CLI version changes, then fix whatever the contract tests report.

## `invocations.json`

What upstream tells the user to type to start Explore, for every tool with a skills dir and every delivery mode (49 × 3 = 147 rows). It's captured by `scripts/capture-explore-invocations.py`, which loads the installed CLI package's dist modules with `node` and applies `openspec init`'s "getting started" hint rules (`core/init.js` `startHintLines`) to `/opsx:explore`:
- `getTransformerForTool` when the tool gets commands;
- `getSkillReferenceTransformer` when it gets skills only.

Each row has `tool`, `delivery`, `capability`, `commands`, `skills`, `invocation` (null when nothing is generated), `commandPath` and `skillPath`. `ExploreInvocationContractTest` seeds each row's files and checks the plugin's short handoff against `invocation`. Re-capture with the other explore fixtures on every CLI bump.
