#!/usr/bin/env zsh
# setup-plane.sh — Create Plane project, labels, cycles, states, modules, pages, views, and work items
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

# Ensure project features are enabled
_plane_auth
api_call PATCH "${PLANE_WS_API}/projects/${PLANE_PROJECT_ID}/" \
  '{"cycle_view": true, "module_view": true, "issue_views_view": true, "page_view": true}' \
  && log_success "Project features enabled (cycles, modules, views, pages)" || true

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
# 4. Configure workflow states
# ------------------------------------------------------------------
log_info "Configuring workflow states..."
state_count=$(jq length "${DATA_DIR}/plane-states.json")
for i in $(seq 0 $((state_count - 1))); do
  name=$(jq -r ".[$i].name" "${DATA_DIR}/plane-states.json")
  group=$(jq -r ".[$i].group" "${DATA_DIR}/plane-states.json")
  color=$(jq -r ".[$i].color" "${DATA_DIR}/plane-states.json")
  sequence=$(jq -r ".[$i].sequence" "${DATA_DIR}/plane-states.json")
  plane_create_state "$PLANE_PROJECT_ID" "$name" "$group" "$color" "$sequence" || true
done
echo ""

# ------------------------------------------------------------------
# 5. Create modules
# ------------------------------------------------------------------
log_info "Creating modules..."
mod_count=$(jq length "${DATA_DIR}/plane-modules.json")
for i in $(seq 0 $((mod_count - 1))); do
  name=$(jq -r ".[$i].name" "${DATA_DIR}/plane-modules.json")
  desc=$(jq -r ".[$i].description" "${DATA_DIR}/plane-modules.json")
  plane_create_module "$PLANE_PROJECT_ID" "$name" "$desc" || true
done
echo ""

# ------------------------------------------------------------------
# 6. Create pages (manual — Pages API not available in this Plane version)
# ------------------------------------------------------------------
log_info "Pages: Create manually in Plane UI (API endpoint returns 404)"
PAGES_DIR="${DATA_DIR}/plane-pages"
for page_file in "${PAGES_DIR}"/*.md; do
  [[ -f "$page_file" ]] || continue
  page_name=$(head -1 "$page_file" | sed 's/^# *//')
  log_info "  → ${page_name} (content in ${page_file})"
done
echo ""

# ------------------------------------------------------------------
# 7. Create views (manual — Views API not available in this Plane version)
# ------------------------------------------------------------------
log_info "Views: Create manually in Plane UI (API endpoint returns 404)"
view_count=$(jq length "${DATA_DIR}/plane-views.json")
for i in $(seq 0 $((view_count - 1))); do
  name=$(jq -r ".[$i].name" "${DATA_DIR}/plane-views.json")
  desc=$(jq -r ".[$i].description" "${DATA_DIR}/plane-views.json")
  log_info "  → ${name}: ${desc}"
done
echo ""

# ------------------------------------------------------------------
# 8. Build lookup caches
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

# State cache
typeset -A PLANE_STATE_CACHE
local _states
_states=$(plane_get_states "$PLANE_PROJECT_ID" 2>/dev/null) || true
if [[ -n "$_states" ]]; then
  _tmpfile=$(mktemp)
  echo "$_states" | jq -r '.[]? | "\(.name)\t\(.id)"' > "$_tmpfile" 2>/dev/null || true
  while IFS=$'\t' read -r sname sid; do
    [[ -n "$sname" ]] && PLANE_STATE_CACHE[$sname]="$sid"
  done < "$_tmpfile"
  rm -f "$_tmpfile"
fi

# Module cache
typeset -A PLANE_MODULE_CACHE
_plane_auth
if api_call GET "${PLANE_WS_API}/projects/${PLANE_PROJECT_ID}/modules/"; then
  _tmpfile=$(mktemp)
  echo "$API_RESPONSE_BODY" | jq -r '(.results[]? // .[]?) | "\(.name)\t\(.id)"' > "$_tmpfile" 2>/dev/null || true
  while IFS=$'\t' read -r mname mid; do
    [[ -n "$mname" ]] && PLANE_MODULE_CACHE[$mname]="$mid"
  done < "$_tmpfile"
  rm -f "$_tmpfile"
fi

log_success "Caches built (${#PLANE_LABEL_CACHE[@]} labels, ${#PLANE_CYCLE_CACHE[@]} cycles, ${#PLANE_STATE_CACHE[@]} states, ${#PLANE_MODULE_CACHE[@]} modules)"
echo ""

# ------------------------------------------------------------------
# 9. Create work items (idempotent — skips existing by title)
# ------------------------------------------------------------------
log_info "Creating work items..."

