#!/usr/bin/env bash

set -euo pipefail

: "${KEYCLOAK_ADMIN_PASSWORD:?Define KEYCLOAK_ADMIN_PASSWORD in the environment}"

KEYCLOAK_ADMIN_USERNAME=${KEYCLOAK_ADMIN_USERNAME:-admin}
KEYCLOAK_URL=${KEYCLOAK_URL:-http://localhost:8090}
API_URL=${API_URL:-http://127.0.0.1:8080}
USER_DB_NAME=${USER_DB_NAME:-okit_users}
USER_DB_USERNAME=${USER_DB_USERNAME:-okit_user}
CLIENT_ID=okit-mobile
REDIRECT_URI=http://localhost:19006

SCRIPT_DIRECTORY=$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
REPOSITORY_ROOT=$(CDPATH= cd -- "$SCRIPT_DIRECTORY/../../.." && pwd)
TEMPORARY_DIRECTORY=$(mktemp -d)
TEST_USERNAME="okit-e2e-$(date +%s)-$$@example.test"
TEST_PASSWORD=$(openssl rand -base64 30 | tr -d '\n')
KCADM_CONFIG="/tmp/okit-e2e-$$.config"
USER_SUBJECT=

export E2E_ADMIN_USERNAME=$KEYCLOAK_ADMIN_USERNAME
export E2E_ADMIN_PASSWORD=$KEYCLOAK_ADMIN_PASSWORD
export E2E_KCADM_CONFIG=$KCADM_CONFIG
export E2E_USERNAME=$TEST_USERNAME

cleanup() {
  set +e

  if [ -n "$USER_SUBJECT" ] && [[ "$USER_SUBJECT" =~ ^[0-9a-fA-F-]{36}$ ]]; then
    printf "DELETE FROM user_profiles WHERE identity_subject = '%s';\n" "$USER_SUBJECT" |
      docker compose exec -T postgres \
        psql --username "$USER_DB_USERNAME" --dbname "$USER_DB_NAME" >/dev/null 2>&1
  fi

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
      user_id=$("$KCADM" get users \
        --config "$E2E_KCADM_CONFIG" \
        -r okit \
        -q "username=$E2E_USERNAME" \
        --fields id \
        --format csv \
        --noquotes 2>/dev/null)
      if [ -n "$user_id" ]; then
        "$KCADM" delete "users/$user_id" \
          --config "$E2E_KCADM_CONFIG" \
          -r okit >/dev/null 2>&1
      fi
      rm -f "$E2E_KCADM_CONFIG"
    ' >/dev/null 2>&1

  rm -rf "$TEMPORARY_DIRECTORY"
}
trap cleanup EXIT

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Required command not found: $1" >&2
    exit 1
  fi
}

log_step() {
  printf '[e2e] %s\n' "$1"
}

for required_command in curl docker jq node openssl; do
  require_command "$required_command"
done

extract_form_action() {
  node - "$1" "$2" <<'NODE'
const fs = require('fs');
const html = fs.readFileSync(process.argv[2], 'utf8');
const formId = process.argv[3];
const marker = `id="${formId}"`;
const markerIndex = html.indexOf(marker);
if (markerIndex < 0) process.exit(1);
const formStart = html.lastIndexOf('<form', markerIndex);
const formEnd = html.indexOf('>', markerIndex);
if (formStart < 0 || formEnd < 0) process.exit(1);
const action = html.slice(formStart, formEnd).match(/\baction="([^"]+)"/);
if (!action) process.exit(1);
process.stdout.write(action[1].replaceAll('&amp;', '&'));
NODE
}

extract_redirect_code() {
  node -e '
    const redirect = new URL(process.argv[1]);
    const code = redirect.searchParams.get("code");
    if (!code) process.exit(1);
    process.stdout.write(code);
  ' "$1"
}

extract_buyer_subject() {
  node -e '
    let token = "";
    process.stdin.setEncoding("utf8");
    process.stdin.on("data", chunk => token += chunk);
    process.stdin.on("end", () => {
      const encoded = token.trim().split(".")[1];
      if (!encoded) process.exit(1);
      const payload = JSON.parse(Buffer.from(encoded, "base64url").toString("utf8"));
      if (!payload.realm_access?.roles?.includes("BUYER") || !payload.sub) process.exit(1);
      process.stdout.write(payload.sub);
    });
  '
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
  OAUTH_STATE=$(openssl rand -hex 24)
}

