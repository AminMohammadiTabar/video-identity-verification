#!/usr/bin/env bash
# Standalone repro for the INVALID_TOKEN bug found while testing DBS-1747's
# base-data crawl against Bank Mellat's real sandbox (stake.atlasdtco.com) -
# no JVM/app involved at all, pure curl+openssl, so this can be handed to
# Bank Mellat's own team to run themselves.
#
# What it shows: POST /auth/login succeeds (200, real access token, signed
# by http://192.168.10.53:30888/realms/baas-realm per the token's own `iss`
# claim) - but that exact token is then rejected by
# GET /digital-platform/api/baas/base-data/provinces with:
#   401 {"errorCode":"INVALID_TOKEN","errorMessage":"Invalid JWT token",
#        "errorDetails":["Token validation failed with all available public keys."]}
# i.e. the base-data API can't validate a token the auth service itself just
# issued - looks like the two don't share the same JWKS/public-key set.
#
# Usage: .claude/skills/run-mellat-provider-app/repro-invalid-token.sh
# Requires: curl, openssl (both already on PATH in this container).
#
# Config mirrors application-dev.yml's feign.mellat.auth.* defaults exactly -
# override via env vars below if Bank Mellat gives you different sandbox
# values to try.

set -euo pipefail
cd "$(dirname "$0")/../../../"

# --noproxy '*' below: bypasses any local HTTP_PROXY/HTTPS_PROXY env var so
# this genuinely reaches stake.atlasdtco.com directly - harmless (a no-op) if
# you don't have one set at all.

BASE_URL="${MELLAT_AUTH_BASE_URL:-https://stake.atlasdtco.com}"
CLIENT_ID="${MELLAT_CLIENT_ID:-mofid-test}"
AUDIENCE="${MELLAT_AUDIENCE:-http://192.168.10.53:30888/realms/baas-realm}"
PRIVATE_KEY="${MELLAT_PRIVATE_KEY_PATH:-src/main/resources/cert/private.pem}"
VALIDITY_SECONDS="${MELLAT_CLIENT_ASSERTION_VALIDITY_SECONDS:-300}"

b64url() {
  # stdin -> base64url, no padding (RFC 7515 requires unpadded base64url)
  openssl base64 -A | tr '+/' '-_' | tr -d '='
}

now=$(date +%s)
exp=$((now + VALIDITY_SECONDS))
jti=$(openssl rand -hex 16)

header='{"alg":"RS256","typ":"JWT"}'
payload=$(printf '{"sub":"%s","iss":"%s","aud":"%s","iat":%d,"exp":%d,"jti":"%s"}' \
  "$CLIENT_ID" "$CLIENT_ID" "$AUDIENCE" "$now" "$exp" "$jti")

header_b64=$(printf '%s' "$header" | b64url)
payload_b64=$(printf '%s' "$payload" | b64url)
signing_input="${header_b64}.${payload_b64}"

signature_b64=$(printf '%s' "$signing_input" | openssl dgst -sha256 -sign "$PRIVATE_KEY" -binary | b64url)
client_assertion="${signing_input}.${signature_b64}"

echo "== Client assertion JWT (RS256, signed with $PRIVATE_KEY) ==" >&2
echo "$client_assertion" >&2
echo >&2

echo "== POST $BASE_URL/auth/login ==" >&2
login_response=$(curl -sS --noproxy '*' -w '\n%{http_code}' -X POST "$BASE_URL/auth/login" \
  -H "Authorization: Bearer ${client_assertion}")
login_status=$(echo "$login_response" | tail -1)
login_body=$(echo "$login_response" | sed '$d')
echo "status: $login_status" >&2
echo "body:   $login_body" >&2
echo >&2

access_token=$(echo "$login_body" | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')
if [ -z "$access_token" ]; then
  echo "No access_token in the login response - stopping here (see body above)." >&2
  exit 1
fi

echo "== GET $BASE_URL/digital-platform/api/baas/base-data/provinces ==" >&2
provinces_response=$(curl -sS --noproxy '*' -w '\n%{http_code}' "$BASE_URL/digital-platform/api/baas/base-data/provinces" \
  -H "Authorization: Bearer ${access_token}")
provinces_status=$(echo "$provinces_response" | tail -1)
provinces_body=$(echo "$provinces_response" | sed '$d')
echo "status: $provinces_status" >&2
echo "body:   $provinces_body" >&2
