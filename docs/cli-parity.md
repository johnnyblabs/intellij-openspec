# CLI Parity Inventory

What in the plugin re-implements the OpenSpec client, which upstream code it mirrors, where that code's behavior changes by release, and the captured runs that prove the plugin matches each side of each change.

> **Maintenance: Living** — a change that adds, alters or removes a port updates its row in the same pull request. A full sweep re-checks every row at each CLI bump and before each release (see [CONTRIBUTING](../CONTRIBUTING.md)). The feature-level view is the [OpenSpec Client Coverage](openspec-support.md) matrix; this page is the layer beneath it.

The rule behind it: **the CLI decides.** A port follows the detected release, cites the upstream file it mirrors, and has a capture on both sides of every release where that file's behavior changes. Plugin behavior with no upstream counterpart is either removed or recorded under [Deliberate differences](#deliberate-differences).

`InventoryContractTest` holds this page to the tree: every test class and fixture path it names exists, and every contract test appears here.

## Ports

Plugin code that re-implements upstream logic. "Release changes" lists each release where the upstream behavior changes; "Captures" names the fixtures on each side. *Swept* is the newest release the row was last checked against, at the last full sweep.

| Upstream | Plugin | Release changes | Captures | Contract test | Swept |
|---|---|---|---|---|---|
| `dist/core/available-tools.js` `getAvailableTools`, with `shared-skill-target.js` | `AiToolDetector`, `Detector2x`, `DetectionRules` | 1.4.0–1.14.0 tool additions; 1.7.0 Kimi and Devin folders; 1.8.0 home tools and shared-folder reconciliation; 1.11.0 own detection path beside a shared folder; 1.15.0 file-view seam (same behavior); 2.0.0, 2.1.0, 2.1.1 2.x lines | `fixtures/cli/<v>/detect/` on 1.3.1–2.2.0 | `AiToolDetectionContractTest` | 2.2.0 |
| `dist/core/shared/tool-detection.js` `getConfiguredTools` | `AiToolDetectionService` (set-up verdict) | 1.7.0 commands count; 1.8.0 shared-folder ownership; 1.11.0 adapter-backed tools' older-folder skills; 1.17.0 older-folder commands; 2.0.0 tool list | `fixtures/cli/<v>/detect/` `configured.json` on 1.3.1–2.2.0 | `ConfiguredToolsContractTest` | 2.2.0 |
| `SKILL_NAMES` (`tool-detection.js`; `config.js` `OPENSPEC_SKILL_NAMES` from 1.8.0) | `AiToolRegistry.skillNames` | 1.6.0 adds `openspec-update-change`; 2.2.0 adds `openspec` | `fixtures/cli/<v>/skill-names.json` on 1.3.0–2.2.0 | `SkillNamesContractTest` | 2.2.0 |
| `dist/core/command-surface.js` adapter-backed tools with older folders | `AiToolRegistry.countsLegacySkillsRegardlessOfOwner` | 1.11.0 Antigravity | `fixtures/cli/<v>/skill-names.json` on 1.10.0–2.2.0 | `SkillNamesContractTest` | 2.2.0 |
| `dist/core/config.js` `AI_TOOLS`, `TOOL_ID_ALIASES` | `AiToolRegistry`, `AiToolRegistry.asOf1x` | each `ai-tools.json` release; 1.7.0, 1.8.0, 1.11.0 tool shapes; 2.0.0 Amazon Q retired; 2.1.0 installer tools | `fixtures/cli/<v>/ai-tools.json` on 1.3.0–2.2.0 | `AiToolRegistryContractTest` | 2.2.0 |
| `dist/core/config.js` `agentsByProjectFolder` | `AiToolRegistry.folderGroup` | 2.1.0 | `fixtures/cli/<v>/ai-tools.json` `agentsByProjectFolder` | `AiToolRegistryContractTest` | 2.2.0 |
| `dist/core/init.js` `resolveSkillScope`; `global-skills.js` `supportsGlobalSkills` | `AiToolDetectionService.installsForMachine`, `SavedSkillsScope` | 2.1.0 machine scope | `fixtures/cli/<v>/machine-wide/` on 2.1.0–2.2.0 | `ConfiguredToolsContractTest` | 2.2.0 |
| `dist/core/global-skills.js` `projectHasOwnSkills` (:28), `readGlobalSkills` (:44) | `ProjectSkillsScope`, `HomeSkills` | 2.1.0 introduced; 2.2.0 router name | `fixtures/cli/<v>/skills-scope/`, `fixtures/cli/<v>/machine-wide/` on 2.1.0–2.2.0 | `ProjectSkillsScopeContractTest` | 2.2.0 |
| `dist/core/init.js` `detectAgents` (:622), the picker's preselection (:600–645) | `MachineToolMarkers`, `InstallToolTicks` | 2.1.0 introduced; unchanged to 2.2.0 | `fixtures/cli/<v>/skills-scope/` on 2.1.0–2.2.0 | `MachineToolMarkersContractTest` | 2.2.0 |
| `dist/core/shared/skill-paths.js` `resolveSkillLinkDir` with `SKILL_LINKED_TOOL_IDS` | `SkillLinkDirs` | 2.0.0 fixed list; 2.1.0 the installer's project folder; 2.2.0 none | `fixtures/cli/<v>/ai-tools.json` `effective` on 2.0.0–2.2.0 | `SkillLinkDirsContractTest` | 2.2.0 |
| `dist/core/shared/skill-paths.js` `resolveToolSkillsDir`, command adapters' `getFilePath` | `AiToolRegistry` explore paths | 1.8.0; 2.0.0 shared folder, commands retired; 2.1.0 home for global tools | `fixtures/cli/<v>/` explore listings on 1.15.0–2.2.0 | `ExplorePathsContractTest` | 2.2.0 |
| `dist/core/shared-skill-target.js` `readListFileTools`, `readListedToolsWithOwnFiles` | `SkillToolList` | 1.8.0 one id; 2.0.0 lines, first token, `own-files`, `keep`; 2.1.0 aliases | `fixtures/cli/<v>/detect/` | `OpenSpecTargetListContractTest` | 2.2.0 |
| `dist/core/shared/skill-links.js` `classifySkillLinkSpot` (:54) | `SkillSpot` | 2.0.0 introduced; 2.1.0 no behavior change | `fixtures/cli/<v>/skill-spots/` on 2.0.0–2.2.0 | `SkillFolderShapeTest` | 2.2.0 |
| `dist/core/shared/generated-fingerprint.js`, `released-generated-fingerprints.js` | `ReleasedSkillFingerprints` | 1.17.0 introduced; 2.1.0 refactor, same behavior | `fixtures/cli/<v>/fingerprints/` on 2.0.0–2.2.0 | `ReleasedSkillFingerprintsContractTest` | 2.2.0 |
| `dist/core/config.js` `AI_TOOLS.setupNote`, the `init.js` print filter | setup notes (`setupNoteAfter`) | 1.7.0 Hermes note; 2.0.0 Hermes text; 2.1.0 universal note and init filter | `fixtures/cli/<v>/ai-tools.json`; init output on 1.17.0, 2.1.0–2.2.0 | `SetupNotesContractTest` | 2.2.0 |
| `dist/core/specs-apply.js`, `parsers/requirement-blocks.js` | `SpecMerge` | many releases from 1.3.1; 1.11.0 retirement; 1.16.1 refuses a misplaced section, Purpose closing run; 2.1.1 front matter, raw-HTML interrupt, Purpose blank line | `fixtures/cli/archive-1.14.3/`, `archive-2.1.0/`, `archive-2.1.1/`, `archive-2.2.0/` | `ArchiveMergeContractTest`, `NestedCapabilitySyncContractTest` | 2.2.0 |
| `dist/core/archive.js` archive target and collision | `ChangeService.archiveTargetName`, `archiveConflict` | 2.1.1 checks the target before syncing | none | none | 2.2.0 |
| `dist/core/archive.js` `contentTheMergeCannotName` | `RetirementAudit` | 1.8.0 retirement; 1.13.0 and 2.1.1 check variants; 1.11.0 description | `fixtures/cli/<v>/retire/` on 1.7.0–2.2.0 | `RetirementContractTest` | 2.2.0 |
| `dist/utils/change-metadata.js` `readBooleanMarker`, `ChangeMetadataSchema` | `RetirementMarkers` | 1.8.0 `retire_capabilities`; 1.11.0 reason sanitizing | `fixtures/cli/<v>/retire/` `marker-*` | `RetirementContractTest` | 2.2.0 |
| `dist/core/archive.js` `retireSpec` | `SpecSyncService.deleteAndPrune`, `retiredWarning` | 1.8.0 | `fixtures/cli/<v>/retire/` | `RetirementApplyTest` | 2.2.0 |
| retirement fix text (`archive.js` hint) | `RetireMarkerFix` | 1.8.0 | `fixtures/cli/<v>/retire/` | `RetireMarkerFixContractTest` | 2.2.0 |
| `dist/core/validation/validator.js`, `constants.js`, `parsers/requirement-blocks.js`, `schemas/base.schema.js` | `BuiltInValidator` and its rule ports | 1.7.0 skip_specs; 1.11.0 placeholder Purpose; 1.13.0 `*`/`+` rename bullets; 1.13.1 orphan warning; 1.16.0 scenario steps; 1.16.1 unrecognized delta section is an error; 2.1.1 MODIFIED growth, delta front matter | `fixtures/cli/1.3.0/`, `fixtures/cli/1.13.2/`, `fixtures/cli/1.14.3/`, `fixtures/cli/1.16.0/`, `fixtures/cli/1.16.1/`, `fixtures/cli/2.1.1/parity-corpus-2x/` | `ValidatorRulePortsContractTest`, `BuiltInValidatorRulesTest`, `RenamedVerdictContractTest`, `Validate116ContractTest`, `Validate1161ContractTest`, `ClosingRunHeadingContractTest`, `UnrecognizedDeltaSectionContractTest`, `CliConfigToleranceContractTest` | 2.2.0 |
| `dist/utils/task-progress.js` `TASK_LINE_PATTERN`, `getTaskProgressForChange` | task lines | 1.6.0 tracked task files; 1.8.0 indented lines, `X`; 1.13.1 `+`, ordered, `[~]`, `[]`, link guard; 2.0.0 wrapped lines | `fixtures/cli/1.13.2/` | `TaskLinesContractTest` | 2.2.0 |
| `schemas/spec-driven/templates/*.md`, `schema.yaml` | scaffolding templates, `ScaffoldingDetectionService` | proposal 1.7.0, 1.8.0, 1.13.1, 1.14.0; spec and design 1.7.0, 1.13.1; tasks 1.13.1 | `fixtures/cli/1.12.0/` | `ScaffoldingContractTest`, `StatusPassThroughContractTest` | 2.2.0 |
| `dist/core/profiles.js` `CORE_WORKFLOWS`, `getProfileWorkflows` | core workflow set, `WorkflowProfileService` | 1.4.0 `sync`; 1.6.0 `update`; 1.10.0 a custom profile with archive gains `sync`; 2.2.0 router | `fixtures/cli/<v>/` `workflows.json` on 1.15.0–2.2.0; `fixtures/cli/1.15.0/config-list/` | `CoreWorkflowsContractTest` | 2.2.0 |
| `dist/core/profiles.js` `ALL_WORKFLOWS`; `core/templates/workflows/*.js` | workflow-gated actions | ids 1.6.0; templates most releases (archive: 2.1.1 checks the target before syncing) | `fixtures/cli/<v>/` `workflows.json` on 1.15.0–2.2.0 | `WorkflowParityContractTest` | 2.2.0 |
| `dist/utils/command-references.js`, `core/command-generation/invocation.js`, `init.js` start hints | `WorkflowInvocation`, `OpenSpecAskAgentAction` | 1.7.0 created; 1.8.0, 1.12.0, 1.14.0 tools; 2.0.0 delivery rules dropped; 2.2.0 router (`openspec` skill, captured for every tool) | `fixtures/cli/<v>/workflow-invocations.json` on 1.17.0–2.2.0 | `WorkflowInvocationContractTest`, `VerifyHandoffTest` | 2.2.0 |
| `dist/core/migration.js` (plan, `LEGACY_TOOL_ROOTS`), `core/update.js` | `UpdateMigration` | 2.0.0; 2.1.0 kept shared skills removed, link guard; 2.1.1; 2.2.0 `SKILL_WORKFLOWS` | `fixtures/cli/<v>/update-migration/` on 2.0.0–2.2.0 | `UpdateMigrationTest`, `UpdateMigrationAtRiskContractTest`, `MigrationNoticeTest` | 2.2.0 |
| `dist/core/root-selection.js` `resolveOpenSpecRoot`, `project-config.js` `readStorePointer` | `RootResolver` | 1.5.0 created; 1.7.0 `defaultStore`; 1.13.1 a store marker inside `specs/` or `changes/` isn't planning | `fixtures/cli/<v>/` store-root on 1.5.0–2.2.0 | `StoreRootContractTest` | 2.2.0 |
| `dist/cli/index.js`, `commands/` | `CliFlags`, `CliCommands` | 1.4.0, 1.5.0, 1.7.0–1.12.0, 1.14.0, 1.15.0, 2.1.0 | `fixtures/cli/<v>/help/` | `CliFlagTableContractTest`, `HelpSweepContractTest` | 2.2.0 |