start_authorization() {
  local endpoint=$1
  local cookie_file=$2
  local html_file=$3

  create_pkce_request
  curl --silent --show-error --fail \
    --cookie-jar "$cookie_file" \
    --get "$KEYCLOAK_URL/realms/okit/protocol/openid-connect/$endpoint" \
    --data-urlencode "client_id=$CLIENT_ID" \
    --data-urlencode "redirect_uri=$REDIRECT_URI" \
    --data-urlencode 'response_type=code' \
    --data-urlencode 'scope=openid profile email' \
    --data-urlencode "state=$OAUTH_STATE" \
    --data-urlencode "code_challenge=$PKCE_CHALLENGE" \
    --data-urlencode 'code_challenge_method=S256' \
    --output "$html_file"
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

exchange_code() {
  local code=$1
  local verifier=$2
  local output_file=$3

  curl --silent --show-error --fail \
    --request POST "$KEYCLOAK_URL/realms/okit/protocol/openid-connect/token" \
    --header 'Content-Type: application/x-www-form-urlencoded' \
    --data-urlencode "client_id=$CLIENT_ID" \
    --data-urlencode 'grant_type=authorization_code' \
    --data-urlencode "code=$code" \
    --data-urlencode "redirect_uri=$REDIRECT_URI" \
    --data-urlencode "code_verifier=$verifier" \
    --output "$output_file"
}

cd "$REPOSITORY_ROOT"

log_step 'checking local services'
curl --silent --show-error --fail "$KEYCLOAK_URL/realms/okit/.well-known/openid-configuration" >/dev/null
curl --silent --show-error --fail "$API_URL/health" >/dev/null

log_step 'reconciling the existing Keycloak realm'
./infra/keycloak/reconcile-local-realm.sh >/dev/null

log_step 'validating Keycloak administration credentials'
docker compose exec -T \
  -e E2E_ADMIN_USERNAME \
  -e E2E_ADMIN_PASSWORD \
  -e E2E_KCADM_CONFIG \
  keycloak sh -lc '
    /opt/keycloak/bin/kcadm.sh config credentials \
      --config "$E2E_KCADM_CONFIG" \
      --server http://127.0.0.1:8080 \
      --realm master \
      --user "$E2E_ADMIN_USERNAME" \
      --password "$E2E_ADMIN_PASSWORD" >/dev/null
  '

registration_cookies="$TEMPORARY_DIRECTORY/registration.cookies"
registration_page="$TEMPORARY_DIRECTORY/registration.html"
registration_headers="$TEMPORARY_DIRECTORY/registration.headers"
registration_result="$TEMPORARY_DIRECTORY/registration-result.html"
registration_tokens="$TEMPORARY_DIRECTORY/registration-tokens.json"

log_step 'registering a buyer with Authorization Code and PKCE'
start_authorization registrations "$registration_cookies" "$registration_page"
registration_verifier=$PKCE_VERIFIER
registration_action=$(extract_form_action "$registration_page" kc-register-form)

curl --silent --show-error \
  --cookie "$registration_cookies" \
  --cookie-jar "$registration_cookies" \
  --dump-header "$registration_headers" \
  --output "$registration_result" \
  --request POST "$registration_action" \
  --data-urlencode 'firstName=Alice' \
  --data-urlencode 'lastName=Martin' \
  --data-urlencode "email=$TEST_USERNAME" \
  --data-urlencode "password=$TEST_PASSWORD" \
  --data-urlencode "password-confirm=$TEST_PASSWORD"

registration_redirect=$(read_redirect_location "$registration_headers")
if [ -z "$registration_redirect" ]; then
  echo 'Public registration did not return an authorization code.' >&2
  exit 1
fi
registration_code=$(extract_redirect_code "$registration_redirect")
exchange_code "$registration_code" "$registration_verifier" "$registration_tokens"

access_token=$(jq -er '.access_token' "$registration_tokens")
refresh_token=$(jq -er '.refresh_token' "$registration_tokens")
USER_SUBJECT=$(printf '%s' "$access_token" | extract_buyer_subject)

log_step 'validating the issued identity and BUYER access'
curl --silent --show-error --fail \
  --header "Authorization: Bearer $access_token" \
  "$KEYCLOAK_URL/realms/okit/protocol/openid-connect/userinfo" |
  jq --exit-status --arg email "$TEST_USERNAME" '.email == $email' >/dev/null

profile_payload='{"firstName":"Alice","lastName":"Martin","phoneNumber":"+33 6 12 34 56 78","birthDate":"2000-03-14"}'
log_step 'creating the buyer profile through the API gateway'
curl --silent --show-error --fail \
  --request PUT "$API_URL/api/v1/users/me" \
  --header "Authorization: Bearer $access_token" \
  --header 'Content-Type: application/json' \
  --data "$profile_payload" |
  jq --exit-status --arg subject "$USER_SUBJECT" '
    .subject == $subject and
    .firstName == "Alice" and
    .lastName == "Martin" and
    .phoneNumber == "+33 6 12 34 56 78"
  ' >/dev/null

log_step 'resolving the business UUID without exposing private profile data'
curl --silent --show-error --fail \
  --header "Authorization: Bearer $access_token" \
  "$API_URL/api/v1/users/me/id" |
  jq --exit-status '
    (keys == ["id"]) and
    (.id | test("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$"))
  ' >/dev/null

curl --silent --show-error --fail \
  --request POST "$KEYCLOAK_URL/realms/okit/protocol/openid-connect/logout" \
  --header 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode "client_id=$CLIENT_ID" \
  --data-urlencode "refresh_token=$refresh_token" >/dev/null

log_step 'logging in again and reading the persisted profile'
login_cookies="$TEMPORARY_DIRECTORY/login.cookies"
login_page="$TEMPORARY_DIRECTORY/login.html"
login_headers="$TEMPORARY_DIRECTORY/login.headers"
login_result="$TEMPORARY_DIRECTORY/login-result.html"
login_tokens="$TEMPORARY_DIRECTORY/login-tokens.json"

start_authorization auth "$login_cookies" "$login_page"
login_verifier=$PKCE_VERIFIER
login_action=$(extract_form_action "$login_page" kc-form-login)

curl --silent --show-error \
  --cookie "$login_cookies" \
  --cookie-jar "$login_cookies" \
  --dump-header "$login_headers" \
  --output "$login_result" \
  --request POST "$login_action" \
  --data-urlencode "username=$TEST_USERNAME" \
  --data-urlencode "password=$TEST_PASSWORD" \
  --data-urlencode 'credentialId='

login_redirect=$(read_redirect_location "$login_headers")
if [ -z "$login_redirect" ]; then
  echo 'Login did not return an authorization code.' >&2
  exit 1
fi
login_code=$(extract_redirect_code "$login_redirect")
exchange_code "$login_code" "$login_verifier" "$login_tokens"

access_token=$(jq -er '.access_token' "$login_tokens")
refresh_token=$(jq -er '.refresh_token' "$login_tokens")
reconnected_subject=$(printf '%s' "$access_token" | extract_buyer_subject)
if [ "$reconnected_subject" != "$USER_SUBJECT" ]; then
  echo 'Reconnection returned a different Keycloak subject.' >&2
  exit 1
fi

curl --silent --show-error --fail \
  --header "Authorization: Bearer $access_token" \
  "$API_URL/api/v1/users/me" |
  jq --exit-status '.firstName == "Alice" and .birthDate == "2000-03-14"' >/dev/null

log_step 'refreshing the session and retrying the protected API'
refresh_tokens="$TEMPORARY_DIRECTORY/refresh-tokens.json"
curl --silent --show-error --fail \
  --request POST "$KEYCLOAK_URL/realms/okit/protocol/openid-connect/token" \
  --header 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode "client_id=$CLIENT_ID" \
  --data-urlencode 'grant_type=refresh_token' \
  --data-urlencode "refresh_token=$refresh_token" \
  --output "$refresh_tokens"

refreshed_access_token=$(jq -er '.access_token' "$refresh_tokens")
refreshed_refresh_token=$(jq -er '.refresh_token // empty' "$refresh_tokens" || true)
if [ -z "$refreshed_refresh_token" ]; then
  refreshed_refresh_token=$refresh_token
fi

curl --silent --show-error --fail \
  --header "Authorization: Bearer $refreshed_access_token" \
  "$API_URL/api/v1/users/me" |
  jq --exit-status --arg subject "$USER_SUBJECT" '.subject == $subject' >/dev/null

unauthenticated_status=$(
  curl --silent --show-error --output /dev/null --write-out '%{http_code}' \
    "$API_URL/api/v1/users/me"
)
if [ "$unauthenticated_status" != 401 ]; then
  echo "Expected 401 without token, received $unauthenticated_status." >&2
  exit 1
fi

log_step 'logging out and cleaning temporary test data'
curl --silent --show-error --fail \
  --request POST "$KEYCLOAK_URL/realms/okit/protocol/openid-connect/logout" \
  --header 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode "client_id=$CLIENT_ID" \
  --data-urlencode "refresh_token=$refreshed_refresh_token" >/dev/null

echo 'Local user flow succeeded: registration, PKCE login, profile persistence, reconnection and token refresh.'
