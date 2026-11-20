## Why

From OpenSpec CLI 1.16, `openspec validate` checks `openspec/config.yaml` before it validates anything. If the file cannot be read or is not valid YAML, it fails the run (exit 1) with an item-less error response carrying `unreadable_config` or `unparseable_config`. The plugin treats every item-less error response as "CLI run failed" and falls back to the built-in validator. The built-in validator reads a broken config as an empty one and finds nothing wrong with it, so Validate reports **passed** on a project the CLI fails. This is the bug in the linked issue.

## What Changes

- **CLI present:** when `validate --json` answers with an `unparseable_config` or `unreadable_config` error response, Validate SHALL report **failed**. The failure carries one ERROR built from the CLI's own message and fix, located at `config.yaml`. It does not fall back to the built-in verdict. Every other item-less error response (`invalid_item`, `unknown_item`, `no_openspec_root`, unreadable output) keeps falling back as today.
- **CLI absent:** the built-in config check reports a `config.yaml` that cannot be read, or that is not valid YAML, as a non-failing **WARNING**. It does not fail the verdict in default or strict mode, because older supported CLIs (1.3–1.15) validate a broken config clean, and the fallback is never more restrictive than a supported client. A config that parses but has an unexpected shape (for example a scalar or a list) is not a parse failure, matching upstream.
- `ConfigService` still falls back to defaults on a broken config, but records **why** the load failed (unreadable, or a YAML syntax error with its first message line), so the validator can report it instead of hiding it.
- Correct the outdated statement, in code comments and in the validation spec, that the CLI's `validate` never reads `config.yaml`.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `validation`: how a 1.16 configuration error response is reported (a failure rather than a fallback); the built-in config check's handling of an unreadable or unparseable file; and the corrected statement about whether the CLI reads `config.yaml`.
- `spec-viewer`: the Change Deltas pane's validation line shows that failure as well, since the pane must agree with Validate.

## Impact

- **Code:**
  - `OpenSpecValidateAction`: `cliOrFallback` and the `combineWithCli` javadoc.
  - `CliOutputParser` and `CliJson`: expose the configuration error response so the action can tell it apart from the other error responses.
  - `SpecPreviewRenderer.cliValidationLine`: show the failure instead of leaving the line out.
  - `ConfigService`: record the load failure.
  - `BuiltInValidator.validateConfig`: the new WARNING.
- **Tests:**
  - Contract tests against the captured 1.16.0 `errors/validate-un{parseable,readable}-config` fixtures.
  - Built-in config tests for a malformed file, a non-mapping file and an unreadable file.
- **Compatibility:**
  - No platform API changes, so IntelliJ IDEA 2024.2+ support is unaffected.
  - With CLI 1.3–1.15 present, behaviour is unchanged: those CLIs never send this response, so their verdict stays authoritative.
- **User-visible:** the notification and console for a broken `config.yaml` now say "failed" and show how to fix it. The CHANGELOG gets a Fixed entry.
