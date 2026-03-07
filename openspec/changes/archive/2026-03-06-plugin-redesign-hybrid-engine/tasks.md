# Tasks: Plugin Redesign — Hybrid Engine

## Phase 1: Foundation
- [x] Create OpenSpecSettings (PersistentStateComponent)
- [x] Create OpenSpecConfigurable + OpenSpecSettingsPanel
- [x] Create CliDetectionService
- [x] Create OpenSpecNotifier
- [x] Update CliRunner with detected path, timeout, CliException
- [x] Update OpenSpecProjectService with startup detection
- [x] Register services, configurable, notification group in plugin.xml

## Phase 2: Hybrid Validation
- [x] Create ValidationResult and ValidationIssue records
- [x] Create VersionSupport enum
- [x] Create BuiltInValidator service
- [x] Update OpenSpecValidateAction for hybrid validation

## Phase 3: Scaffolding & Lifecycle
- [x] Create TemplateProvider
- [x] Create ScaffoldingService
- [x] Create ProposeChangeDialog (DialogWrapper)
- [x] Create ChangeStatus enum
- [x] Update OpenSpecProposeAction for hybrid behavior
- [x] Update OpenSpecInitAction with built-in fallback
- [x] Update OpenSpecArchiveAction with built-in fallback
- [x] Add lifecycle methods to ChangeService

## Phase 4: Tool Window
- [x] Create OpenSpecConsolePanel
- [x] Create CreateDeltaSpecAction
- [x] Update ToolWindowFactory with tabbed layout
- [x] Update ToolWindowPanel with status bar, context menus, debounced refresh
- [x] Update SpecTreeModel with status labels, missing artifacts, delta-spec nodes
- [x] Update SpecTreeCellRenderer with status colors and italic styling

## Phase 5: CLI Output
- [x] Create CliOutputParser
- [x] Update OpenSpecListAction with built-in fallback
- [x] Update OpenSpecRefreshAction for tabbed layout

## Phase 6: Dogfooding
- [x] Create change directory with all artifacts
