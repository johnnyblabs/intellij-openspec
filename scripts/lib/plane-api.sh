#!/usr/bin/env zsh
# plane-api.sh — Plane API helpers for projects, labels, cycles, states, modules, pages, views, work items

# Requires common.sh to be sourced first
# Expects PLANE_API_KEY, PLANE_URL, PLANE_WORKSPACE from environment

PLANE_API="${PLANE_URL}/api/v1"
PLANE_WS_API="${PLANE_API}/workspaces/${PLANE_WORKSPACE}"

_plane_auth() {
  API_AUTH_HEADER="X-API-Key: ${PLANE_API_KEY}"
}

# ---------------------------------------------------------------------------
# Projects
# ---------------------------------------------------------------------------

# plane_create_project NAME DESCRIPTION IDENTIFIER
# Returns project ID in PLANE_PROJECT_ID
plane_create_project() {
  local name="$1"
  local description="$2"
  local identifier="$3"
  _plane_auth

  local payload
  payload=$(jq -n \
    --arg name "$name" \
    --arg desc "$description" \
    --arg id "$identifier" \
    '{ name: $name, description: $desc, identifier: $id, network: 2, cycle_view: true }')

  if api_call POST "${PLANE_WS_API}/projects/" "$payload"; then
    PLANE_PROJECT_ID=$(echo "$API_RESPONSE_BODY" | jq -r '.id')
    log_success "Project: created '${name}' (${PLANE_PROJECT_ID})"
  elif [[ "$API_RESPONSE_CODE" == "409" || "$API_RESPONSE_CODE" == "422" ]]; then
    log_warn "Project: '${name}' may already exist — looking up"
    plane_get_project_id "$name"
  else
    log_error "Project: failed to create '${name}'"
    return 1
  fi
}

# plane_get_project_id NAME → sets PLANE_PROJECT_ID
plane_get_project_id() {
  local name="$1"
  _plane_auth

  if api_call GET "${PLANE_WS_API}/projects/"; then
    PLANE_PROJECT_ID=$(echo "$API_RESPONSE_BODY" | jq -r --arg n "$name" \
      '.results[]? // .[]? | select(.name == $n) | .id' | head -1)
    if [[ -n "$PLANE_PROJECT_ID" && "$PLANE_PROJECT_ID" != "null" ]]; then
      log_success "Project: found '${name}' (${PLANE_PROJECT_ID})"
      return 0
    fi
  fi
  log_error "Project: could not find '${name}'"
  return 1
}

# ---------------------------------------------------------------------------
# Labels
# ---------------------------------------------------------------------------

# plane_create_label PROJECT_ID NAME COLOR
plane_create_label() {
  local project_id="$1"
  local name="$2"
  local color="$3"
  _plane_auth

  local payload
  payload=$(jq -n \
    --arg name "$name" \
    --arg color "#${color}" \
    '{ name: $name, color: $color }')

  if api_call POST "${PLANE_WS_API}/projects/${project_id}/labels/" "$payload"; then
    log_success "Label: created '${name}'"
  elif [[ "$API_RESPONSE_CODE" == "409" || "$API_RESPONSE_CODE" == "422" ]]; then
    log_warn "Label: '${name}' already exists — skipping"
  else
    log_error "Label: failed to create '${name}'"
    return 1
  fi
}

# plane_get_label_id PROJECT_ID NAME → prints label ID
plane_get_label_id() {
  local project_id="$1"
  local name="$2"
  _plane_auth

  if api_call GET "${PLANE_WS_API}/projects/${project_id}/labels/"; then
    local label_id
    label_id=$(echo "$API_RESPONSE_BODY" | jq -r --arg n "$name" \
      '.results[]? // .[]? | select(.name == $n) | .id' | head -1)
    if [[ -n "$label_id" && "$label_id" != "null" ]]; then
      echo "$label_id"
      return 0
    fi
  fi
  return 1
}

# ---------------------------------------------------------------------------
# Cycles
# ---------------------------------------------------------------------------

# plane_create_cycle PROJECT_ID NAME START_DATE END_DATE DESCRIPTION
plane_create_cycle() {
  local project_id="$1"
  local name="$2"
  local start_date="$3"
  local end_date="$4"
  local description="$5"
  _plane_auth

  # Check if cycle already exists
  local existing
  existing=$(plane_get_cycle_id "$project_id" "$name" 2>/dev/null)
  if [[ -n "$existing" ]]; then
    log_warn "Cycle: '${name}' already exists — skipping"
    return 0
  fi

  local payload
  payload=$(jq -n \
    --arg name "$name" \
    --arg pid "$project_id" \
    --arg start "$start_date" \
    --arg end "$end_date" \
    --arg desc "$description" \
    '{ name: $name, project_id: $pid, start_date: $start, end_date: $end, description: $desc }')

  if api_call POST "${PLANE_WS_API}/projects/${project_id}/cycles/" "$payload"; then
    log_success "Cycle: created '${name}'"
  else
    log_error "Cycle: failed to create '${name}'"
    return 1
  fi
}

