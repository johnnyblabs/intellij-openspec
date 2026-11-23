# Tasks

## 1. Capture plumbing

- [x] 1.1 Add shared capture helpers to the scripts: an isolated env with `OPEN_SPEC_INTERACTIVE=0` and stdin closed, a Node version check (22.20+ for 2.1.x), a Git and network check for 2.1.0, and path sanitizing to `/fixture/demo-project` and `/fixture/home`. Verify: a dry run against 2.1.1 on a machine with older Node stops with the required version, and the sanitizer refuses a file that still holds the scratch prefix or the real HOME.
- [x] 1.2 Teach the layout recorder to walk with `lstat` and write a top-level `links` map (`project` and `home` sides), never descending into a link. Verify: recapturing the 1.17.0 detection cases produces byte-identical `layout.json` files (1.x has no links), and a 2.1.1 `init --tools claude --scope project` layout lists each `.claude/skills/openspec-*` folder as a link into `.agents/skills/`.

## 2. Test-side links support

- [x] 2.1 Extend the layout materializer to read `links` and write each one as `SYMLINK`, `COPY` or `LINK_TEXT`, skipping `SYMLINK` with `assumeTrue` where links can't be created. Add unit tests that materialize a two-link layout in each mode and assert the resulting tree, each failing if the mode writes the wrong thing.
- [x] 2.2 Add `FixtureNoSymlinkHygieneTest`, which walks the fixtures tree without following links and fails naming any link it finds. Verify by mutation: commit-free, create a temporary link under the fixtures tree and confirm the test fails with that path, then remove it.

## 3. 2.x captures

- [x] 3.1 Capture `ai-tools.json`, `init-help.txt`, `version.txt` and `config-list.json` for 2.0.0, 2.1.0 and 2.1.1. Verify: `AiToolRegistryContractTest` parses each file, and the 2.1.x registries list `--scope` in `init-help.txt` while 2.0.0's does not.
- [x] 3.2 Capture detection cases for each 2.x version: the 1.x cases that still apply, plus `scope-project`, `scope-default-global`, `own-files`, `mixed-1.17-and-2x` and `global-2.1-read-by-2.0`. Verify: every `detect/<case>/` has `layout.json` and `detected.json`, and the hygiene test passes.
- [x] 3.3 Capture `workflows.json` and `workflow-invocations.json` for each 2.x version, recording `/openspec-<skill>`, Codex's `$openspec-<skill>`, and per tool whether `/opsx` still exists. Verify: `WorkflowInvocationContractTest` parses all three.
- [x] 3.4 Capture `update/*.txt` and `update-dry-run/*.{json,exit}` for each 2.x version, including the `global-skills`, `removed-skills` and `left-alone-skills` note codes. If 2.1.0's `global-skills` failure path does not reproduce, record that in the README instead. Verify: `UpdatePlanContractTest` parses every capture.
- [x] 3.5 Capture the `.openspec-target` each 2.x version writes (2.0 `keep` lines, 2.1 aliases, 2.1.1 inference from `skills-lock.json`), recorded in the detection layouts, and the init errors: `amazon-q` on 2.x, `--scope` on 1.17.0 and 2.0.0. Sanitize `projects.json`. Verify: each error capture has a non-zero `.exit`, and no new file contains the scratch prefix or the real HOME.
- [x] 3.6 Capture the validation parity corpus output for each 2.x version and add 2.0.0, 2.1.0 and 2.1.1 to `ValidatorVerdictVersionStabilityTest.FLOOR`. Verify: the test passes and fails if a 2.x corpus file is removed.

- [x] 3.7 Capture `instructions/instructions-{design,specs}.json` for each 2.x version by the 1.15.0 recipe, add them to `RegenerationNotePromptTest`, and add `ArtifactInstructionsVersionStabilityTest`, which fails when a release from 1.17.0 up rewords an instruction. Verify: the script reproduces 1.17.0's committed pair byte for byte, and by mutation one changed word in a 2.x capture, or a missing 2.x capture, fails the test.

## 4. `--help` sweep

- [x] 4.1 Add a help-sweep capture that writes `<version>/help/<command>.txt` and `help/index.json` for the newest patch of every 1.x minor from 1.3 and for 2.0.0, 2.1.0 and 2.1.1, covering `init update validate status instructions archive schema store workset list show new`. Commands a version lacks are recorded with their exit code and `"file": null`. Add a contract test that every captured version has an index, and every index entry with a file points to a file that exists. Verify by mutation: deleting one help file fails the test.

## 5. Version-parameterized contract tests

- [x] 5.1 Make `AiToolRegistryContractTest`, `AiToolDetectionContractTest`, `WorkflowInvocationContractTest` and `UpdatePlanContractTest` run each case over every version folder that holds its fixture family, replacing the single target or hard-coded `1.14.3` read. Verify: the 1.x results are unchanged, and the test report shows each case per version.
- [x] 5.2 Add the strict `KNOWN_GAPS` list (version + case → the change that closes it) for the 2.x cases the plugin does not yet handle. Listed cases must still diverge. Verify by mutation: removing an entry fails its case, and adding an entry for a case that passes also fails.

## 6. Documentation and gates

- [x] 6.1 Add 2.0.0, 2.1.0 and 2.1.1 sections to `src/test/resources/fixtures/cli/README.md` (CLI `--version`, `dist.integrity`, capture date, script per family), document the `links` schema and the help-sweep index, and update `scripts/README.md` for the new capture options. Verify: `DocumentationHygieneTest` passes.
- [ ] 6.2 Run `./gradlew build`, `openspec validate --all --strict`, and the staged-file leak grep from `CLAUDE.md`. Verify: all three are clean, and CI is green on Linux, macOS and Windows (the hygiene and materializer tests matter most on Windows).

## Workflow follow-up

- Archive the change after merge, then close its trackers.
- The CLI version-compatibility matrix change builds its flag guard on the `--help` sweep.
