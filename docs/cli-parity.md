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
| `dist/core/init.js` `resolveSkillScope`; `global-skills.js` `supportsGlobalSkills` | `AiToolDetectionService.installsForMachine`, `SavedSkillsScope` | 2.1.0 machine scope | `fixtures/cli/<v>/machine-wide/` on 2.1.0–2.2.0 | `ConfiguredToolsContractTest` | 2.2.0 |
| `dist/core/global-skills.js` `readGlobalSkills`, `projectHasOwnSkills` | `ProjectSkillsScope` | 2.1.0 | `fixtures/cli/<v>/machine-wide/`; `fixtures/cli/<v>/skills-scope/` | `ProjectSkillsScopeContractTest` | to sweep |
| `dist/core/shared/skill-paths.js` link folders | `SkillLinkDirs` | 2.0.0, 2.1.0 | `fixtures/cli/<v>/ai-tools.json` `effective` | `SkillLinkDirsContractTest` | to sweep |
| `dist/core/archive.js` merge, `specs-apply.js` | `SpecMerge` | 2.1.0, 2.1.1 | `fixtures/cli/archive-1.14.3/`, `archive-2.1.0/`, `archive-2.1.1/` | `ArchiveMergeContractTest`, `NestedCapabilitySyncContractTest` | to sweep |
| `dist/core/archive.js` `contentTheMergeCannotName` | `RetirementAudit` | 1.8.0 retirement; 1.13.0 and 2.1.1 check variants; 1.11.0 description | `fixtures/cli/<v>/retire/` on 1.7.0–2.2.0 | `RetirementContractTest` | 2.2.0 |
| `dist/utils/change-metadata.js` `readBooleanMarker`, `ChangeMetadataSchema` | `RetirementMarkers` | 1.8.0 `retire_capabilities`; 1.11.0 reason sanitizing | `fixtures/cli/<v>/retire/` `marker-*` | `RetirementContractTest` | 2.2.0 |
| `dist/core/archive.js` `retireSpec` | `SpecSyncService.deleteAndPrune`, `retiredWarning` | 1.8.0 | `fixtures/cli/<v>/retire/` | `RetirementApplyTest` | 2.2.0 |
| retirement fix text (`archive.js` hint) | `RetireMarkerFix` | 1.8.0 | `fixtures/cli/<v>/retire/` | `RetireMarkerFixContractTest` | 2.2.0 |
| `dist/core/generated-fingerprint.js` | `ReleasedSkillFingerprints` | 2.x releases | `fixtures/cli/<v>/fingerprints/` | `ReleasedSkillFingerprintsContractTest` | to sweep |
| `CORE_WORKFLOWS` | core workflow set | to sweep | `fixtures/cli/<v>/` | `CoreWorkflowsContractTest` | to sweep |
| workflow templates (`dist/core/templates/workflows/`) | workflow-gated actions | to sweep | `fixtures/cli/<v>/` | `WorkflowParityContractTest` | to sweep |
| workflow invocation wording | `WorkflowInvocation` | to sweep | `fixtures/cli/<v>/workflow-invocations.json` | `WorkflowInvocationContractTest`, `VerifyHandoffTest` | to sweep |
| tool `setupNote` | setup notes | 2.1.0 | `fixtures/cli/<v>/ai-tools.json` | `SetupNotesContractTest` | to sweep |
| installer machine markers | `MachineToolMarkers` | to sweep | `fixtures/cli/<v>/skills-scope/` | `MachineToolMarkersContractTest` | to sweep |
| skill spot classification | `SkillSpot` | to sweep | `fixtures/cli/<v>/skill-spots/` | `SkillFolderShapeTest` | to sweep |
| `.openspec-target` list | `SkillToolList` | 2.0.0 list; 2.1.0 aliases | `fixtures/cli/<v>/detect/` | `OpenSpecTargetListContractTest` | to sweep |
| tool add with no CLI (writes) | `AiToolRegistry.builtInAdd` | to sweep | `fixtures/cli/<v>/detect/` | `BuiltInAddContractTest` | to sweep |
| explore skill and command paths | `AiToolRegistry` explore paths | to sweep | `fixtures/cli/<v>/` | `ExplorePathsContractTest` | to sweep |
| update migration plan | `UpdateMigration` | 2.0.0, 2.1.0, 2.1.1 | `fixtures/cli/<v>/update-migration/` | `UpdateMigrationTest`, `UpdateMigrationAtRiskContractTest`, `MigrationNoticeTest` | to sweep |
| root and store resolution (`context --json`) | `RootResolver` | to sweep | `fixtures/cli/<v>/` | `StoreRootContractTest` | to sweep |
| `validate` rules | `BuiltInValidator` and its rule ports | 1.16.0, 1.16.1 behaviours; 2.x corpus | `fixtures/cli/1.14.3/`, `fixtures/cli/1.16.0/`, `fixtures/cli/1.16.1/`, `fixtures/cli/2.1.1/parity-corpus-2x/` | `ValidatorRulePortsContractTest`, `RenamedVerdictContractTest`, `Validate116ContractTest`, `Validate1161ContractTest`, `ClosingRunHeadingContractTest`, `UnrecognizedDeltaSectionContractTest`, `CliConfigToleranceContractTest` | to sweep |
| task counting (`list --json`) | task lines | to sweep | `fixtures/cli/1.13.2/` | `TaskLinesContractTest` | to sweep |
| built-in scaffolding (no CLI) | scaffolding templates | to sweep | `fixtures/cli/` | `ScaffoldingContractTest`, `StatusPassThroughContractTest` | to sweep |
| CLI commands and flags (`--help`) | `CliFlags`, `CliCommands` | every release | `fixtures/cli/<v>/help/` | `CliFlagTableContractTest`, `HelpSweepContractTest` | to sweep |

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

## Known gaps

Divergences found and not yet fixed. Each has a tracker entry; `KnownGaps` keys hold the captured ones.

| Gap | Entry |
|---|---|
| The skills-scope check reads only `~/.agents/skills`, treats the home folder as a project, and decides "in project" from skill files, not set-up tools | skills-scope-parity |
