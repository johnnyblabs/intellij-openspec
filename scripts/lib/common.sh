#!/usr/bin/env zsh
# common.sh — Shared utilities for OpenSpec setup scripts

set -euo pipefail

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# SCRIPT_DIR should be set by the calling script before sourcing this file.
# Fallback: derive from $0 (works when common.sh is the entry point)
: "${SCRIPT_DIR:=${0:A:h}}"

# ---------------------------------------------------------------------------
# Logging
# ---------------------------------------------------------------------------
log_info()    { print -P "%F{blue}[INFO]%f  $*"; }
log_success() { print -P "%F{green}[OK]%f    $*"; }
log_warn()    { print -P "%F{yellow}[WARN]%f  $*"; }
log_error()   { print -P "%F{red}[ERROR]%f $*" >&2; }

# ---------------------------------------------------------------------------
# load_env — source scripts/.env
# ---------------------------------------------------------------------------
load_env() {
  local env_file="${SCRIPT_DIR}/.env"
  if [[ ! -f "$env_file" ]]; then
    log_error "Missing ${env_file}. Run scripts/setup-tokens.sh first."
    exit 1
  fi
  # shellcheck source=/dev/null
  source "$env_file"

  # Validate required vars
  local missing=()
  [[ -z "${FORGEJO_TOKEN:-}" ]]     && missing+=(FORGEJO_TOKEN)
  [[ -z "${FORGEJO_URL:-}" ]]       && missing+=(FORGEJO_URL)
  [[ -z "${PLANE_API_KEY:-}" ]]     && missing+=(PLANE_API_KEY)
  [[ -z "${PLANE_URL:-}" ]]         && missing+=(PLANE_URL)
  [[ -z "${PLANE_WORKSPACE:-}" ]]   && missing+=(PLANE_WORKSPACE)

  if [[ ${#missing[@]} -gt 0 ]]; then
    log_error "Missing env vars: ${missing[*]}"
    exit 1
  fi
}

# ---------------------------------------------------------------------------
# check_deps — verify required CLI tools
# ---------------------------------------------------------------------------
check_deps() {
  local deps=(curl jq base64)
  local missing=()
  for cmd in "${deps[@]}"; do
    if ! command -v "$cmd" &>/dev/null; then
      missing+=("$cmd")
    fi
  done
  if [[ ${#missing[@]} -gt 0 ]]; then
    log_error "Missing dependencies: ${missing[*]}"
    log_error "Install them and retry."
    exit 1
  fi
}

# ---------------------------------------------------------------------------
# api_call — curl wrapper with error handling
#   Usage: api_call METHOD URL [data]
#   Reads auth header from $API_AUTH_HEADER (set by caller)
# ---------------------------------------------------------------------------
api_call() {
  local method="$1"
  local url="$2"
  local data="${3:-}"
  local max_retries=5
  local attempt=0
  local response http_code body

  while (( attempt < max_retries )); do
    (( attempt++ ))

    local -a curl_args=(
      -s -S
      -X "$method"
      -H "Content-Type: application/json"
      -H "$API_AUTH_HEADER"
      -w "\n%{http_code}"
    )

    if [[ -n "$data" ]]; then
      curl_args+=(-d "$data")
    fi

    response=$(curl "${curl_args[@]}" "$url" 2>&1) || {
      log_error "curl failed for $method $url"
      return 1
    }

    http_code=$(echo "$response" | tail -1)
    body=$(echo "$response" | sed '$d')

    # Export for caller
    API_RESPONSE_CODE="$http_code"
    API_RESPONSE_BODY="$body"

    if [[ "$http_code" -ge 200 && "$http_code" -lt 300 ]]; then
      return 0
    elif [[ "$http_code" == "400" || "$http_code" == "409" || "$http_code" == "422" ]]; then
      # Conflict / already exists — caller can handle idempotently
      # Includes 400 because Forgejo returns 400 instead of 409 for some endpoints
      return 2
    elif [[ "$http_code" == "429" && $attempt -lt $max_retries ]]; then
      local wait_secs=$(( attempt * 2 ))
      log_warn "Rate limited — waiting ${wait_secs}s (retry ${attempt}/${max_retries})"
      sleep "$wait_secs"
      continue
    else
      log_error "HTTP $http_code from $method $url"
      if echo "$body" | jq . &>/dev/null 2>&1; then
        echo "$body" | jq . >&2
      else
        echo "$body" >&2
      fi
      return 1
    fi
  done
}