## Readers of CLI output

Plugin code that parses what the CLI prints. It must match every release's shape; it doesn't re-implement a rule.

| CLI output | Plugin | Captures | Contract test |
|---|---|---|---|
| `archive --json` | `ArchiveJson` | `fixtures/cli/1.14.3/` and the floor releases | `ArchiveJsonContractTest` |
| `update --dry-run --json` | `UpdatePlanParser` | `fixtures/cli/<v>/update-dry-run/` | `UpdatePlanContractTest` |
| `update` legacy cleanup | update output parser | `fixtures/cli/1.4.1/` | `UpdateOutputParserContractTest` |
| `instructions <artifact> --json` | artifact instructions | `fixtures/cli/<v>/` | `ArtifactInstructionsVersionStabilityTest` |
| `init` failures | failed-init notice | `fixtures/cli/<v>/` | `FailedInitNoticeContractTest`, `InitNoToolsContractTest` |
| `show <change> --json` | `ChangeDeltaModel`, change preview | `fixtures/cli/1.6.0/` | `ChangeDeltasContractTest`, `ChangeShowOutcomeContractTest` |
| `show <spec> --json` structure | `SpecParsingService` | `fixtures/cli/1.6.0/spec-structure/` | `SpecParserCliStructureContractTest` |
| `.openspec.yaml` | `ChangeMetadataParser` | `fixtures/cli/1.7.0/` | `ChangeMetadataContractTest` |
| `validate --json` | validate line, `CliJson` | `fixtures/cli/<v>/` | `ValidationLineContractTest`, `CliJsonReadContractTest` |
| `status --json` | apply readiness | `fixtures/cli/<v>/` | `ApplyReadinessContractTest` |
| `list`, `status` for Explore | Explore context | `fixtures/cli/<v>/` | `ExploreContextContractTest` |
| coordination JSON | coordination parsers | `fixtures/cli/` | `CoordinationContractTest`, `StoreWorksetContractTest`, `StoreWorksetWriteContractTest` |
| `schema` commands | schema tooling | `fixtures/cli/` | `SchemaToolingContractTest` |
| `skillsScope` in `config.yaml` | `ConfigService` | `fixtures/cli/<v>/` | `SkillsScopeConfigContractTest` |
| general CLI JSON | CLI parsers | `fixtures/cli/` | `CliContractTest` |

