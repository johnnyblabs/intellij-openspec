#!/bin/bash
# Seed a disposable OpenSpec demo project that exercises the plugin's change
# lifecycle and its behavioral edge cases. Single source of truth for BOTH:
#   - the manual test-drive (.claude/skills/lifecycle-testdrive)
#   - the automated UI smoke journeys (uiTest fixtures)
# so the human and automated walkthrough environments cannot drift apart.
#
# Usage: ./scripts/seed-lifecycle-demo.sh <target-dir>
#
# The target directory must not exist (each test-drive/journey gets a fresh
# path — per-project sandbox state like cleanup-dismissal memory is keyed to
# the project path).
#
# What the seeding deliberately triggers:
#   * init by an OLD OpenSpec CLI (1.3.1) with the junie tool -> leaves legacy
#     .junie/commands/opsx-*.md files, so the Update action's legacy-cleanup
#     flow fires on a 1.4+ CLI
#   * a main spec with a lowercase "### requirement:" header -> exercises the
#     CLI-1.4 case-insensitive header parsing parity
#   * a change with proposal done and design/specs/tasks pending -> the
#     workflow chips render all three DAG states (done / ready / blocked)
#
# (Distinct from scripts/demo-project.sh, which builds a rich Java project for
# marketplace screenshots.)

set -euo pipefail

DEMO="${1:?usage: seed-lifecycle-demo.sh <target-dir>}"

if [ -e "$DEMO" ]; then
    echo "error: target '$DEMO' already exists — seeding requires a fresh directory" >&2
    exit 1
fi

mkdir -p "$DEMO"
cd "$DEMO"
export OPENSPEC_TELEMETRY=0

# Old CLI on purpose: creates the legacy command files the cleanup flow detects.
npx -y @fission-ai/openspec@1.3.1 init --tools junie . < /dev/null > /dev/null 2>&1

mkdir -p openspec/specs/greeting
cat > openspec/specs/greeting/spec.md << 'EOF'
# Greeting

## Purpose
Demo capability for the lifecycle walkthrough.

## Requirements

### requirement: Friendly greeting
The system SHALL greet the user by name.

#### Scenario: Greet
- **WHEN** the user arrives
- **THEN** the system greets them by name
EOF

# Keyword only in the requirement HEADER — triggers the plugin's targeted
# "move the keyword onto the requirement body line" diagnostic (CLI 1.4 parity).
mkdir -p openspec/specs/keyword-in-header
cat > openspec/specs/keyword-in-header/spec.md << 'EOF'
# Keyword In Header

## Purpose
Demo spec whose requirement carries its RFC 2119 keyword only in the header.

## Requirements

### Requirement: The system SHALL demonstrate the header hint
A body sentence without the normative keyword anywhere.

#### Scenario: Hint
- **WHEN** the file is inspected
- **THEN** the targeted keyword-placement diagnostic appears
EOF

mkdir -p openspec/changes/demo-add-farewell
cat > openspec/changes/demo-add-farewell/.openspec.yaml << 'EOF'
schema: spec-driven
created: 2026-07-04
EOF
cat > openspec/changes/demo-add-farewell/proposal.md << 'EOF'
## Why
The demo needs a change mid-lifecycle so the workflow chips show done / ready / blocked states.

## What Changes
- Add a farewell message alongside the greeting.

## Capabilities

### Modified Capabilities
- `greeting`: adds a farewell requirement.

## Impact
Demo only.
EOF

echo "Seeded lifecycle demo at: $DEMO"
