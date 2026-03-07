# Validation

OpenSpec provides multiple layers of validation: built-in rules, CLI validation, and editor inspections.

## Built-in Validation Rules

`BuiltInValidator` checks three categories:

### Spec Validation
| Rule | Description |
|------|-------------|
| Title required | Every spec.md must have a `# Title` heading |
| Requirements present | At least one `## Requirement` section |
| RFC 2119 keywords | Requirements should use MUST, SHOULD, MAY, etc. |
| Scenario format | GIVEN/WHEN/THEN clauses properly structured |

### Change Validation
| Rule | Description |
|------|-------------|
| Metadata exists | `.openspec.yaml` present with valid schema and status |
| Required artifacts | All version-required artifacts are present |
| Delta specs valid | Delta spec files reference existing spec domains |

### Config Validation
| Rule | Description |
|------|-------------|
| Schema version | `schema` field matches a supported version |
| Required fields | Version-specific required fields are present |
| Profile valid | Profile name is recognized |

## CLI Validation

When the CLI is available, **OpenSpec → Validate** runs both:
1. Built-in validation (always)
2. `openspec validate` (CLI)

Results are **merged** — issues from both sources are combined and deduplicated. CLI validation may catch additional rules defined in the OpenSpec schema.

## Validation Results

Each validation run produces a `ValidationResult`:
- **passed** — boolean
- **issues** — list of `ValidationIssue` objects
- **source** — `"built-in"`, `"cli"`, or `"merged"`

Each `ValidationIssue` contains:
- **severity** — `ERROR`, `WARNING`, `INFO`
- **path** — file path relative to openspec/
- **line** — line number (0 if unknown)
- **message** — human-readable description
- **rule** — rule identifier

## Editor Inspections

Three inspection tools provide inline warnings as you edit:

| Inspection | Applies To | Checks |
|------------|-----------|--------|
| **SpecFormatInspection** | `spec.md` files | Title, requirements, scenarios |
| **DeltaSpecInspection** | Files in `delta-specs/` | Format, domain references |
| **ConfigValidationInspection** | `config.yaml` | Schema, required fields |

Inspections are enabled by default at WARNING level. They run as you type and show gutter icons + squiggly underlines in the editor.

### Disabling Inspections

**Settings → Editor → Inspections → OpenSpec** — uncheck individual inspections.

## Strict Mode

Enable in **Settings → Tools → OpenSpec → Strict validation**.

In strict mode:
- Warnings are elevated to errors
- All RFC 2119 keyword checks are enforced
- Missing optional fields trigger warnings

## Running Validation

| Method | How |
|--------|-----|
| **Menu** | OpenSpec → Validate |
| **Toolbar** | Validate button in tool window |
| **Context menu** | Right-click a spec or change node |
| **Automatic** | Inspections run in the editor continuously |

Results appear in a notification balloon and in the Console tab.

---

**Previous:** [[AI-Configuration]] | **Next:** [[Workflow-Patterns]]
