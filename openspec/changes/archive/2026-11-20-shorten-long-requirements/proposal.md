## Why

OpenSpec CLI 1.16 warns when a requirement's text runs past 500 characters, and `validate --strict` treats the warning as a failure. Across the main specs, 86 requirements in 27 specs are over that limit, the longest about 3,900 characters. A project-wide strict run therefore fails, which hides real strict failures and makes the strict gate meaningless for the project's own specs.

Shortening the requirements is spec-content work, not a code change. Each requirement's normative text must be kept, with examples and edge cases moved into scenarios, so the work needs a reviewable delta per spec.

## What Changes

- **Shorten each long requirement** to 500 characters or fewer. Keep every normative statement. Move examples, rationale and edge-case detail into scenarios, or split the requirement where it describes more than one behaviour.
- **Keep every scenario.** A MODIFIED requirement must carry all of its scenarios, so no scenario is dropped.
- **Work in batches** by spec, so each batch is reviewable and validates on its own. The pilot batch is `pipeline-interaction`, whose one long requirement is shortened here.
- **Make project strict validation pass** (`openspec validate --all --strict`) once every batch has landed.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

Each spec with a long requirement gets a MODIFIED delta for that requirement, in batches. This change starts with `pipeline-interaction`.

## Impact

- Spec text only. No plugin behaviour changes, and no code, test or fixture changes.
- Each batch is reviewed as spec wording. Normative statements must match the main spec.
