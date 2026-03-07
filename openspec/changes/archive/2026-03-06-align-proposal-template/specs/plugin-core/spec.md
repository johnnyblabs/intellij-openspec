## MODIFIED Requirements

### Requirement: Proposal Scaffolding Template

The plugin's built-in proposal template SHALL match the official OpenSpec 1.2.0 spec-driven schema template structure.

#### Scenario: Generated proposal structure
- **WHEN** the plugin scaffolds a new proposal.md via built-in scaffolding
- **THEN** the generated file SHALL contain the sections `## Why`, `## What Changes`, `## Capabilities` (with `### New Capabilities` and `### Modified Capabilities`), and `## Impact`
- **THEN** the generated file SHALL NOT contain a `# Proposal:` H1 heading, `## Summary`, or `## Motivation` section

#### Scenario: User input placed in corresponding sections
- **WHEN** the user provides text in the "Why" dialog field during proposal creation
- **THEN** that text SHALL appear under the `## Why` section in the generated proposal.md
- **WHEN** the user provides text in the "What Changes" dialog field during proposal creation
- **THEN** that text SHALL appear under the `## What Changes` section in the generated proposal.md

#### Scenario: Blank optional fields use placeholders
- **WHEN** the user leaves the "Why" or "What Changes" fields blank during proposal creation
- **THEN** the generated proposal.md SHALL contain the standard HTML comment placeholders for those sections
