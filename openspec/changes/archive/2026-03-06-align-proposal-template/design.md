## Context

The plugin's built-in scaffolding generates proposal.md files using a non-compliant template with sections (Summary, Motivation, Impact) that don't match the OpenSpec 1.2.0 spec-driven schema. The official template uses Why, What Changes, Capabilities (New/Modified), and Impact. The "Propose New Change" dialog labels its text input as "Description" but places it into the non-existent "Summary" section. Four files are involved: `TemplateProvider`, `ProposeChangeDialog`, `ScaffoldingService`, and `OpenSpecProposeAction`.

## Goals / Non-Goals

**Goals:**
- Align `TemplateProvider.proposalTemplate()` output with the official OpenSpec 1.2.0 spec-driven proposal template
- Relabel the dialog input from "Description" to "Why" so the label matches the template section it populates
- Rename the method parameter from `description` to `why` for clarity

**Non-Goals:**
- Changing the design.md or tasks.md templates (separate concern)
- Modifying CLI-based propose flow (only the built-in scaffolding fallback)
- Adding dialog fields for Capabilities or Impact (these require more thought and are filled in after creation)

## Decisions

**1. Map dialog inputs to template sections**

The dialog provides two optional text areas: "Why" maps to `## Why` and "What Changes" maps to `## What Changes`. When a field is left blank, the template inserts the standard HTML comment placeholder. Only the change name is required. Capabilities and Impact are always left as placeholders.

Alternative considered: Adding fields for all four sections. Rejected because Capabilities requires knowing existing spec names (kebab-case identifiers) and Impact is often discovered later — too much friction for initial creation.

**2. Use multi-line text areas**

Both "Why" and "What Changes" benefit from multi-line input — Why can be a paragraph, What Changes is typically a bullet list. Replace `JBTextField` with `JBTextArea` wrapped in a `JBScrollPane` for these fields.

Alternative considered: Keeping single-line fields. Rejected because it forces overly terse input for fields that naturally need more space.

**2. Remove the `# Proposal: <name>` H1 heading**

The official template has no H1 — it starts at `## Why`. Dropping it keeps us compliant. The change name is already visible in the file path and tree node.

Alternative considered: Keeping the H1 as a convenience. Rejected because it's not in the official template and could interfere with tooling that parses the proposal format.

**3. Include the full Capabilities subsection structure**

The template includes `### New Capabilities` and `### Modified Capabilities` with placeholder guidance as HTML comments, matching the official template exactly.

## Risks / Trade-offs

[Existing proposals become inconsistent] → Only affects proposals created via built-in scaffolding going forward. Existing archived proposals are historical and don't need migration. Active proposals (if any) can be manually updated.

[Dialog label "Why" may be less intuitive than "Description"] → The label aligns with OpenSpec terminology. A tooltip or placeholder text can provide additional guidance if needed.

[Optional fields may lead to empty proposals] → Acceptable trade-off. A proposal with just a name is still useful as a placeholder — the user can fill in details later. The proposal is meant to be iterated on.
