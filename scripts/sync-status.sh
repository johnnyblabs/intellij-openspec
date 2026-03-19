#!/usr/bin/env zsh
# sync-status.sh — Sync Forgejo issues and Plane work items with actual project status
#
# Usage: ./scripts/sync-status.sh
#
# This script:
# 1. Closes duplicate Forgejo issues (no-milestone originals)
# 2. Closes completed Forgejo issues (by title match)
# 3. Updates Plane work item statuses
# 4. Links Plane work items to Forgejo issues via external_id

set -euo pipefail
SCRIPT_DIR="${0:A:h}"
source "${SCRIPT_DIR}/lib/common.sh"
load_env
check_deps

source "${SCRIPT_DIR}/lib/forgejo-api.sh"
source "${SCRIPT_DIR}/lib/plane-api.sh"

# ============================================================
# Configuration — edit these when completing new work items
# ============================================================

# Forgejo issues to force-close (duplicates, no-milestone originals)
DUPLICATE_ISSUES=(1 2 3 4 5 6 7 8 38 39 40 41 42 43 44 45 46 47 48 49 50 51 52 53 54 55 56 57 58)

# Completed work items — title + closing comment
typeset -A COMPLETED_ITEMS
COMPLETED_ITEMS=(
  ["Add unit tests for CliOutputParser"]="Completed. CliOutputParserTest.java covers valid JSON, malformed JSON, text fallback, and empty input."
  ["Add unit tests for VersionSupport"]="Completed. VersionSupportTest.java covers version enum behavior, required fields, and artifacts per version."
  ["Create wiki documentation"]="Completed. 17 wiki pages created via setup-forgejo.sh covering Architecture, Getting Started, Service Layer, and more."
  ["Set up project tracking infrastructure"]="Completed. Forgejo labels/milestones/wiki/issues and Plane project/cycles/work items all configured."
  ["Feature comparison matrix maintenance"]="Initial version created at docs/feature-comparison-matrix.md. Covers all 4 VS Code extensions across 10 feature categories."
)

# Title → Forgejo issue number mapping (for Plane cross-linking)
typeset -A FORGEJO_ISSUE_MAP
FORGEJO_ISSUE_MAP=(
  ["Add unit tests for SpecParsingService"]=59
  ["Add unit tests for BuiltInValidator"]=60
  ["Add unit tests for CliOutputParser"]=61
  ["Add unit tests for VersionSupport"]=62
  ["Handle malformed YAML gracefully in ConfigService"]=63
  ["Add CLI command timeout configuration"]=64
  ["Add progress indicator for long-running operations"]=65
  ["Improve error messages for API failures"]=66
  ["Add keyboard shortcuts for common actions"]=67
  ["Fix dark theme colors in tree renderer"]=68
  ["Add spec template selection for Propose"]=69
  ["Add inline quick-fixes for validation issues"]=70
  ["Improve validation result formatting"]=71
  ["Add v2.0 schema support"]=72
  ["Add integration tests for action lifecycle"]=73
  ["Add notification grouping"]=74
  ["Support multi-project workspaces"]=75
  ["Publish to JetBrains Marketplace"]=76
  ["Add Ollama integration for local AI"]=77
  ["Add streaming API response support"]=78
  ["Add spec search and filtering"]=79
  ["Register OpenSpec file types"]=80
  ["Add change diff viewer"]=81
  ["Add OpenSpec file templates"]=82
  ["Document all public APIs with Javadoc"]=83
  ["Add CI pipeline"]=84
  ["Add tree node tooltips"]=85
  ["Handle concurrent file operations safely"]=86
  ["Add config.yaml code completion"]=87
  ["Welcome panel and onboarding experience"]=88
  ["Custom plugin icon and visual identity"]=89
  ["Polish artifact pipeline with proper icons and theme support"]=90
  ["Generate All button in workflow panel"]=91
  ["Guided first proposal flow"]=92
  ["Spec gutter markers for code-to-spec traceability"]=93
  ["Spec coverage panel"]=94
  ["Change diff viewer with delta spec preview"]=95
  ["Spec search and filtering in tool window"]=96
  ["Engage with OpenSpec/Fission AI for official integration"]=97
  ["Feature comparison matrix maintenance"]=98
)

# ============================================================
# Helper functions
# ============================================================

