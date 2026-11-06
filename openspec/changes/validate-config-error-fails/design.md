# Design

## Context

See proposal.md, "Why". Two upstream facts shape the approach. Both come from the 1.16.0 package source, `commands/validate.js` and `readConfigParseError` in `core/project-config.js`:

- The configuration check runs on every `validate` invocation once the root resolves, before the bulk, single-item and `--strict` paths split. So the response is the same for `--all`, `<id> --type change|spec` and `--strict`.
- Upstream reports only two failures: the file cannot be read (`unreadable_config`), and `parseYaml` throws (`unparseable_config`). YAML whose top level is a scalar or a list parses, and is not reported. `config.yml` is used when `config.yaml` is absent; the plugin reads only `config.yaml` today, and that does not change here.

How the plugin reads the response today:

- `CliJson.readReport` returns a `Failed` with `kind=ENVELOPE`, `code`, `message` and `fix`. The capture contract tests are in `Validate116ContractTest`.
- `CliOutputParser.parseJsonOutput` throws `CliErrorResponseException`, and `OpenSpecValidateAction.cliOrFallback` catches every exception and falls back.
- `SpecPreviewRenderer.cliValidationLine` catches the same exception and leaves the line out.
- `ConfigService.reload` catches every exception, including the SnakeYAML `ClassCastException` a non-mapping file causes, and stores `new OpenSpecConfig()`.

## Goals / Non-Goals

**Goals:**
- One place decides that a response is a configuration error, so the Validate action and the Change Deltas pane cannot disagree.
- The ERROR text comes from the CLI's response, never from text the plugin writes.

**Non-Goals:**
- Reading `config.yml`.
- Changing how `status`, `instructions` or other commands react to a broken config. Upstream only warns there, and the plugin's readers already tolerate it.
- Putting `config.yaml` read or parse failures in the editor inspection. The inspection works on PSI, so a file the IDE can open is already parsed by the YAML plugin, which shows its own syntax errors.

## Decisions

1. **A typed exception, a subclass of `CliErrorResponseException`.** `parseJsonOutput` throws `CliConfigErrorException(code, message, fix)` when the `Failed` envelope has no items and its code is `unparseable_config` or `unreadable_config`.
   - Callers that only know the parent type keep falling back, which is safe.
   - The two callers that should report the failure catch the subclass first.
   - *Alternative:* return a failed `ValidationResult` straight from `parseJsonOutput`. Rejected, because the parser has no project and cannot place the issue at `config.yaml`. It would also change the parser's contract for every caller at once.
   - *Alternative:* check `failed.code()` in the action. Rejected, because the pane would need the same check, which is the duplication goal 1 rules out.
2. **The failed result is built in one place**, `CliConfigErrorException.toResult(configPath)`. `OpenSpecValidateAction.configErrorResult(project, ex)` resolves the path and calls it; `cliValidationLine`, which has no project and only counts, calls it directly.
   - It holds one ERROR, with `source=cli` and rule `cli`, the rule every CLI-reported issue carries.
   - The path is the resolved `config.yaml` (`OpenSpecFileUtil.getConfigFile`, else `<base>/openspec/config.yaml`), so the console hyperlink opens the file.
   - The line is 1. The CLI's parse message carries its own line and column, and the plugin does not parse them out of prose.
   - The text is `message + " " + fix`, which is `CliJson.Failed.describe()`.
   - It merges no built-in config result. That result would carry the new `config-unparseable` WARNING, which duplicates the error.
3. **`ConfigService` records why the load failed** as a nullable `LoadProblem(kind, detail)`, while keeping defaults for every caller. The config and its problem are published together as one `LoadResult` snapshot (`current()`), so a concurrent reload can never pair one load's config with another load's problem.
   - `UNREADABLE` covers an `IOException` from the input stream.
   - `UNPARSEABLE` covers a SnakeYAML `YAMLException` (`MarkedYAMLException` is a subclass), with the detail being the first line of its message.
   - A `ClassCastException` or a field type mismatch records nothing, matching upstream, which parses such a file without error.
   - *Alternative:* return null instead of defaults. Rejected, because `OpenSpecSettings.getEffectiveVersion` and `ExploreContextService` would treat it as "no config" and change behaviour elsewhere.
4. **The built-in WARNING is non-failing and outside the strict flip set.** `validateConfig` returns `passed=true` with one WARNING, `config-unparseable` or `config-unreadable`, and both rules stay out of `CLI_MIRRORING_STRICT_WARNINGS`.
   - 1.16 fails such a file, but 1.3–1.15 validate it clean, and the fallback must not be stricter than any supported client.
   - A WARNING is still a visible improvement over today's silence.
   - When the file is broken, the schema and other field checks are skipped, because they would run against defaults and produce a misleading `config-schema-required` INFO.

## Risks / Trade-offs

- **Upstream renames a code, or adds a third configuration code.** The response then falls back as it did before. This fails safe, and the capture contract test notices a renamed code on the next fixture refresh.
- **The fallback stays laxer than 1.16 when the CLI is absent.** That is intended: the spec says the fallback MAY be laxer.
- **The line is fixed at 1.** The hyperlink lands at the top of the file, and the CLI's message names the real position.
