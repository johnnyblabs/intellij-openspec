## Why

The plugin's built-in proposal template (`TemplateProvider.proposalTemplate()`) uses non-compliant sections (Summary, Motivation, Impact) that don't match the official OpenSpec 1.2.0 spec-driven schema template. The official template requires Why, What Changes, Capabilities (New/Modified), and Impact sections. Additionally, the "Propose New Change" dialog labels its input as "Description" but places it into a "Summary" section — neither of which exist in the official format. The `## Why` header is specifically checked by OpenSpec tooling to avoid archive warnings.

## What Changes

- Replace the built-in proposal template sections (Summary, Motivation, Impact) with the official OpenSpec 1.2.0 sections (Why, What Changes, Capabilities, Impact)
- Remove the `# Proposal: <name>` H1 heading — the official template starts at `## Why`
- Relabel the dialog's "Description" field to "Why" and add a new "What Changes" field
- Both "Why" and "What Changes" are optional — only Change name is required, enabling quick proposals where details are filled in later
- Convert dialog text inputs to multi-line text areas for Why and What Changes
- Update `proposalTemplate()` to accept optional `why` and `whatChanges` parameters, populating sections with user input or leaving HTML comment placeholders when blank
- Update `ScaffoldingService.createChange()` and `OpenSpecProposeAction` to pass dialog inputs to the updated template method

## Capabilities

### New Capabilities

_(none — this is a correction to existing behavior, not a new feature)_

### Modified Capabilities

- `actions`: The propose action dialog labels and input handling change
- `plugin-core`: The scaffolding template for proposals changes to match OpenSpec 1.2.0

## Impact

- `TemplateProvider.java` — proposal template rewritten, method signature updated
- `ProposeChangeDialog.java` — "Description" replaced with optional "Why" and "What Changes" multi-line text areas; description validation removed
- `ScaffoldingService.java` — parameter naming and passing updated
- `OpenSpecProposeAction.java` — passes dialog inputs to updated template method
- No external API or dependency changes
