# Tasks

## 1. Decision

- [x] 1.1 Add `AiToolRegistry.builtInAdd(tool, project)` (`CREATE_DIR`, `CREATE_DIR_AND_LIST`, `NEEDS_CLI`) using `DetectionRules.fromDisk`.
- [x] 1.2 Add `BuiltInAddContractTest` over every captured detection layout, as design Decision 2 says, and run the mutation check.

## 2. Dialog

- [x] 2.1 Make `ManageAiToolsDialog.addToolBuiltIn` follow the decision: for `NEEDS_CLI`, write nothing and show a notice that the OpenSpec CLI is needed to set the tool up in this project, with the existing install hint; never write the list unless the decision says so.
- [x] 2.2 Make the Add button's tooltip say so for a `NEEDS_CLI` tool.

## 3. Verification and docs

- [x] 3.1 Run `./gradlew build` and `openspec validate --all --strict`, and run each new plain test class on its own.
- [x] 3.2 Add a CHANGELOG `## Unreleased` entry for users, and a line in `docs/feature-reference.md`'s Manage AI Tools section.
