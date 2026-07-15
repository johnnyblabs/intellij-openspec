## ADDED Requirements

### Requirement: Store registration outcome semantics across CLI generations

The plugin SHALL treat `openspec store register --json` outcomes according to the CLI's own parsed report rather than plugin-side assumptions about root health. A registration the CLI reports as successful SHALL be treated as success even when the root lacks the planning directories (`openspec/specs`, `openspec/changes`, `openspec/changes/archive`) — the CLI 1.6+ fresh/config-only-root case. A registration refusal SHALL be surfaced from the parsed uniform `status[]` envelope with the entry's `fix` remediation verbatim; this includes the 1.6 refusal codes `invalid_store_pointer` and `store_root_pointer_declared` (registering a root whose `openspec/config.yaml` declares `store:`) and the 1.5-generation refusal code `store_register_root_unhealthy`. The plugin SHALL NOT special-case any refusal code in a way that breaks when a code is absent on another supported CLI generation.

#### Scenario: Fresh root registers successfully on CLI 1.6+
- **WHEN** `store register` succeeds for a root that lacks the planning directories
- **THEN** the plugin SHALL treat the registration as a success and refresh the store listing, with the new store presented without any unhealthy or error marker

#### Scenario: Pointer-root refusal surfaced with its fix
- **WHEN** `store register` fails with `invalid_store_pointer` or `store_root_pointer_declared`
- **THEN** the plugin SHALL surface the parsed `status[]` message and its `fix` string verbatim, and SHALL NOT display raw CLI stderr

#### Scenario: 1.5-generation unhealthy-root refusal still parsed
- **WHEN** a 1.5-generation CLI fails `store register` with `store_register_root_unhealthy`
- **THEN** the plugin SHALL surface the parsed failure message and its `fix` remediation exactly as before