# plane_get_cycle_id PROJECT_ID NAME → prints cycle ID
plane_get_cycle_id() {
  local project_id="$1"
  local name="$2"
  _plane_auth

  if api_call GET "${PLANE_WS_API}/projects/${project_id}/cycles/"; then
    local cycle_id
    cycle_id=$(echo "$API_RESPONSE_BODY" | jq -r --arg n "$name" \
      '.results[]? // .[]? | select(.name == $n) | .id' | head -1)
    if [[ -n "$cycle_id" && "$cycle_id" != "null" ]]; then
      echo "$cycle_id"
      return 0
    fi
  fi
  return 1
}

# ---------------------------------------------------------------------------
# States
# ---------------------------------------------------------------------------

# plane_get_states PROJECT_ID → prints JSON array of states
plane_get_states() {
  local project_id="$1"
  _plane_auth

  if api_call GET "${PLANE_WS_API}/projects/${project_id}/states/"; then
    echo "$API_RESPONSE_BODY" | jq '[.results[]? // .[]]'
    return 0
  fi
  return 1
}

# plane_create_state PROJECT_ID NAME GROUP COLOR SEQUENCE
# GROUP: "backlog" | "unstarted" | "started" | "completed" | "cancelled"
plane_create_state() {
  local project_id="$1"
  local name="$2"
  local group="$3"
  local color="$4"
  local sequence="$5"
  _plane_auth

  # Check if state already exists
  local existing
  existing=$(plane_get_states "$project_id" 2>/dev/null | jq -r --arg n "$name" \
    '.[]? | select(.name == $n) | .id' | head -1)
  if [[ -n "$existing" && "$existing" != "null" ]]; then
    log_warn "State: '${name}' already exists — skipping"
    return 0
  fi

  local payload
  payload=$(jq -n \
    --arg name "$name" \
    --arg group "$group" \
    --arg color "$color" \
    --argjson seq "$sequence" \
    '{ name: $name, group: $group, color: $color, sequence: $seq }')

  if api_call POST "${PLANE_WS_API}/projects/${project_id}/states/" "$payload"; then
    log_success "State: created '${name}' (${group})"
  else
    log_error "State: failed to create '${name}'"
    return 1
  fi
}

# plane_get_state_id PROJECT_ID NAME → prints state ID
plane_get_state_id() {
  local project_id="$1"
  local name="$2"

  local state_id
  state_id=$(plane_get_states "$project_id" 2>/dev/null | jq -r --arg n "$name" \
    '.[]? | select(.name == $n) | .id' | head -1)
  if [[ -n "$state_id" && "$state_id" != "null" ]]; then
    echo "$state_id"
    return 0
  fi
  return 1
}

# ---------------------------------------------------------------------------
# Modules
# ---------------------------------------------------------------------------

# plane_create_module PROJECT_ID NAME DESCRIPTION
plane_create_module() {
  local project_id="$1"
  local name="$2"
  local description="$3"
  _plane_auth

  # Check if module already exists
  local existing
  existing=$(plane_get_module_id "$project_id" "$name" 2>/dev/null)
  if [[ -n "$existing" ]]; then
    log_warn "Module: '${name}' already exists — skipping"
    return 0
  fi

  local payload
  payload=$(jq -n \
    --arg name "$name" \
    --arg desc "$description" \
    '{ name: $name, description: $desc }')

  if api_call POST "${PLANE_WS_API}/projects/${project_id}/modules/" "$payload"; then
    log_success "Module: created '${name}'"
  else
    log_error "Module: failed to create '${name}'"
    return 1
  fi
}

# plane_get_module_id PROJECT_ID NAME → prints module ID
plane_get_module_id() {
  local project_id="$1"
  local name="$2"
  _plane_auth

  if api_call GET "${PLANE_WS_API}/projects/${project_id}/modules/"; then
    local module_id
    module_id=$(echo "$API_RESPONSE_BODY" | jq -r --arg n "$name" \
      '.results[]? // .[]? | select(.name == $n) | .id' | head -1)
    if [[ -n "$module_id" && "$module_id" != "null" ]]; then
      echo "$module_id"
      return 0
    fi
  fi
  return 1
}

