#!/bin/bash
# Seed a disposable OpenSpec demo project that exercises the plugin's change
# lifecycle and its behavioral edge cases. Single source of truth for BOTH:
#   - the manual test-drive (.claude/skills/lifecycle-testdrive)
#   - the automated UI smoke journeys (uiTest fixtures)
# so the human and automated walkthrough environments cannot drift apart.
#
# Usage: ./scripts/seed-lifecycle-demo.sh <target-dir> [--archive-ready-demo]
#
#   --archive-ready-demo  also seed two fully-specified changes for the Changes-tree rollup
#                         walkthrough: demo-apply-ready (artifacts complete, tasks 1/3 -> green dot)
#                         and demo-archive-ready (artifacts complete, tasks 3/3 -> check mark +
#                         "all tasks done"). Opt-in so the captured uiSmoke fixture
#                         (src/integrationTest/testData/lifecycle-demo) is unaffected: without the
#                         flag the output is unchanged.
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

DEMO="${1:?usage: seed-lifecycle-demo.sh <target-dir> [--archive-ready-demo]}"
ARCHIVE_READY_DEMO=false
if [ "${2:-}" = "--archive-ready-demo" ]; then
    ARCHIVE_READY_DEMO=true
elif [ -n "${2:-}" ]; then
    echo "error: unknown option '$2'" >&2
    exit 1
fi

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

# A change with every spec-driven artifact present and a valid delta. $2 is the tasks.md body.
seed_complete_change() {
    local name="$1" tasks="$2"
    mkdir -p "openspec/changes/$name/specs/greeting"
    printf '%s\n' "schema: spec-driven" "created: 2026-10-17" > "openspec/changes/$name/.openspec.yaml"
    printf '%s\n' \
        "## Why" \
        "Demo change for the Changes-tree rollup walkthrough ($name)." \
        "" \
        "## What Changes" \
        "- Add a parting message to the greeting capability." \
        "" \
        "## Capabilities" \
        "" \
        "### Modified Capabilities" \
        "- \`greeting\`: adds a parting-message requirement." \
        "" \
        "## Impact" \
        "Demo only." > "openspec/changes/$name/proposal.md"
    printf '%s\n' "## Context" "Demo only." "" "## Decisions" \
        "- Keep the parting message next to the greeting." > "openspec/changes/$name/design.md"
    printf '%s\n' \
        "## ADDED Requirements" \
        "" \
        "### Requirement: Parting message for $name" \
        "The system SHALL show a parting message when the user leaves." \
        "" \
        "#### Scenario: Leave" \
        "- **WHEN** the user leaves" \
        "- **THEN** the system shows a parting message" > "openspec/changes/$name/specs/greeting/spec.md"
    printf '%s\n' "## 1. Implement" "" "$tasks" > "openspec/changes/$name/tasks.md"
}

if [ "$ARCHIVE_READY_DEMO" = true ]; then
    seed_complete_change demo-apply-ready \
        "$(printf '%s\n' '- [x] 1.1 Add the message' '- [ ] 1.2 Wire it up' '- [ ] 1.3 Test it')"
    seed_complete_change demo-archive-ready \
        "$(printf '%s\n' '- [x] 1.1 Add the message' '- [x] 1.2 Wire it up' '- [x] 1.3 Test it')"
fi

echo "Seeded lifecycle demo at: $DEMO"