# Build work item title cache to avoid duplicates
typeset -A PLANE_WORKITEM_CACHE
_plane_auth
if api_call GET "${PLANE_WS_API}/projects/${PLANE_PROJECT_ID}/work-items/?per_page=200"; then
  _tmpfile=$(mktemp)
  echo "$API_RESPONSE_BODY" | jq -r '(.results[]? // .[]?) | "\(.name)\t\(.id)"' > "$_tmpfile" 2>/dev/null || true
  while IFS=$'\t' read -r wname wid; do
    [[ -n "$wname" ]] && PLANE_WORKITEM_CACHE[$wname]="$wid"
  done < "$_tmpfile"
  rm -f "$_tmpfile"
fi
log_info "  ${#PLANE_WORKITEM_CACHE[@]} existing work items found"

item_count=$(jq length "${DATA_DIR}/plane-workitems.json")
for i in $(seq 0 $((item_count - 1))); do
  title=$(jq -r ".[$i].title" "${DATA_DIR}/plane-workitems.json")

  # Skip if already exists
  if [[ -n "${PLANE_WORKITEM_CACHE[$title]:-}" ]]; then
    log_warn "Work item: '${title}' already exists — skipping"
    continue
  fi
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

  # Resolve module
  module_ref=$(jq -r ".[$i].module // empty" "${DATA_DIR}/plane-workitems.json")

  plane_create_work_item "$PLANE_PROJECT_ID" "$title" "$description" "$label_ids" "$cycle_id" "$priority" || true

  # Add to module if specified (need to look up work item ID by name)
  if [[ -n "$module_ref" ]]; then
    local mod_id="${PLANE_MODULE_CACHE[$module_ref]:-}"
    if [[ -n "$mod_id" ]]; then
      # Get the work item ID we just created (from API_RESPONSE_BODY if created, or look up)
      local wi_id
      wi_id=$(echo "${API_RESPONSE_BODY:-}" | jq -r '.id // empty' 2>/dev/null)
      if [[ -n "$wi_id" && "$wi_id" != "null" ]]; then
        plane_add_to_module "$PLANE_PROJECT_ID" "$mod_id" "$wi_id"
      fi
    fi
  fi
done
echo ""

# ------------------------------------------------------------------
# 10. Migrate priority labels to native priority
# ------------------------------------------------------------------
log_info "Migrating priority labels to native priority field..."

# Map priority label names to Plane native priority values
typeset -A PRIORITY_MAP
PRIORITY_MAP["priority:high"]="high"
PRIORITY_MAP["priority:medium"]="medium"
PRIORITY_MAP["priority:low"]="low"

# Fetch all work items
_plane_auth
if api_call GET "${PLANE_WS_API}/projects/${PLANE_PROJECT_ID}/work-items/?per_page=200"; then
  _tmpfile=$(mktemp)
  echo "$API_RESPONSE_BODY" | jq -r '.results[]? // .[]? | "\(.id)\t\(.priority)\t\(.labels | join(","))"' > "$_tmpfile" 2>/dev/null || true

  migrated=0
  while IFS=$'\t' read -r item_id current_priority label_ids_csv; do
    [[ -z "$item_id" ]] && continue
    # Skip if already has a non-none priority
    [[ "$current_priority" != "none" && -n "$current_priority" ]] && continue

    # Check if any labels match priority labels
    new_priority=""
    labels_to_remove=()
    for pname pval in "${(@kv)PRIORITY_MAP}"; do
      plabel_id="${PLANE_LABEL_CACHE[$pname]:-}"
      if [[ -n "$plabel_id" && "$label_ids_csv" == *"$plabel_id"* ]]; then
        new_priority="$pval"
        labels_to_remove+=("$plabel_id")
      fi
    done

    if [[ -n "$new_priority" ]]; then
      # Update priority
      _plane_auth
      local patch_payload
      patch_payload=$(jq -n --arg p "$new_priority" '{ priority: $p }')
      if api_call PATCH "${PLANE_WS_API}/projects/${PLANE_PROJECT_ID}/work-items/${item_id}/" "$patch_payload"; then
        log_success "  Priority migrated: ${item_id} → ${new_priority}"
        ((migrated++))
      fi
    fi
  done < "$_tmpfile"
  rm -f "$_tmpfile"
  log_success "Priority migration complete (${migrated} items updated)"
fi
echo ""

# ------------------------------------------------------------------
# 11. Enable estimates (manual — not available via API v1)
# ------------------------------------------------------------------
log_info "Estimates: Configure manually in Plane UI"
log_info "  → Project Settings > Estimates > Enable > Fibonacci (1, 2, 3, 5, 8, 13)"
echo ""

echo "========================================"
log_success "Plane setup complete!"
echo ""
echo "  Project: ${PLANE_URL}/${PLANE_WORKSPACE}/projects/"
echo ""
echo "  Manual steps (not available via API v1):"
echo "    - Enable estimates: Project Settings > Estimates > Fibonacci (1, 2, 3, 5, 8, 13)"
echo "    - Create pages from: ${DATA_DIR}/plane-pages/*.md"
echo "    - Create views from: ${DATA_DIR}/plane-views.json"
echo "========================================"
