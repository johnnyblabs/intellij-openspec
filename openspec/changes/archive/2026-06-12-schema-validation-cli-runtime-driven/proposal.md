## Why

The upstream OpenSpec CLI accepts any non-empty string as a workflow schema (Zod schema is `z.string().min(1)` per `@fission-ai/openspec/dist/core/project-config.js`) and lets users fork project-local custom schemas via `openspec schema fork spec-driven my-team-flow` and `openspec schema init my-schema`. The plugin's validator, however, hardcodes the recognized schema set in `VersionSupport.V1_2.getValidSchemas()` as `{"spec-driven", "workspace-planning"}` and warns whenever a project or change declares anything else.

Concrete user-visible symptom: a user with a legitimate forked `my-team-flow` schema sees these false-positive warnings in the editor:

- `BuiltInValidator.java:181` — `change-schema-incompatible` WARNING on each change's `.openspec.yaml`
- `BuiltInValidator.java:250` — `config-schema-invalid` WARNING on the project `config.yaml`

Both warnings appear even though the CLI is perfectly happy with the schema. The plugin is being more restrictive than upstream — the same architectural pattern profile-ui-cli-alignment course-corrects for the profile UI, applied here to schemas.

## What Changes

### Important framing — combo population is already runtime-driven

Investigation surfaced that `OpenSpecSettingsPanel.loadSchemaList` (`OpenSpecSettingsPanel.java:405-426`) already populates the Default schema combo from `SchemaService.listSchemas()` at runtime. **That part of the original tracker is already correct.** This change targets the validator only.

### Validator changes

- Add `SchemaService.getKnownSchemaNames()` returning a `Set<String>` that unions:
  - The hardcoded built-in set (`VersionSupport.V1_2.getValidSchemas()`, currently `{"spec-driven", "workspace-planning"}`) — always available, no CLI required
  - The CLI-runtime set from `listSchemas()` (just the `name` field of each `SchemaInfo`) when CLI is available + schema-supported
  - Cached for the session; invalidated by the existing `clearCache()` (already called on fork/init)
- Update both `BuiltInValidator` warning sites to use the broader set:
  - Line 181 (`change-schema-incompatible`) — warn only when the change's schema is not in the known set
  - Line 250 (`config-schema-invalid`) — warn only when the project's schema is not in the known set
- Update the WARNING text on both sites to reference the broader known-set (and mention CLI status) so users on no-CLI or pre-1.3 CLI see honest messaging about why a name they expected isn't recognized.

### `VersionSupport.getValidSchemas()` — preserved with clarifying Javadoc

Keep the method and the V1_2 enum entry's `validSchemas` field unchanged. They are the synchronous built-in fallback that the new `SchemaService.getKnownSchemaNames()` will read for the CLI-unavailable case. Update Javadoc to clarify this is the "built-in fallback set," not the canonical valid-set anymore.

### What does NOT change

- The `VersionSupport.V1_2.validSchemas` field's value. Adding new *built-in* schemas to upstream (e.g., if 1.5.x introduces a `task-runner` workflow) still requires updating the V1_2 enum entry; custom forks ride free via the runtime path.
- The Settings → Default schema combo. Already runtime-populated.
- The error severity. Still WARNING for unknown schemas, not ERROR — we want users to see typos, just not false positives.

## Capabilities

### New Capabilities
<!-- None — modifying existing validation behavior. -->

### Modified Capabilities
- `validation`: the "Config validation" requirement's "Schema field required" / "Schema value invalid for version" scenarios need updating. The semantic shift is that "invalid" now means "neither built-in nor known to the CLI" — strictly more permissive than before. New scenarios cover the custom-forked schema case (no warning) and the CLI-unavailable fallback (built-in-only check).

## Impact

- **Code:** `SchemaService.java` (one new method, ~25 lines), `BuiltInValidator.java` (two call-site changes + updated warning text), `VersionSupport.java` (Javadoc-only update).
- **Tests:** `SchemaServiceTest` (new `KnownSchemaNames` nested class with ~4 cases), `ConfigVersionValidationTest` (new fork-aware scenario), regression test for the typo case.
- **Specs:** `openspec/specs/validation/spec.md` — modify "Schema value invalid for version" scenario; add new scenarios for "Custom-forked schema recognized" and "CLI-unavailable known-set falls back to built-ins."
- **Plugin behavior for users with built-in schemas only:** unchanged.
- **Plugin behavior for users with custom-forked schemas (CLI ≥ 1.3):** spurious warnings disappear.
- **Plugin behavior for users with no CLI:** unchanged from today — only built-in schemas recognized.
- **Plugin behavior for users on pre-1.3 CLI:** unchanged from today — `SchemaService` returns empty list (per `MIN_CLI_VERSION` floor); known-set falls back to built-ins.
- **Risk:** very low. The change is one-directional permissive — strict→permissive cannot worsen the validation experience for users with legitimate setups. The only regression risk is the typo case (user writes `spec-drivenn`), and the warning still fires there since the typo isn't in built-ins OR CLI list.

## References

- Tracker: the linked issue (the canonical tracker — has the original fix sketch)
- Tracker: the linked issue
- Architectural sibling: `profile-ui-cli-alignment` (in-flight, on `main`) — same "don't be more restrictive than upstream" lesson, applied to profiles
- Upstream source confirming `z.string().min(1)`: `@fission-ai/openspec/dist/core/project-config.js` Zod schema for the `schema` field

No new Forgejo/Plane trackers created. When this change archives, the archive flow closes the linked tracker issue.
