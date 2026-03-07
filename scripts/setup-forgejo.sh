#!/usr/bin/env zsh
# setup-forgejo.sh — Push wiki, labels, milestones, and issues to Forgejo
#
# Idempotent: skips resources that already exist (409/422)

set -euo pipefail

SCRIPT_DIR="${0:A:h}"
source "${SCRIPT_DIR}/lib/common.sh"

check_deps
load_env

source "${SCRIPT_DIR}/lib/forgejo-api.sh"

WIKI_DIR="${SCRIPT_DIR}/docs/wiki"
DATA_DIR="${SCRIPT_DIR}/docs/data"

echo ""
echo "========================================"
echo "  OpenSpec — Forgejo Setup"
echo "========================================"
echo ""

# ------------------------------------------------------------------
# 1. Create labels
# ------------------------------------------------------------------
log_info "Creating labels..."
label_count=$(jq length "${DATA_DIR}/labels.json")
for i in $(seq 0 $((label_count - 1))); do
  name=$(jq -r ".[$i].name" "${DATA_DIR}/labels.json")
  color=$(jq -r ".[$i].color" "${DATA_DIR}/labels.json")
  desc=$(jq -r ".[$i].description" "${DATA_DIR}/labels.json")
  forgejo_create_label "$name" "$color" "$desc" || true
done
echo ""

# ------------------------------------------------------------------
# 2. Create milestones
# ------------------------------------------------------------------
log_info "Creating milestones..."
ms_count=$(jq length "${DATA_DIR}/milestones.json")
for i in $(seq 0 $((ms_count - 1))); do
  title=$(jq -r ".[$i].title" "${DATA_DIR}/milestones.json")
  desc=$(jq -r ".[$i].description" "${DATA_DIR}/milestones.json")
  due=$(jq -r ".[$i].due_date" "${DATA_DIR}/milestones.json")
  forgejo_create_milestone "$title" "$desc" "$due" || true
done
echo ""

# ------------------------------------------------------------------
# 3. Push wiki pages
# ------------------------------------------------------------------
log_info "Pushing wiki pages..."
for wiki_file in "${WIKI_DIR}"/*.md; do
  # Title is the filename without extension
  title=$(basename "$wiki_file" .md)
  forgejo_create_wiki_page "$title" "$wiki_file" || true
done
echo ""

# ------------------------------------------------------------------
# 4. Create issues
# ------------------------------------------------------------------
log_info "Creating issues..."

# Build label ID lookup cache — use temp file to avoid zsh subshell-in-pipe
typeset -A LABEL_ID_CACHE
_forgejo_auth
if api_call GET "${FORGEJO_REPO_API}/labels?limit=50"; then
  _tmpfile=$(mktemp)
  echo "$API_RESPONSE_BODY" | jq -r '.[] | "\(.name)\t\(.id)"' > "$_tmpfile"
  while IFS=$'\t' read -r lname lid; do
    [[ -n "$lname" ]] && LABEL_ID_CACHE[$lname]="$lid"
  done < "$_tmpfile"
  rm -f "$_tmpfile"
fi

# Build milestone ID lookup cache
typeset -A MILESTONE_ID_CACHE
if api_call GET "${FORGEJO_REPO_API}/milestones?limit=50"; then
  _tmpfile=$(mktemp)
  echo "$API_RESPONSE_BODY" | jq -r '.[] | "\(.title)\t\(.id)"' > "$_tmpfile"
  while IFS=$'\t' read -r mname mid; do
    [[ -n "$mname" ]] && MILESTONE_ID_CACHE[$mname]="$mid"
  done < "$_tmpfile"
  rm -f "$_tmpfile"
fi

issue_count=$(jq length "${DATA_DIR}/forgejo-issues.json")
for i in $(seq 0 $((issue_count - 1))); do
  title=$(jq -r ".[$i].title" "${DATA_DIR}/forgejo-issues.json")
  body=$(jq -r ".[$i].body" "${DATA_DIR}/forgejo-issues.json")
  milestone_ref=$(jq -r ".[$i].milestone" "${DATA_DIR}/forgejo-issues.json")

  # Resolve label IDs
  label_ids="[]"
  label_names=("${(@f)$(jq -r ".[$i].labels[]" "${DATA_DIR}/forgejo-issues.json" 2>/dev/null)}") 2>/dev/null || label_names=()
  if [[ ${#label_names[@]} -gt 0 ]]; then
    ids=()
    for lname in "${label_names[@]}"; do
      [[ -z "$lname" ]] && continue
      lid="${LABEL_ID_CACHE[$lname]:-}"
      if [[ -n "$lid" ]]; then
        ids+=("$lid")
      else
        log_warn "Label '${lname}' not found in cache — skipping"
      fi
    done
    if [[ ${#ids[@]} -gt 0 ]]; then
      label_ids=$(printf '%s\n' "${ids[@]}" | jq -s '.')
    fi
  fi

  # Resolve milestone ID
  milestone_id=0
  if [[ -n "$milestone_ref" && "$milestone_ref" != "null" ]]; then
    mid="${MILESTONE_ID_CACHE[$milestone_ref]:-}"
    if [[ -n "$mid" ]]; then
      milestone_id="$mid"
    else
      log_warn "Milestone '${milestone_ref}' not found — creating without milestone"
    fi
  fi

  forgejo_create_issue "$title" "$body" "$label_ids" "$milestone_id" || true
done

echo ""
echo "========================================"
log_success "Forgejo setup complete!"
echo ""
echo "  Wiki:       ${FORGEJO_URL}/${FORGEJO_OWNER}/${FORGEJO_REPO}/wiki/"
echo "  Issues:     ${FORGEJO_URL}/${FORGEJO_OWNER}/${FORGEJO_REPO}/issues"
echo "  Milestones: ${FORGEJO_URL}/${FORGEJO_OWNER}/${FORGEJO_REPO}/milestones"
echo "========================================"