# plane_add_to_module PROJECT_ID MODULE_ID ISSUE_ID
plane_add_to_module() {
  local project_id="$1"
  local module_id="$2"
  local issue_id="$3"
  _plane_auth

  local payload
  payload=$(jq -n --arg id "$issue_id" '{ issues: [$id] }')

  if api_call POST "${PLANE_WS_API}/projects/${project_id}/modules/${module_id}/module-issues/" "$payload"; then
    log_success "  → added to module"
  else
    log_warn "  → could not add to module (may already be linked)"
  fi
}

# ---------------------------------------------------------------------------
# Pages
# ---------------------------------------------------------------------------

# plane_create_page PROJECT_ID NAME DESCRIPTION_HTML
plane_create_page() {
  local project_id="$1"
  local name="$2"
  local description_html="$3"
  _plane_auth

  local payload
  payload=$(jq -n \
    --arg name "$name" \
    --arg desc "$description_html" \
    '{ name: $name, description_html: $desc }')

  if api_call POST "${PLANE_WS_API}/projects/${project_id}/pages/" "$payload"; then
    log_success "Page: created '${name}'"
  elif [[ "$API_RESPONSE_CODE" == "409" || "$API_RESPONSE_CODE" == "422" ]]; then
    log_warn "Page: '${name}' already exists — skipping"
  else
    log_error "Page: failed to create '${name}'"
    return 1
  fi
}

# ---------------------------------------------------------------------------
# Views
# ---------------------------------------------------------------------------

# plane_create_view PROJECT_ID NAME DESCRIPTION FILTERS_JSON
# FILTERS_JSON is a JSON object with filter criteria
plane_create_view() {
  local project_id="$1"
  local name="$2"
  local description="$3"
  local filters_json="$4"
  _plane_auth

  local payload
  payload=$(jq -n \
    --arg name "$name" \
    --arg desc "$description" \
    --argjson filters "$filters_json" \
    '{ name: $name, description: $desc, filters: $filters }')

  if api_call POST "${PLANE_WS_API}/projects/${project_id}/views/" "$payload"; then
    log_success "View: created '${name}'"
  elif [[ "$API_RESPONSE_CODE" == "409" || "$API_RESPONSE_CODE" == "422" ]]; then
    log_warn "View: '${name}' already exists — skipping"
  else
    log_error "View: failed to create '${name}'"
    return 1
  fi
}

# ---------------------------------------------------------------------------
# Work Items (Issues in Plane)
# ---------------------------------------------------------------------------

# plane_create_work_item PROJECT_ID TITLE DESCRIPTION LABEL_IDS_JSON CYCLE_ID PRIORITY
# LABEL_IDS_JSON is a JSON array of UUIDs e.g. '["uuid1","uuid2"]'
# PRIORITY: "urgent" | "high" | "medium" | "low" | "none"
plane_create_work_item() {
  local project_id="$1"
  local title="$2"
  local description="$3"
  local label_ids="$4"
  local cycle_id="$5"
  local priority="$6"
  _plane_auth

  local payload
  payload=$(jq -n \
    --arg title "$title" \
    --arg desc "$description" \
    --argjson labels "$label_ids" \
    --arg priority "$priority" \
    '{ name: $title, description_html: ("<p>" + $desc + "</p>"), labels: $labels, priority: $priority }')

  if api_call POST "${PLANE_WS_API}/projects/${project_id}/work-items/" "$payload"; then
    log_success "Work item: '${title}'"

    # If cycle_id provided, add to cycle
    if [[ -n "$cycle_id" && "$cycle_id" != "null" && "$cycle_id" != "" ]]; then
      local item_id
      item_id=$(echo "$API_RESPONSE_BODY" | jq -r '.id')
      if [[ -n "$item_id" && "$item_id" != "null" ]]; then
        plane_add_to_cycle "$project_id" "$cycle_id" "$item_id"
      fi
    fi
  elif [[ "$API_RESPONSE_CODE" == "409" || "$API_RESPONSE_CODE" == "422" ]]; then
    log_warn "Work item: '${title}' may already exist — skipping"
  else
    log_error "Work item: failed to create '${title}'"
    return 1
  fi
}

# plane_add_to_cycle PROJECT_ID CYCLE_ID ISSUE_ID
plane_add_to_cycle() {
  local project_id="$1"
  local cycle_id="$2"
  local issue_id="$3"
  _plane_auth

  local payload
  payload=$(jq -n --arg id "$issue_id" '{ issues: [$id] }')

  if api_call POST "${PLANE_WS_API}/projects/${project_id}/cycles/${cycle_id}/cycle-issues/" "$payload"; then
    log_success "  → added to cycle"
  else
    log_warn "  → could not add to cycle (may already be linked)"
  fi
}
