#!/usr/bin/env zsh
# forgejo-api.sh — Forgejo API helpers for wiki, labels, milestones, issues

# Requires common.sh to be sourced first
# Expects FORGEJO_TOKEN and FORGEJO_URL from environment

FORGEJO_OWNER="johnb"
FORGEJO_REPO="intellij-openspec"
FORGEJO_API="${FORGEJO_URL}/api/v1"
FORGEJO_REPO_API="${FORGEJO_API}/repos/${FORGEJO_OWNER}/${FORGEJO_REPO}"

_forgejo_auth() {
  API_AUTH_HEADER="Authorization: token ${FORGEJO_TOKEN}"
}

# ---------------------------------------------------------------------------
# Wiki
# ---------------------------------------------------------------------------

# forgejo_create_wiki_page TITLE CONTENT_FILE
forgejo_create_wiki_page() {
  local title="$1"
  local content_file="$2"
  _forgejo_auth

  local content
  content=$(cat "$content_file")

  local payload
  payload=$(jq -n \
    --arg title "$title" \
    --arg content "$content" \
    '{ title: $title, content_base64: ($content | @base64) }')

  if api_call POST "${FORGEJO_REPO_API}/wiki/new" "$payload"; then
    log_success "Wiki: created '${title}'"
  elif [[ "$API_RESPONSE_CODE" == "400" || "$API_RESPONSE_CODE" == "409" || "$API_RESPONSE_CODE" == "422" ]]; then
    # Forgejo returns 400 (not 409) for "wiki page already exists"
    log_warn "Wiki: '${title}' already exists — updating"
    forgejo_update_wiki_page "$title" "$content_file"
  else
    log_error "Wiki: failed to create '${title}'"
    return 1
  fi
}

# forgejo_update_wiki_page TITLE CONTENT_FILE
forgejo_update_wiki_page() {
  local title="$1"
  local content_file="$2"
  _forgejo_auth

  local content
  content=$(cat "$content_file")

  # Forgejo wiki page names use dashes for spaces in URLs
  local page_name
  page_name=$(echo "$title" | tr ' ' '-')

  local payload
  payload=$(jq -n \
    --arg title "$title" \
    --arg content "$content" \
    '{ title: $title, content_base64: ($content | @base64) }')

  if api_call PATCH "${FORGEJO_REPO_API}/wiki/page/${page_name}" "$payload"; then
    log_success "Wiki: updated '${title}'"
  else
    log_error "Wiki: failed to update '${title}'"
    return 1
  fi
}

# ---------------------------------------------------------------------------
# Labels
# ---------------------------------------------------------------------------

# forgejo_create_label NAME COLOR DESCRIPTION
# COLOR should be hex without # (e.g. "d73a4a")
forgejo_create_label() {
  local name="$1"
  local color="$2"
  local description="$3"
  _forgejo_auth

  # Check if label already exists
  if api_call GET "${FORGEJO_REPO_API}/labels?limit=50"; then
    local existing
    existing=$(echo "$API_RESPONSE_BODY" | jq -r --arg n "$name" '.[] | select(.name == $n) | .id')
    if [[ -n "$existing" ]]; then
      log_warn "Label: '${name}' already exists — skipping"
      return 0
    fi
  fi

  local payload
  payload=$(jq -n \
    --arg name "$name" \
    --arg color "#${color}" \
    --arg desc "$description" \
    '{ name: $name, color: $color, description: $desc }')

  if api_call POST "${FORGEJO_REPO_API}/labels" "$payload"; then
    log_success "Label: created '${name}'"
  else
    log_error "Label: failed to create '${name}'"
    return 1
  fi
}

# forgejo_get_label_id NAME → prints label ID
forgejo_get_label_id() {
  local name="$1"
  _forgejo_auth

  if api_call GET "${FORGEJO_REPO_API}/labels?limit=50"; then
    local label_id
    label_id=$(echo "$API_RESPONSE_BODY" | jq -r --arg n "$name" '.[] | select(.name == $n) | .id')
    if [[ -n "$label_id" && "$label_id" != "null" ]]; then
      echo "$label_id"
      return 0
    fi
  fi
  log_error "Label: could not find ID for '${name}'"
  return 1
}

# ---------------------------------------------------------------------------
# Milestones
# ---------------------------------------------------------------------------

# forgejo_create_milestone TITLE DESCRIPTION DUE_DATE
# DUE_DATE in ISO 8601 format (e.g. "2026-04-01T00:00:00Z")
forgejo_create_milestone() {
  local title="$1"
  local description="$2"
  local due_date="$3"
  _forgejo_auth

  # Check if milestone already exists
  if api_call GET "${FORGEJO_REPO_API}/milestones?limit=50"; then
    local existing
    existing=$(echo "$API_RESPONSE_BODY" | jq -r --arg t "$title" '.[] | select(.title == $t) | .id')
    if [[ -n "$existing" ]]; then
      log_warn "Milestone: '${title}' already exists — skipping"
      return 0
    fi
  fi

  local payload
  payload=$(jq -n \
    --arg title "$title" \
    --arg desc "$description" \
    --arg due "$due_date" \
    '{ title: $title, description: $desc, due_on: $due }')

  if api_call POST "${FORGEJO_REPO_API}/milestones" "$payload"; then
    log_success "Milestone: created '${title}'"
  else
    log_error "Milestone: failed to create '${title}'"
    return 1
  fi
}

# forgejo_get_milestone_id TITLE → prints milestone ID
forgejo_get_milestone_id() {
  local title="$1"
  _forgejo_auth

  if api_call GET "${FORGEJO_REPO_API}/milestones?limit=50"; then
    local ms_id
    ms_id=$(echo "$API_RESPONSE_BODY" | jq -r --arg t "$title" '.[] | select(.title == $t) | .id')
    if [[ -n "$ms_id" && "$ms_id" != "null" ]]; then
      echo "$ms_id"
      return 0
    fi
  fi
  log_error "Milestone: could not find ID for '${title}'"
  return 1
}

# ---------------------------------------------------------------------------
# Issues
# ---------------------------------------------------------------------------

# forgejo_create_issue TITLE BODY LABEL_IDS_JSON MILESTONE_ID
# LABEL_IDS_JSON is a JSON array string e.g. "[1,2,3]"
forgejo_create_issue() {
  local title="$1"
  local body="$2"
  local label_ids="$3"
  local milestone_id="$4"
  _forgejo_auth

  # Check if issue with same title already exists
  local encoded_title
  encoded_title=$(printf '%s' "$title" | jq -sRr @uri)
  if api_call GET "${FORGEJO_REPO_API}/issues?type=issues&state=open&limit=5&q=${encoded_title}"; then
    local match
    match=$(echo "$API_RESPONSE_BODY" | jq -r --arg t "$title" '.[] | select(.title == $t) | .number' 2>/dev/null | head -1)
    if [[ -n "$match" ]]; then
      log_warn "Issue #${match}: '${title}' already exists — skipping"
      return 0
    fi
  fi

  local payload
  payload=$(jq -n \
    --arg title "$title" \
    --arg body "$body" \
    --argjson labels "$label_ids" \
    --argjson ms "$milestone_id" \
    '{ title: $title, body: $body, labels: $labels, milestone: $ms }')

  if api_call POST "${FORGEJO_REPO_API}/issues" "$payload"; then
    local issue_num
    issue_num=$(echo "$API_RESPONSE_BODY" | jq -r '.number // empty' 2>/dev/null || echo "")
    log_success "Issue #${issue_num}: '${title}'"
  else
    log_error "Issue: failed to create '${title}'"
    return 1
  fi
}
