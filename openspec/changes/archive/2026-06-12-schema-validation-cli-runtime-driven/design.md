## Context

`BuiltInValidator` warns on schema mismatch at two sites that share a dependency on `VersionSupport.getValidSchemas()`. The `validSchemas` field is a hardcoded `Set<String>` per enum entry — historically the right model when schemas were a closed upstream set, but since `openspec schema fork` and `openspec schema init` shipped, schemas are a runtime-discoverable open set. The plugin's `SchemaService.listSchemas()` already calls `openspec schemas --json` and parses results into `List<SchemaInfo>`; the validator simply doesn't consult it.

The Settings → Default schema combo (`OpenSpecSettingsPanel.loadSchemaList`) already populates from `SchemaService.listSchemas()` at runtime — confirming the pattern works at the UI layer. The validator is the last hardcoded surface.

## Goals / Non-Goals

**Goals:**
- A user with a forked custom schema (e.g., `my-team-flow`) sees no validator warning on either the change `.openspec.yaml` or the project `config.yaml`.
- A user with a typo (`spec-drivenn`) still sees a WARNING — accurate text mentions both the built-in set and the CLI-runtime set.
- A user without the CLI installed still gets sensible behavior — the built-in set is the floor; nothing breaks.
- The two existing call sites share one source of truth for "is this schema recognized" so they can't drift.

**Non-Goals:**
- Removing `VersionSupport.getValidSchemas()`. It still answers "what built-ins do we ship with?" for callers without project context.
- Removing the hardcoded built-in set. Adding new built-ins like `task-runner` (hypothetical 1.5.x addition) still wants the enum-level acknowledgment so the plugin documents what it natively supports.
- Migrating to a "no built-in fallback; CLI is the only source of truth" model. Too aggressive — users without a CLI would lose validation entirely.
- Changing severity from WARNING to anything stricter. We're being more lenient, not less.
- Re-validating on every keystroke. The cached known-set is fine; existing `clearCache()` already handles fork/init invalidation.
- Touching the Default schema combo population logic. Already correct.

## Decisions

**Locate the new logic in `SchemaService`, not a new class.** `SchemaService` already owns CLI-schemas-list interaction + caching. Adding `getKnownSchemaNames()` keeps the cache invalidation story coherent (one cache, one `clearCache()`, called consistently on fork/init). A new class would split the cache state and introduce an unnecessary dependency direction.

**Union built-ins + CLI list, not "prefer CLI."** Two reasons. (1) Built-ins are always available offline; preferring CLI when CLI is below the SchemaService floor (1.3.x) would erase the built-ins from validation. (2) The CLI's list IS the built-ins plus any custom forks — empirically the two sets agree on built-in names. Union is the correct semantic.

**Read built-ins from `VersionSupport.V1_2`, not a duplicate constant.** Keeps single source of truth for the built-in set. If upstream adds a new built-in (e.g., `task-runner` in 1.5.x), the existing V1_2 enum-update pattern (per `openspec-1-4-baseline`'s approach) continues to work — the validator picks up the new built-in automatically.

**Update the WARNING text to mention both sources.** When the schema isn't recognized, the message shouldn't say "Valid: [spec-driven, workspace-planning]" because that's only the built-in subset. Instead: "Valid: [<built-ins>] + any schemas listed by `openspec schemas --json` (none detected — install CLI 1.3+ to enable custom schema recognition)" when CLI is unavailable, or "Valid: [<built-ins + CLI list>]" when CLI is available. Honest messaging beats misleading lists.

**Preserve `VersionSupport.getValidSchemas()` as-is but update Javadoc.** Don't delete the method. Don't change the field value. The Javadoc clarifies the role shift: it's now the "built-in fallback set used by callers without project context" rather than the canonical validation set. Anyone reading the method definition gets the full story.

**No new spec capability or requirement.** The "Config validation" requirement still describes what's validated; only the *semantics* of "valid schema" widen. Existing scenarios update; new scenarios cover the custom-fork and CLI-unavailable cases. No requirement structural changes needed.

## Risks / Trade-offs

**`SchemaService.getKnownSchemaNames()` calls into the CLI at validation time, potentially blocking the validator on a slow CLI invocation.** → Mitigation: the existing `cachedSchemas` field caches the CLI result for the session. First validation triggers a CLI call (~50-200ms); subsequent validations are cache hits. `BuiltInValidator` already runs on background threads for periodic re-validation, so a one-time CLI call at startup-ish time is acceptable. If profiling later shows this is a hot path, add a non-blocking "schemas pending — using built-ins" interim state.

**Custom-forked schemas registered AFTER the cache is populated won't be recognized until next IDE restart.** → Mitigation: the existing `clearCache()` is called from `SchemaService.forkSchema()` and `initSchema()` already. As long as the user forks via the IDE's schema-management UI (which calls these methods), the cache invalidates. If the user forks via the CLI from a terminal while the IDE is running, they need to re-open the project or trigger a re-validation. Acceptable — fork is rare and the workaround is obvious.

**CLI returns an empty list (e.g., on `openspec schemas --json` failure) and the known-set falls back to built-ins only.** → That's the desired behavior. The user effectively gets pre-change behavior. No regression.

**Typo case: user writes `spec-drivenn`.** → Built-ins don't contain it; CLI list (which is built-ins plus custom forks) doesn't contain it; warning fires. Behavior identical to today for typos. Net win is *only* legitimate forks.

**`VersionSupport.getValidSchemas()` callers continue to use the built-in-only set, missing custom forks.** → Intentional. Callers without project context (scaffolding templates) can't ask the CLI — they need the built-in fallback. The validator is the right place to do the broader check because it has the project context.

## Migration Plan

None. Existing configs and changes continue to validate; the only behavior change is that more configurations now validate successfully (custom-forked schemas stop triggering warnings). Rollback is a single-commit revert affecting two call sites in `BuiltInValidator` plus one new method in `SchemaService`.
