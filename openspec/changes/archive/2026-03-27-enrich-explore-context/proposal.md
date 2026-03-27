## Why

The Explore tab assembles project context for pasting into AI tools, but the output is too shallow to be useful. Specs are listed by domain name only (no content), change proposals are truncated to 500 characters (designs, specs, and tasks omitted), and the `context` and `rules` fields from `config.yaml` are not included. An AI receiving this context can't meaningfully understand the project.

## What Changes

- Include `context` and `rules` from `config.yaml` in the Project Config section
- Include full requirement summaries for each spec domain (requirement names with their description text)
- Include full artifacts for active changes (proposal, design, specs, tasks) instead of truncated proposals only
- Remove the 500-character proposal truncation

## Capabilities

### New Capabilities

_(none)_

### Modified Capabilities

- `explore-context`: Enrich context assembly to include config context/rules, spec requirement summaries, and full change artifacts

## Impact

- `ExploreContextService.java` — `appendConfigSummary()`, `appendSpecsDomains()`, and `appendActiveChanges()` extended
- `ExploreContextServiceTest.java` — tests updated for richer output
- No new dependencies, no API changes
