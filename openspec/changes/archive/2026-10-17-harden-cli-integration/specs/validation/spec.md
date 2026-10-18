## ADDED Requirements

### Requirement: Unreadable CLI validate output is a failed run

When `validate --json` output cannot be read as a verdict, the plugin SHALL treat the CLI run as failed and use the built-in validator's result as the verdict, as it does when the CLI cannot run. Unreadable output includes malformed or truncated JSON, and a JSON object with neither an `items` array nor an error `status` entry. Such output SHALL NOT be reported as a pass with zero issues.

#### Scenario: Truncated JSON does not pass
- **WHEN** the CLI's `validate --json` stdout begins with `{` but is not complete JSON
- **THEN** the plugin SHALL NOT report the run as passed, and SHALL use the built-in validator's result as the verdict

#### Scenario: A JSON object without a verdict does not pass
- **WHEN** the CLI's `validate --json` stdout is a JSON object with no `items` array and no error `status` entry
- **THEN** the plugin SHALL use the built-in validator's result as the verdict

#### Scenario: Captured no-root response falls back
- **WHEN** the CLI's `validate --all --json` output is the captured 1.14.3 `no_openspec_root` response
- **THEN** the plugin SHALL use the built-in validator's result as the verdict

#### Scenario: A real report with failing items is unaffected
- **WHEN** the CLI exits 1 with an `items` array whose failing items carry ERROR issues
- **THEN** the plugin SHALL take the verdict and issues from the items exactly as before
