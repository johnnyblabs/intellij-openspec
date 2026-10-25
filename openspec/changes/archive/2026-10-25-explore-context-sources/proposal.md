# Proposal

## Why

The Explore context is meant to stand in for what an agent would learn from `openspec list`, `status` and `show`. It doesn't, because it reads only one directory level and assumes the spec-driven schema. Tracked by the linked tracker entry.

- **Nested capabilities are missing.**
  - **Main specs:** the context reads `openspec/specs/*/spec.md`, so a capability at `openspec/specs/identity/user-auth/spec.md` never appears.
  - **Delta specs:** it reads `openspec/changes/<name>/specs/*/spec.md`, so a delta for `identity/user-auth` is dropped too.

  OpenSpec reports nested ids in full (`openspec list --specs --json` lists `identity/user-auth`) and its propose instructions tell agents to preserve the full path. That makes nested capabilities ordinary, not an edge case.
- **Custom-schema artifacts are missing.** Change artifacts are hard-coded to spec-driven's `proposal.md`, `design.md` and `tasks.md`. A change on a project schema with other artifacts (for example a `research` artifact generating `research/**/*.md`) shows only the files that happen to share those names.
- **Nothing says anything is missing.** The model, and a Direct API model in particular, which can't look for itself, is told this is the project's state and reasons from an incomplete picture.

## What Changes

- **Change artifacts come from OpenSpec.** For each active change, the context takes its files from `openspec status --change <name> --json` (`artifactPaths.<artifact>.existingOutputPaths`), in the order the CLI lists the artifacts, and labels each file with its artifact id. A file under a glob artifact is labelled with its path inside the change, such as `specs/identity/user-auth/spec.md`, so nested delta specs and custom-schema files are included and identifiable.
- **Fallback without the CLI.** When the CLI isn't available, fails, rejects the change, or emits no `artifactPaths`, the context falls back to a recursive walk of the change directory for Markdown files. It skips dot-files and dot-directories such as `.openspec.yaml`.
- **Main specs are listed recursively.** The Specs section lists each capability by its full id: from `openspec list --specs --json` when the CLI answers, otherwise from a recursive walk for `spec.md` under `openspec/specs/`. Each capability's requirement summaries are read from its `spec.md` as today.
- **Prompt assembly moves off the UI thread.** The two CLI calls make context assembly a background job. Explore builds the prompt under a cancellable progress indicator for every delivery mode, then copies, opens the editor tab or sends the request on completion. The Explore tab's input keeps its existing guarantee of being re-enabled on every outcome.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `explore-context`: **Context assembly** now covers every artifact file a change has (any schema, nested paths), lists nested capabilities by full id, and names its sources and fallback. It also says assembly runs off the UI thread.

## Impact

- **Code:**
  - `ExploreContextService`: artifact and spec sources, the CLI seam and the fallback walks.
  - `ChangeArtifactDag`: gains the `artifactPaths` block from `status --json`.
  - `ExploreContextAction` and `ExplorePromptService`: build the prompt in a background task before delivery.
- **Tests:** contract tests against captured 1.15.0 `list --specs --json` and `status --json` output from a project with a nested capability, a nested delta and a custom-schema change (`src/test/resources/fixtures/cli/1.15.0/explore-context/`, re-capturable with `scripts/capture-explore-context-fixtures.py`), plus fallback-walk tests.
- **CLI support:** no new floor. An older CLI without `artifactPaths` or `list --specs --json` uses the fallback walks.
- **Platform compatibility:** IntelliJ IDEA 2024.2 and later stays supported; the background-task APIs used are available on 2024.2.
- **Out of scope:** projects whose OpenSpec root is a store (tracked separately).
