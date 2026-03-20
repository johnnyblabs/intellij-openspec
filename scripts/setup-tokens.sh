#!/usr/bin/env zsh
# setup-tokens.sh — Interactive guide for creating API tokens
#
# Creates scripts/.env with FORGEJO_TOKEN, FORGEJO_URL,
# PLANE_API_KEY, PLANE_URL, PLANE_WORKSPACE

set -euo pipefail

SCRIPT_DIR="${0:A:h}"
source "${SCRIPT_DIR}/lib/common.sh"

ENV_FILE="${SCRIPT_DIR}/.env"

echo ""
echo "========================================"
echo "  OpenSpec — API Token Setup"
echo "========================================"
echo ""

# ------------------------------------------------------------------
# Forgejo token
# ------------------------------------------------------------------
log_info "Step 1: Create a Forgejo API token"
echo ""
echo "  1. Open <your-forgejo-url>/user/settings/applications"
echo "  2. Under 'Generate New Token':"
echo "     - Token Name: openspec-setup"
echo "     - Scopes: check 'repo' (read/write) and 'issue' (read/write)"
echo "  3. Click 'Generate Token' and copy the value"
echo ""
read -r "FORGEJO_TOKEN?  Paste your Forgejo token: "
echo ""

FORGEJO_URL="${FORGEJO_URL:-}"
read -r "input_url?  Forgejo base URL (e.g. https://forgejo.example.com): "
FORGEJO_URL="${input_url:-$FORGEJO_URL}"

# ------------------------------------------------------------------
# Plane API key
# ------------------------------------------------------------------
log_info "Step 2: Create a Plane API key"
echo ""
echo "  1. Open <your-plane-url>/<workspace>/settings/api-tokens/"
echo "     (or Workspace Settings > API Tokens)"
echo "  2. Click 'Add API Token'"
echo "     - Title: openspec-setup"
echo "     - Expiry: choose an appropriate duration"
echo "  3. Copy the generated key"
echo ""
read -r "PLANE_API_KEY?  Paste your Plane API key: "
echo ""

PLANE_URL="${PLANE_URL:-}"
read -r "input_plane_url?  Plane base URL (e.g. https://plane.example.com): "
PLANE_URL="${input_plane_url:-$PLANE_URL}"

PLANE_WORKSPACE="${PLANE_WORKSPACE:-openspec}"
read -r "input_ws?  Plane workspace slug [${PLANE_WORKSPACE}]: "
PLANE_WORKSPACE="${input_ws:-$PLANE_WORKSPACE}"

# ------------------------------------------------------------------
# Write .env
# ------------------------------------------------------------------
cat > "$ENV_FILE" <<EOF
# OpenSpec setup tokens — DO NOT COMMIT
FORGEJO_TOKEN="${FORGEJO_TOKEN}"
FORGEJO_URL="${FORGEJO_URL}"
PLANE_API_KEY="${PLANE_API_KEY}"
PLANE_URL="${PLANE_URL}"
PLANE_WORKSPACE="${PLANE_WORKSPACE}"
EOF

chmod 600 "$ENV_FILE"
log_success "Saved to ${ENV_FILE}"

# ------------------------------------------------------------------
# Ensure .gitignore has scripts/.env
# ------------------------------------------------------------------
GITIGNORE="${SCRIPT_DIR}/../.gitignore"
if ! grep -qF 'scripts/.env' "$GITIGNORE" 2>/dev/null; then
  echo -e "\n# Setup tokens\nscripts/.env" >> "$GITIGNORE"
  log_success "Added scripts/.env to .gitignore"
fi

# ------------------------------------------------------------------
# Test connectivity
# ------------------------------------------------------------------
echo ""
log_info "Testing Forgejo connectivity..."
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" \
  -H "Authorization: token ${FORGEJO_TOKEN}" \
  "${FORGEJO_URL}/api/v1/user")

if [[ "$HTTP_CODE" == "200" ]]; then
  log_success "Forgejo token is valid"
else
  log_error "Forgejo returned HTTP ${HTTP_CODE} — check your token and URL"
fi

log_info "Testing Plane connectivity..."
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" \
  -H "X-API-Key: ${PLANE_API_KEY}" \
  "${PLANE_URL}/api/v1/workspaces/${PLANE_WORKSPACE}/")

if [[ "$HTTP_CODE" == "200" ]]; then
  log_success "Plane API key is valid"
else
  log_error "Plane returned HTTP ${HTTP_CODE} — check your API key and URL"
fi

echo ""
log_success "Setup complete. You can now run:"
echo "  scripts/setup-forgejo.sh"
echo "  scripts/setup-plane.sh"