## Not CLI ports

Contract tests for other sources: Direct API provider responses (`DirectApiErrorsContractTest`, `DirectApiModelsContractTest`, `DirectApiResponseContractTest`), on-disk lookup on a real project (`SkillPathResolutionContractTest`), the CLI argument catalog itself (`CliArgsVersionMatrixTest`, `CliCommandsTest`), and the known-gap guard (`KnownGapsTest`).

## Deliberate differences

Plugin behavior with no upstream counterpart, kept on purpose. Each names its reason and tracker entry.

| Difference | Why | Entry |
|---|---|---|
| With no CLI detected, 1.x detection keeps 1.17's rules and tool list, with no release filter | Upstream has no no-CLI mode; this keeps the plugin's behavior unchanged where nothing can be checked | detection-1x-release-rules |
| With no CLI detected, the core workflow set falls back to six workflows (`WorkflowProfileService.coreWorkflows`) | No CLI answers; the plugin needs a working set | parity sweep |
| The `sync` workflow has no action of its own | Sync Specs covers it in the plugin's UI | parity sweep |
| Adding a tool with no CLI (`AiToolRegistry.builtInAdd`) | Upstream needs the CLI to add a tool; the plugin offers a built-in add that approximates `writeSharedSkillTarget`, held by `BuiltInAddContractTest` to leave a layout the CLI then reads as intended | parity sweep |
| The preferred tool is ticked in the install dialog (`InstallToolTicks`) | A plugin preference; upstream preselects from detection only | parity sweep |
| The router's reference files are recognized as released (`isReleasedRouterReference`) | Upstream's released fingerprint set holds no `references/*.md`; without it a 2.2 project would read as edited | parity sweep |

