## Why

The OpenSpec CLI (v1.x) offers an expanded workflow profile with 10 slash commands covering the full spec-driven development lifecycle. Our plugin currently implements only 4 core workflows (propose, apply, archive, explore-as-clipboard). Power users who work with Claude Code, Cursor, or other AI tools have access to `/opsx:ff`, `/opsx:continue`, `/opsx:verify`, `/opsx:sync`, `/opsx:bulk-archive`, and `/opsx:new` — but none of these are available in the IntelliJ plugin. This gap means power users drop out of the IDE to use CLI workflows, breaking their flow.

Adding these workflows brings the plugin to full parity with the OpenSpec CLI's expanded profile and positions it as the most complete OpenSpec IDE integration available — across both IntelliJ and VS Code ecosystems.

## What Changes

- **Fast-Forward (FF) action**: One-click workflow that creates a change AND generates all artifacts via Direct API in a single operation. Users describe what they want, and the plugin handles everything — name derivation, change creation, DAG walking, artifact generation. The hero feature for v0.3.0.
- **Continue action**: "Pick up where I left off" — detects the next ready artifact in the current change and generates it. Essential for interrupted workflows or step-by-step artifact creation.
- **Verify action**: Pre-archive quality gate that checks task completion, spec coverage in code, and design coherence. Produces a verification report with CRITICAL/WARNING/SUGGESTION issues.
- **Sync Specs action**: Merges delta specs from a change into main specs without archiving. Handles ADDED, MODIFIED, REMOVED, and RENAMED requirements intelligently.
- **Bulk Archive action**: Archive multiple completed changes at once with conflict detection when multiple changes touch the same spec domains.
- **Custom Schema support**: UI for selecting, forking, and creating workflow schemas. Different artifact DAGs for different project needs. Integrates with `openspec schema fork/init`.
- **Enhanced Explore mode**: Replace clipboard-only explore with an interactive panel for investigating ideas, viewing project context, and comparing approaches — read-only investigation mode.
- **Config Profile management**: Switch between core/expanded profiles and manage workflow selection from Settings.
- **Update CLI instructions**: Trigger `openspec update` from the IDE to refresh agent instruction files.

## Capabilities

### New Capabilities
- `ff-workflow`: Fast-forward workflow — create change and generate all artifacts in one shot
- `continue-workflow`: Continue workflow — resume artifact generation for active changes
- `verify-workflow`: Verification workflow — pre-archive quality gate with completeness, correctness, and coherence checks
- `sync-specs`: Spec synchronization — merge delta specs to main specs without archiving
- `bulk-archive`: Bulk archive — batch archive multiple changes with conflict detection
- `custom-schemas`: Custom workflow schema management — select, fork, create, and configure schemas
- `explore-panel`: Enhanced explore mode — interactive investigation panel replacing clipboard-only explore

### Modified Capabilities
- `workflow`: Add FF, Continue, Verify, Sync, and Bulk Archive actions to the workflow action panel and menus
- `plugin-core`: Add config profile management and `openspec update` CLI delegation
- `onboarding`: Update setup wizard and getting started panel to reflect new workflows

## Impact

- **Actions**: 6+ new `AnAction` classes registered in `plugin.xml`
- **Services**: New `SchemaService` for custom schema management, `VerificationService` for pre-archive checks, `SpecSyncService` for delta-to-main spec merging
- **UI**: New toolbar buttons, menu items, and potentially a verification results panel
- **Settings**: New config profile selector, workflow toggles, schema preferences
- **CLI integration**: New CLI commands used: `openspec new change`, `openspec schema fork/init/which`, `openspec update`, `openspec config profile`
- **Dependencies**: No new external dependencies — all features build on existing IntelliJ Platform SDK and CLI integration patterns
