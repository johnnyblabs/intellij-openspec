#!/usr/bin/env zsh
# test-pages-views.sh — Test Plane Pages and Views API creation

set -euo pipefail

SCRIPT_DIR="${0:A:h}"
source "${SCRIPT_DIR}/lib/common.sh"

check_deps
load_env

source "${SCRIPT_DIR}/lib/plane-api.sh"

DATA_DIR="${SCRIPT_DIR}/docs/data"

echo ""
echo "========================================"
echo "  Test Plane Pages & Views API"
echo "========================================"
echo ""

# Get project ID
log_info "Looking up project..."
plane_get_project_id "OpenSpec Plugin"

if [[ -z "${PLANE_PROJECT_ID:-}" || "$PLANE_PROJECT_ID" == "null" ]]; then
  log_error "Could not find project. Run setup-plane.sh first."
  exit 1
fi

log_success "Found project: ${PLANE_PROJECT_ID}"
echo ""

# ------------------------------------------------------------------
# Test: Create pages
# ------------------------------------------------------------------
log_info "Testing page creation..."
PAGES_DIR="${DATA_DIR}/plane-pages"
page_count=0
for page_file in "${PAGES_DIR}"/*.md; do
  [[ -f "$page_file" ]] || continue
  page_name=$(head -1 "$page_file" | sed 's/^# *//')

  # Read the full markdown content
  page_content=$(<"$page_file")

  log_info "  Creating page: ${page_name}"
  if plane_create_page "$PLANE_PROJECT_ID" "$page_name" "$page_content"; then
    ((page_count++))
  fi
done
echo ""
log_success "Pages processed: ${page_count}"
echo ""

# ------------------------------------------------------------------
# Test: Create views
# ------------------------------------------------------------------
log_info "Testing view creation..."
view_count=$(jq length "${DATA_DIR}/plane-views.json")
views_created=0
for i in $(seq 0 $((view_count - 1))); do
  name=$(jq -r ".[$i].name" "${DATA_DIR}/plane-views.json")
  desc=$(jq -r ".[$i].description" "${DATA_DIR}/plane-views.json")
  filters=$(jq -c ".[$i].filters" "${DATA_DIR}/plane-views.json")

  log_info "  Creating view: ${name}"
  if plane_create_view "$PLANE_PROJECT_ID" "$name" "$desc" "$filters"; then
    ((views_created++))
  fi
done
echo ""
log_success "Views processed: ${views_created}"
echo ""

echo "========================================"
echo "  Test Complete"
echo "========================================"
echo ""
echo "Check your Plane instance at: ${PLANE_URL}"
echo ""

