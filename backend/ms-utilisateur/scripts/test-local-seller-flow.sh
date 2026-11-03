#!/usr/bin/env bash

set -euo pipefail

: "${KEYCLOAK_ADMIN_PASSWORD:?Define KEYCLOAK_ADMIN_PASSWORD in the environment}"

KEYCLOAK_ADMIN_USERNAME=${KEYCLOAK_ADMIN_USERNAME:-admin}
KEYCLOAK_URL=${KEYCLOAK_URL:-http://localhost:8090}
API_URL=${API_URL:-http://127.0.0.1:8080}
USER_DB_NAME=${USER_DB_NAME:-okit_users}
USER_DB_USERNAME=${USER_DB_USERNAME:-okit_user}
CLIENT_ID=okit-seller-web
REDIRECT_URI=http://localhost:5173

SCRIPT_DIRECTORY=$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
REPOSITORY_ROOT=$(CDPATH= cd -- "$SCRIPT_DIRECTORY/../../.." && pwd)
TEMPORARY_DIRECTORY=$(mktemp -d)
TEST_USERNAME="okit-seller-e2e-$(date +%s)-$$@example.test"
TEST_PASSWORD=$(openssl rand -base64 30 | tr -d '\n')
KCADM_CONFIG="/tmp/okit-seller-e2e-$$.config"
USER_SUBJECT=

export E2E_ADMIN_USERNAME=$KEYCLOAK_ADMIN_USERNAME
export E2E_ADMIN_PASSWORD=$KEYCLOAK_ADMIN_PASSWORD
export E2E_KCADM_CONFIG=$KCADM_CONFIG
export E2E_USERNAME=$TEST_USERNAME

cleanup() {
  set +e
  docker compose exec -T \
    -e E2E_ADMIN_USERNAME \
    -e E2E_ADMIN_PASSWORD \
    -e E2E_KCADM_CONFIG \
    -e E2E_USERNAME \
    keycloak sh -lc '
      KCADM=/opt/keycloak/bin/kcadm.sh
      "$KCADM" config credentials \
        --config "$E2E_KCADM_CONFIG" \
        --server http://127.0.0.1:8080 \
        --realm master \
        --user "$E2E_ADMIN_USERNAME" \
        --password "$E2E_ADMIN_PASSWORD" >/dev/null 2>&1 || exit 0
      user_id=$("$KCADM" get users --config "$E2E_KCADM_CONFIG" -r okit \
        -q "username=$E2E_USERNAME" --fields id --format csv --noquotes 2>/dev/null)
      if [ -n "$user_id" ]; then
        "$KCADM" delete "users/$user_id" --config "$E2E_KCADM_CONFIG" -r okit >/dev/null 2>&1
      fi
      rm -f "$E2E_KCADM_CONFIG"
    ' >/dev/null 2>&1
  rm -rf "$TEMPORARY_DIRECTORY"
}
trap cleanup EXIT

for required_command in curl docker jq node openssl; do
  if ! command -v "$required_command" >/dev/null 2>&1; then
    echo "Required command not found: $required_command" >&2
    exit 1
  fi
done

extract_form_action() {
  node - "$1" <<'NODE'
const fs = require('fs');
const html = fs.readFileSync(process.argv[2], 'utf8');
const markerIndex = html.indexOf('id="kc-form-login"');
if (markerIndex < 0) process.exit(1);
const formStart = html.lastIndexOf('<form', markerIndex);
const formEnd = html.indexOf('>', markerIndex);
const action = html.slice(formStart, formEnd).match(/\baction="([^"]+)"/);
if (!action) process.exit(1);
process.stdout.write(action[1].replaceAll('&amp;', '&'));
NODE
}

read_redirect_location() {
  awk '
    BEGIN { IGNORECASE = 1 }
    /^Location:/ {
      sub(/\r$/, "");
      sub(/^[^:]+:[[:space:]]*/, "");
      print;
      exit;
    }
  ' "$1"
}

create_pkce_request() {
  PKCE_VERIFIER=$(openssl rand -base64 64 | tr '+/' '-_' | tr -d '=\n')
  PKCE_CHALLENGE=$(
    printf '%s' "$PKCE_VERIFIER" |
      openssl dgst -sha256 -binary |
      openssl base64 -A |
      tr '+/' '-_' |
      tr -d '='
  )
}

assert_exclusive_seller_token() {
  node -e '
    let token = "";
    process.stdin.setEncoding("utf8");
    process.stdin.on("data", chunk => token += chunk);
    process.stdin.on("end", () => {
      const encoded = token.trim().split(".")[1];
      if (!encoded) process.exit(1);
      const payload = JSON.parse(Buffer.from(encoded, "base64url").toString("utf8"));
      const effectiveRoles = payload.realm_access?.roles ?? [];
      const directRoles = payload.okit_direct_realm_roles ?? [];
      if (!effectiveRoles.includes("SELLER") || !effectiveRoles.includes("BUYER") ||
          !directRoles.includes("SELLER") || directRoles.includes("BUYER") || !payload.sub) process.exit(1);
      process.stdout.write(payload.sub);
    });
  '
}

cd "$REPOSITORY_ROOT"

echo '[seller-e2e] checking local services'
curl --silent --show-error --fail "$KEYCLOAK_URL/realms/okit/.well-known/openid-configuration" >/dev/null
curl --silent --show-error --fail "$API_URL/health" >/dev/null

echo '[seller-e2e] creating a directly assigned seller through the admin workflow'
SELLER_EMAIL="$TEST_USERNAME" \
SELLER_TEMPORARY_PASSWORD="$TEST_PASSWORD" \
SELLER_PASSWORD_TEMPORARY=false \
SELLER_FIRST_NAME='Camille' \
SELLER_LAST_NAME='Martin' \
./infra/keycloak/create-local-seller.sh >/dev/null

create_pkce_request
cookies="$TEMPORARY_DIRECTORY/login.cookies"
login_page="$TEMPORARY_DIRECTORY/login.html"
login_headers="$TEMPORARY_DIRECTORY/login.headers"
login_result="$TEMPORARY_DIRECTORY/login-result.html"
tokens="$TEMPORARY_DIRECTORY/tokens.json"

echo '[seller-e2e] authenticating with Authorization Code and PKCE'
curl --silent --show-error --fail \
  --cookie-jar "$cookies" \
  --get "$KEYCLOAK_URL/realms/okit/protocol/openid-connect/auth" \
  --data-urlencode "client_id=$CLIENT_ID" \
  --data-urlencode "redirect_uri=$REDIRECT_URI" \
  --data-urlencode 'response_type=code' \
  --data-urlencode 'scope=openid profile email' \
  --data-urlencode 'state=seller-e2e' \
  --data-urlencode "code_challenge=$PKCE_CHALLENGE" \
  --data-urlencode 'code_challenge_method=S256' \
  --output "$login_page"

login_action=$(extract_form_action "$login_page")
curl --silent --show-error \
  --cookie "$cookies" \
  --cookie-jar "$cookies" \
  --dump-header "$login_headers" \
  --output "$login_result" \
  --request POST "$login_action" \
  --data-urlencode "username=$TEST_USERNAME" \
  --data-urlencode "password=$TEST_PASSWORD" \
  --data-urlencode 'credentialId='

login_redirect=$(read_redirect_location "$login_headers")
if [ -z "$login_redirect" ]; then
  echo 'Seller login did not return an authorization code.' >&2
  exit 1
fi
authorization_code=$(node -e '
  const code = new URL(process.argv[1]).searchParams.get("code");
  if (!code) process.exit(1);
  process.stdout.write(code);
' "$login_redirect")

curl --silent --show-error --fail \
  --request POST "$KEYCLOAK_URL/realms/okit/protocol/openid-connect/token" \
  --header 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode "client_id=$CLIENT_ID" \
  --data-urlencode 'grant_type=authorization_code' \
  --data-urlencode "code=$authorization_code" \
  --data-urlencode "redirect_uri=$REDIRECT_URI" \
  --data-urlencode "code_verifier=$PKCE_VERIFIER" \
  --output "$tokens"

access_token=$(jq -er '.access_token' "$tokens")
refresh_token=$(jq -er '.refresh_token' "$tokens")
USER_SUBJECT=$(printf '%s' "$access_token" | assert_exclusive_seller_token)

echo '[seller-e2e] checking identity and buyer API isolation'
curl --silent --show-error --fail \
  --header "Authorization: Bearer $access_token" \
  "$KEYCLOAK_URL/realms/okit/protocol/openid-connect/userinfo" |
  jq --exit-status --arg email "$TEST_USERNAME" '.email == $email' >/dev/null

profile_status=$(curl --silent --show-error --output /dev/null --write-out '%{http_code}' \
  --header "Authorization: Bearer $access_token" "$API_URL/api/v1/users/me")
if [ "$profile_status" != 403 ]; then
  echo "Expected seller profile access to return 403, received $profile_status." >&2
  exit 1
fi

profile_count=$(printf "SELECT count(*) FROM user_profiles WHERE identity_subject = '%s';\n" "$USER_SUBJECT" |
  docker compose exec -T postgres psql \
    --username "$USER_DB_USERNAME" \
    --dbname "$USER_DB_NAME" \
    --tuples-only --no-align)
if [ "$profile_count" != 0 ]; then
  echo 'A seller profile was unexpectedly stored in PostgreSQL.' >&2
  exit 1
fi

echo '[seller-e2e] refreshing and ending the seller session'
refresh_tokens="$TEMPORARY_DIRECTORY/refresh-tokens.json"
curl --silent --show-error --fail \
  --request POST "$KEYCLOAK_URL/realms/okit/protocol/openid-connect/token" \
  --header 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode "client_id=$CLIENT_ID" \
  --data-urlencode 'grant_type=refresh_token' \
  --data-urlencode "refresh_token=$refresh_token" \
  --output "$refresh_tokens"

refreshed_access_token=$(jq -er '.access_token' "$refresh_tokens")
printf '%s' "$refreshed_access_token" | assert_exclusive_seller_token >/dev/null
refreshed_refresh_token=$(jq -er '.refresh_token // empty' "$refresh_tokens" || true)
if [ -z "$refreshed_refresh_token" ]; then
  refreshed_refresh_token=$refresh_token
fi

curl --silent --show-error --fail \
  --request POST "$KEYCLOAK_URL/realms/okit/protocol/openid-connect/logout" \
  --header 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode "client_id=$CLIENT_ID" \
  --data-urlencode "refresh_token=$refreshed_refresh_token" >/dev/null

echo 'Local seller flow succeeded: admin provisioning, PKCE login, direct SELLER claim, buyer API refusal and token refresh.'