## Known gaps

Divergences found and not yet fixed. Each has a tracker entry; `KnownGaps` keys hold the captured ones.

| Gap | Entry |
|---|---|
| The skills-scope check reads only `~/.agents/skills`, treats the home folder as a project, and decides "in project" from skill files, not set-up tools | skills-scope-parity |
| Hermes's setup note is hidden when `init` picks the project scope with no `--scope` | parity sweep: setup notes |
| The archive merge applies 1.14.3's behavior on every release: the 1.16.1 misplaced-section refusal and Purpose closing run are ungated; nothing before 1.14.3 is captured | parity sweep: archive merge |
| Validator: orphan level and scope by release; ungated rename bullets and `skip_specs`; no port of 1.11.0's placeholder-Purpose rule | parity sweep: validator |
| Task counting uses 1.13.1's grammar on every release and reads only the top-level `tasks.md` | parity sweep: task counting |
| Scaffolding templates follow pre-1.7 wording; the delta template's shape and the template-hint list aren't upstream's | parity sweep: scaffolding |
| Core workflows (1.4.0, 1.6.0) and the 1.10.0 custom-profile sync rule have no capture on one side | parity sweep: core workflows |
| Archive target naming and collision have no capture; 2.1.1 checks the target before syncing | parity sweep: archive target |
| Workflow invocation wording is uncaptured on 1.7–1.14; leftover `/opsx` commands under a 2.x CLI; the plain-words form reads "Use the <skill> skill: …" where upstream's init hint says `ask <tool> to use the <skill> skill with "…"` | parity sweep: invocations |
| Update migration's 2.1.0 changes (kept shared skills, link guard) are captured on one side | parity sweep: update migration |
| Root resolution applies 1.13.1's store-marker rule on every release | parity sweep: root resolution |
| The tool list is read 2.1-style by callers that don't know the release | parity sweep: tool list |
| Explore paths are replayed only on 1.17.0; 1.x Codex resolves to `.agents` | parity sweep: explore paths |
