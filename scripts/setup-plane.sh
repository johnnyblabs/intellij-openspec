#!/usr/bin/env zsh
# setup-plane.sh — Create Plane project, labels, cycles, and work items
#
# Idempotent: skips resources that already exist (409/422)

set -euo pipefail

SCRIPT_DIR="${0:A:h}"
source "${SCRIPT_DIR}/lib/common.sh"

check_deps
load_env

source "${SCRIPT_DIR}/lib/plane-api.sh"

DATA_DIR="${SCRIPT_DIR}/docs/data"

echo ""
echo "========================================"
echo "  OpenSpec — Plane Setup"
echo "========================================"
echo ""

# ------------------------------------------------------------------
# 1. Create project
# ------------------------------------------------------------------
log_info "Creating project..."
plane_create_project "OpenSpec Plugin" \
  "IntelliJ IDEA plugin for OpenSpec spec-driven development framework" \
  "OSP"
echo ""

if [[ -z "${PLANE_PROJECT_ID:-}" || "$PLANE_PROJECT_ID" == "null" ]]; then
  log_error "Could not create or find project. Aborting."
  exit 1
fi

# Ensure cycles are enabled on the project
_plane_auth
api_call PATCH "${PLANE_WS_API}/projects/${PLANE_PROJECT_ID}/" \
  '{"cycle_view": true}' && log_success "Cycles enabled on project" || true

# ------------------------------------------------------------------
# 2. Create labels
# ------------------------------------------------------------------
log_info "Creating labels..."
label_count=$(jq length "${DATA_DIR}/labels.json")
for i in $(seq 0 $((label_count - 1))); do
  name=$(jq -r ".[$i].name" "${DATA_DIR}/labels.json")
  color=$(jq -r ".[$i].color" "${DATA_DIR}/labels.json")
  plane_create_label "$PLANE_PROJECT_ID" "$name" "$color" || true
done
echo ""

# ------------------------------------------------------------------
# 3. Create cycles (mapped from milestones)
# ------------------------------------------------------------------
log_info "Creating cycles..."

# Define cycle date ranges based on milestone due dates
typeset -A CYCLE_START
CYCLE_START["v0.1.0 - Initial Release"]="2026-03-01"
CYCLE_START["v0.2.0 - Polish & Testing"]="2026-04-02"
CYCLE_START["v0.3.0 - Advanced Features"]="2026-05-02"

ms_count=$(jq length "${DATA_DIR}/milestones.json")
for i in $(seq 0 $((ms_count - 1))); do
  title=$(jq -r ".[$i].title" "${DATA_DIR}/milestones.json")
  desc=$(jq -r ".[$i].description" "${DATA_DIR}/milestones.json")
  due_date=$(jq -r ".[$i].due_date" "${DATA_DIR}/milestones.json")

  # Extract just the date part (YYYY-MM-DD)
  end_date="${due_date%%T*}"
  start_date="${CYCLE_START[$title]:-2026-03-01}"

  plane_create_cycle "$PLANE_PROJECT_ID" "$title" "$start_date" "$end_date" "$desc" || true
done
echo ""

# ------------------------------------------------------------------
# 4. Build lookup caches
# ------------------------------------------------------------------
log_info "Building lookup caches..."

# Label cache — use temp file to avoid zsh subshell-in-pipe losing assignments
typeset -A PLANE_LABEL_CACHE
_plane_auth
if api_call GET "${PLANE_WS_API}/projects/${PLANE_PROJECT_ID}/labels/"; then
  _tmpfile=$(mktemp)
  echo "$API_RESPONSE_BODY" | jq -r '(.results[]? // .[]?) | "\(.name)\t\(.id)"' > "$_tmpfile" 2>/dev/null || true
  while IFS=$'\t' read -r lname lid; do
    [[ -n "$lname" ]] && PLANE_LABEL_CACHE[$lname]="$lid"
  done < "$_tmpfile"
  rm -f "$_tmpfile"
fi

# Cycle cache
typeset -A PLANE_CYCLE_CACHE
if api_call GET "${PLANE_WS_API}/projects/${PLANE_PROJECT_ID}/cycles/"; then
  _tmpfile=$(mktemp)
  echo "$API_RESPONSE_BODY" | jq -r '(.results[]? // .[]?) | "\(.name)\t\(.id)"' > "$_tmpfile" 2>/dev/null || true
  while IFS=$'\t' read -r cname cid; do
    [[ -n "$cname" ]] && PLANE_CYCLE_CACHE[$cname]="$cid"
  done < "$_tmpfile"
  rm -f "$_tmpfile"
fi

log_success "Caches built (${#PLANE_LABEL_CACHE[@]} labels, ${#PLANE_CYCLE_CACHE[@]} cycles)"
echo ""

# ------------------------------------------------------------------
# 5. Create work items
# ------------------------------------------------------------------
log_info "Creating work items..."

item_count=$(jq length "${DATA_DIR}/plane-workitems.json")
for i in $(seq 0 $((item_count - 1))); do
  title=$(jq -r ".[$i].title" "${DATA_DIR}/plane-workitems.json")
  description=$(jq -r ".[$i].description" "${DATA_DIR}/plane-workitems.json")
  priority=$(jq -r ".[$i].priority" "${DATA_DIR}/plane-workitems.json")
  cycle_ref=$(jq -r ".[$i].cycle" "${DATA_DIR}/plane-workitems.json")

  # Resolve label IDs
  label_ids="[]"
  label_names=("${(@f)$(jq -r ".[$i].labels[]" "${DATA_DIR}/plane-workitems.json" 2>/dev/null)}") 2>/dev/null || label_names=()
  if [[ ${#label_names[@]} -gt 0 ]]; then
    ids=()
    for lname in "${label_names[@]}"; do
      [[ -z "$lname" ]] && continue
      lid="${PLANE_LABEL_CACHE[$lname]:-}"
      if [[ -n "$lid" ]]; then
        ids+=("\"$lid\"")
      fi
    done
    if [[ ${#ids[@]} -gt 0 ]]; then
      label_ids="[$(IFS=,; echo "${ids[*]}")]"
    fi
  fi

  # Resolve cycle ID
  cycle_id=""
  if [[ -n "$cycle_ref" && "$cycle_ref" != "null" ]]; then
    cycle_id="${PLANE_CYCLE_CACHE[$cycle_ref]:-}"
  fi

  plane_create_work_item "$PLANE_PROJECT_ID" "$title" "$description" "$label_ids" "$cycle_id" "$priority" || true
done

echo ""
echo "========================================"
log_success "Plane setup complete!"
echo ""
echo "  Project: ${PLANE_URL}/${PLANE_WORKSPACE}/projects/"
echo "========================================"