close_forgejo_duplicates() {
  echo "\n========================================"
  echo "  Step 1: Close duplicate issues"
  echo "========================================"
  _forgejo_auth
  for issue_num in "${DUPLICATE_ISSUES[@]}"; do
    local payload
    payload=$(jq -n '{ state: "closed" }')
    if api_call PATCH "${FORGEJO_REPO_API}/issues/${issue_num}" "$payload"; then
      log_success "Closed duplicate issue #${issue_num}"
    else
      log_warn "Could not close issue #${issue_num} (may already be closed)"
    fi
    sleep 0.2
  done
}

close_forgejo_completed() {
  echo "\n========================================"
  echo "  Step 2: Close completed issues"
  echo "========================================"

  for title in "${(@k)COMPLETED_ITEMS}"; do
    local comment="${COMPLETED_ITEMS[$title]}"
    _forgejo_auth

    local encoded_title
    encoded_title=$(printf '%s' "$title" | python3 -c "import sys,urllib.parse; print(urllib.parse.quote(sys.stdin.read().strip()))")

    if api_call GET "${FORGEJO_REPO_API}/issues?type=issues&state=open&limit=5&q=${encoded_title}"; then
      local issue_num
      issue_num=$(echo "$API_RESPONSE_BODY" | python3 -c "
import sys, json
data = json.loads(sys.stdin.read(), strict=False)
for i in data:
    if i['title'] == '''${title}''' and i.get('milestone'):
        print(i['number'])
        break
" 2>/dev/null)

      if [[ -n "$issue_num" ]]; then
        local comment_payload
        comment_payload=$(jq -n --arg body "$comment" '{ body: $body }')
        api_call POST "${FORGEJO_REPO_API}/issues/${issue_num}/comments" "$comment_payload" 2>/dev/null || true
        sleep 0.2

        local close_payload
        close_payload=$(jq -n '{ state: "closed" }')
        if api_call PATCH "${FORGEJO_REPO_API}/issues/${issue_num}" "$close_payload"; then
          log_success "Closed #${issue_num}: ${title}"
        fi
        sleep 0.2
      else
        log_warn "Not found or already closed: ${title}"
      fi
    fi
  done
}

update_plane_statuses() {
  echo "\n========================================"
  echo "  Step 3: Update Plane work items"
  echo "========================================"

  plane_get_project_id "OpenSpec Plugin"

  # Build state cache (avoids repeated API calls)
  typeset -A STATE_CACHE
  local _states
  _states=$(plane_get_states "$PLANE_PROJECT_ID" 2>/dev/null) || true
  if [[ -n "$_states" ]]; then
    local _tmpfile
    _tmpfile=$(mktemp)
    echo "$_states" | jq -r '.[]? | "\(.name)\t\(.id)"' > "$_tmpfile" 2>/dev/null || true
    while IFS=$'\t' read -r sname sid; do
      [[ -n "$sname" ]] && STATE_CACHE[$sname]="$sid"
    done < "$_tmpfile"
    rm -f "$_tmpfile"
  fi

  local plane_state_done="${STATE_CACHE[Done]:-}"
  local plane_state_in_progress="${STATE_CACHE[In Progress]:-}"
  local plane_state_backlog="${STATE_CACHE[Backlog]:-}"

  if [[ -z "$plane_state_done" ]]; then
    log_warn "Could not find 'Done' state — skipping Plane status updates"
    return 0
  fi

  log_info "States cached: Done=${plane_state_done}, In Progress=${plane_state_in_progress}, Backlog=${plane_state_backlog}"
  log_info "Marking completed items as Done..."

  for title in "${(@k)COMPLETED_ITEMS}"; do
    _plane_auth
    if api_call GET "${PLANE_WS_API}/projects/${PLANE_PROJECT_ID}/work-items/?search=${title}&per_page=5"; then
      local item_id
      item_id=$(echo "$API_RESPONSE_BODY" | jq -r --arg t "$title" \
        '(.results[]? // .[]?) | select(.name == $t) | .id' | head -1)

      if [[ -n "$item_id" && "$item_id" != "null" ]]; then
        local payload
        payload=$(jq -n --arg state "$plane_state_done" '{ state: $state }')
        if api_call PATCH "${PLANE_WS_API}/projects/${PLANE_PROJECT_ID}/work-items/${item_id}/" "$payload"; then
          log_success "Updated: ${title} → Done"
        fi
        sleep 0.3
      else
        log_warn "Work item not found: ${title}"
      fi
    fi
  done
}

link_plane_to_forgejo() {
  echo "\n========================================"
  echo "  Step 4: Link Plane items to Forgejo"
  echo "========================================"

  _plane_auth
  if ! api_call GET "${PLANE_WS_API}/projects/${PLANE_PROJECT_ID}/work-items/?per_page=100"; then
    log_error "Could not fetch Plane work items"
    return 1
  fi

  # Write response to temp file for python processing
  echo "$API_RESPONSE_BODY" > /tmp/plane_sync_items.json

  python3 - "${PLANE_API_KEY}" "${PLANE_WS_API}" "${PLANE_PROJECT_ID}" << 'PYEOF'
import sys, json, subprocess, time

API_KEY = sys.argv[1]
WS_API = sys.argv[2]
PID = sys.argv[3]

FORGEJO_MAP = {
    "Add unit tests for SpecParsingService": 59, "Add unit tests for BuiltInValidator": 60,
    "Add unit tests for CliOutputParser": 61, "Add unit tests for VersionSupport": 62,
    "Handle malformed YAML gracefully in ConfigService": 63, "Add CLI command timeout configuration": 64,
    "Add progress indicator for long-running operations": 65, "Improve error messages for API failures": 66,
    "Add keyboard shortcuts for common actions": 67, "Fix dark theme colors in tree renderer": 68,
    "Add spec template selection for Propose": 69, "Add inline quick-fixes for validation issues": 70,
    "Improve validation result formatting": 71, "Add v2.0 schema support": 72,
    "Add integration tests for action lifecycle": 73, "Add notification grouping": 74,
    "Support multi-project workspaces": 75, "Publish to JetBrains Marketplace": 76,
    "Add Ollama integration for local AI": 77, "Add streaming API response support": 78,
    "Add spec search and filtering": 79, "Register OpenSpec file types": 80,
    "Add change diff viewer": 81, "Add OpenSpec file templates": 82,
    "Document all public APIs with Javadoc": 83, "Add CI pipeline": 84,
    "Add tree node tooltips": 85, "Handle concurrent file operations safely": 86,
    "Add config.yaml code completion": 87, "Welcome panel and onboarding experience": 88,
    "Custom plugin icon and visual identity": 89, "Polish artifact pipeline with proper icons and theme support": 90,
    "Generate All button in workflow panel": 91, "Guided first proposal flow": 92,
    "Spec gutter markers for code-to-spec traceability": 93, "Spec coverage panel": 94,
    "Change diff viewer with delta spec preview": 95, "Spec search and filtering in tool window": 96,
    "Engage with OpenSpec/Fission AI for official integration": 97, "Feature comparison matrix maintenance": 98,
}

CANCELLED = "2c99d2e7-0edd-4333-a3d1-60eb312b31e4"

with open("/tmp/plane_sync_items.json") as f:
    data = json.load(f)
items = data.get("results", data) if isinstance(data, dict) else data

linked = 0
for item in items:
    if item.get("state") == CANCELLED:
        continue
    title = item.get("name", "")
    issue_num = FORGEJO_MAP.get(title)
    if not issue_num:
        continue
    if item.get("external_id"):
        continue  # already linked

    payload = json.dumps({"external_id": f"forgejo-issue-{issue_num}", "external_source": "forgejo"})
    result = subprocess.run([
        "/usr/bin/curl", "-s", "-w", "\n%{http_code}", "-X", "PATCH",
        "-H", f"X-API-Key: {API_KEY}", "-H", "Content-Type: application/json",
        "-d", payload, f"{WS_API}/projects/{PID}/work-items/{item['id']}/"
    ], capture_output=True, text=True)
    code = result.stdout.strip().split("\n")[-1]
    if code == "200":
        print(f"  [OK]    Linked: {title} -> Forgejo #{issue_num}")
        linked += 1
    else:
        print(f"  [WARN]  Could not link: {title} (HTTP {code})")
    time.sleep(0.3)

print(f"\n  Linked {linked} items")
PYEOF

  rm -f /tmp/plane_sync_items.json
}

# ============================================================
# Main
# ============================================================

close_forgejo_duplicates
close_forgejo_completed
update_plane_statuses
link_plane_to_forgejo

echo "\n========================================"
log_success "Status sync complete!"
echo ""
echo "  Forgejo: ${FORGEJO_URL}/johnb/intellij-openspec/issues"
echo "  Plane:   ${PLANE_URL}/openspec/projects/"
echo "========================================"
