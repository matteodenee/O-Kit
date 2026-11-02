#!/bin/sh

set -eu

: "${KEYCLOAK_ADMIN_PASSWORD:?Define KEYCLOAK_ADMIN_PASSWORD in the environment}"

KEYCLOAK_ADMIN_USERNAME=${KEYCLOAK_ADMIN_USERNAME:-admin}
KEYCLOAK_SMTP_HOST=${KEYCLOAK_SMTP_HOST:-mailpit}
KEYCLOAK_SMTP_PORT=${KEYCLOAK_SMTP_PORT:-1025}
KEYCLOAK_SMTP_FROM=${KEYCLOAK_SMTP_FROM:-noreply@okit.local}
KEYCLOAK_SMTP_FROM_DISPLAY_NAME=${KEYCLOAK_SMTP_FROM_DISPLAY_NAME:-"O'kit"}
KEYCLOAK_SMTP_AUTH=${KEYCLOAK_SMTP_AUTH:-false}
KEYCLOAK_SMTP_STARTTLS=${KEYCLOAK_SMTP_STARTTLS:-false}
KEYCLOAK_SMTP_SSL=${KEYCLOAK_SMTP_SSL:-false}
KEYCLOAK_SMTP_USER=${KEYCLOAK_SMTP_USER:-}
KEYCLOAK_SMTP_PASSWORD=${KEYCLOAK_SMTP_PASSWORD:-}
SCRIPT_DIRECTORY=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
REPOSITORY_ROOT=$(CDPATH= cd -- "$SCRIPT_DIRECTORY/../.." && pwd)

case "$KEYCLOAK_SMTP_PORT" in
  ''|*[!0-9]*)
    echo "KEYCLOAK_SMTP_PORT must be a numeric port." >&2
    exit 1
    ;;
esac

for boolean_value in "$KEYCLOAK_SMTP_AUTH" "$KEYCLOAK_SMTP_STARTTLS" "$KEYCLOAK_SMTP_SSL"; do
  case "$boolean_value" in
    true|false) ;;
    *)
      echo "SMTP boolean values must be 'true' or 'false'." >&2
      exit 1
      ;;
  esac
done

if [ "$KEYCLOAK_SMTP_STARTTLS" = true ] && [ "$KEYCLOAK_SMTP_SSL" = true ]; then
  echo "KEYCLOAK_SMTP_STARTTLS and KEYCLOAK_SMTP_SSL cannot both be enabled." >&2
  exit 1
fi

if [ "$KEYCLOAK_SMTP_AUTH" = true ] && { [ -z "$KEYCLOAK_SMTP_USER" ] || [ -z "$KEYCLOAK_SMTP_PASSWORD" ]; }; then
  echo "KEYCLOAK_SMTP_USER and KEYCLOAK_SMTP_PASSWORD are required when SMTP authentication is enabled." >&2
  exit 1
fi

export OKIT_KEYCLOAK_ADMIN_USERNAME=$KEYCLOAK_ADMIN_USERNAME
export OKIT_KEYCLOAK_ADMIN_PASSWORD=$KEYCLOAK_ADMIN_PASSWORD
export OKIT_SMTP_HOST=$KEYCLOAK_SMTP_HOST
export OKIT_SMTP_PORT=$KEYCLOAK_SMTP_PORT
export OKIT_SMTP_FROM=$KEYCLOAK_SMTP_FROM
export OKIT_SMTP_FROM_DISPLAY_NAME=$KEYCLOAK_SMTP_FROM_DISPLAY_NAME
export OKIT_SMTP_AUTH=$KEYCLOAK_SMTP_AUTH
export OKIT_SMTP_STARTTLS=$KEYCLOAK_SMTP_STARTTLS
export OKIT_SMTP_SSL=$KEYCLOAK_SMTP_SSL
export OKIT_SMTP_USER=$KEYCLOAK_SMTP_USER
export OKIT_SMTP_PASSWORD=$KEYCLOAK_SMTP_PASSWORD

cd "$REPOSITORY_ROOT"

docker compose exec -T \
  -e OKIT_KEYCLOAK_ADMIN_USERNAME \
  -e OKIT_KEYCLOAK_ADMIN_PASSWORD \
  -e OKIT_SMTP_HOST \
  -e OKIT_SMTP_PORT \
  -e OKIT_SMTP_FROM \
  -e OKIT_SMTP_FROM_DISPLAY_NAME \
  -e OKIT_SMTP_AUTH \
  -e OKIT_SMTP_STARTTLS \
  -e OKIT_SMTP_SSL \
  -e OKIT_SMTP_USER \
  -e OKIT_SMTP_PASSWORD \
  keycloak sh -s <<'KEYCLOAK_SCRIPT'
set -eu

KCADM=/opt/keycloak/bin/kcadm.sh
CONFIG=$(mktemp)
SERVER_URL=http://127.0.0.1:8080
REALM=okit
BUYER_ROLE=BUYER
SELLER_ROLE=SELLER
MOBILE_CLIENT_ID=okit-mobile
SELLER_CLIENT_ID=okit-seller-web

cleanup() {
  rm -f "$CONFIG"
}
trap cleanup EXIT

kcadm() {
  command=$1
  shift
  "$KCADM" "$command" --config "$CONFIG" "$@"
}

"$KCADM" config credentials \
  --config "$CONFIG" \
  --server "$SERVER_URL" \
  --realm master \
  --user "$OKIT_KEYCLOAK_ADMIN_USERNAME" \
  --password "$OKIT_KEYCLOAK_ADMIN_PASSWORD" >/dev/null

if ! kcadm get "realms/$REALM" >/dev/null 2>&1; then
  echo "Realm '$REALM' absent. Start from an empty volume to run the versioned import first." >&2
  exit 1
fi

kcadm update "realms/$REALM" \
  -s enabled=true \
  -s sslRequired=external \
  -s registrationAllowed=true \
  -s registrationEmailAsUsername=true \
  -s resetPasswordAllowed=true \
  -s rememberMe=true \
  -s loginWithEmailAllowed=true \
  -s duplicateEmailsAllowed=false \
  -s verifyEmail=false \
  -s "smtpServer.host=$OKIT_SMTP_HOST" \
  -s "smtpServer.port=$OKIT_SMTP_PORT" \
  -s "smtpServer.from=$OKIT_SMTP_FROM" \
  -s "smtpServer.fromDisplayName=$OKIT_SMTP_FROM_DISPLAY_NAME" \
  -s "smtpServer.auth=$OKIT_SMTP_AUTH" \
  -s "smtpServer.starttls=$OKIT_SMTP_STARTTLS" \
  -s "smtpServer.ssl=$OKIT_SMTP_SSL" \
  -s "smtpServer.user=$OKIT_SMTP_USER" \
  -s "smtpServer.password=$OKIT_SMTP_PASSWORD" >/dev/null

ensure_role() {
  role_name=$1
  role_description=$2
  if ! kcadm get "roles/$role_name" -r "$REALM" >/dev/null 2>&1; then
    kcadm create roles -r "$REALM" -s "name=$role_name" >/dev/null
  fi
  kcadm update "roles/$role_name" -r "$REALM" \
    -s "description=$role_description" >/dev/null
}

client_uuid() {
  kcadm get clients -r "$REALM" \
    -q "clientId=$1" \
    --fields id \
    --format csv \
    --noquotes
}

ensure_client_exists() {
  requested_client_id=$1
  requested_client_uuid=$(client_uuid "$requested_client_id")
  if [ -z "$requested_client_uuid" ]; then
    kcadm create clients -r "$REALM" -s "clientId=$requested_client_id" >/dev/null
  fi
}

ensure_scope_mapping() {
  mapped_client_uuid=$1
  mapped_role=$2
  if ! kcadm get "clients/$mapped_client_uuid/scope-mappings/realm" -r "$REALM" \
    --fields name --format csv --noquotes | grep -Fx "$mapped_role" >/dev/null; then
    mapped_role_id=$(kcadm get "roles/$mapped_role" -r "$REALM" \
      --fields id --format csv --noquotes)
    kcadm create "clients/$mapped_client_uuid/scope-mappings/realm" -r "$REALM" \
      --body "[{\"id\":\"$mapped_role_id\",\"name\":\"$mapped_role\"}]" >/dev/null
  fi
}

ensure_direct_roles_mapper() {
  mapped_client_uuid=$1
  mapper_name=okit-direct-realm-roles
  if ! kcadm get "clients/$mapped_client_uuid/protocol-mappers/models" -r "$REALM" \
    --fields name --format csv --noquotes | grep -Fx "$mapper_name" >/dev/null; then
    kcadm create "clients/$mapped_client_uuid/protocol-mappers/models" -r "$REALM" \
      -s "name=$mapper_name" \
      -s protocol=openid-connect \
      -s protocolMapper=okit-direct-realm-roles-mapper \
      -s consentRequired=false >/dev/null
  fi
}

ensure_role "$BUYER_ROLE" "Acheteur autorisé à utiliser l'application mobile O'kit"
ensure_role "$SELLER_ROLE" "Employé O'kit autorisé à utiliser le backoffice vendeur"

ensure_client_exists "$MOBILE_CLIENT_ID"
MOBILE_CLIENT_UUID=$(client_uuid "$MOBILE_CLIENT_ID")

kcadm update "clients/$MOBILE_CLIENT_UUID" -r "$REALM" \
  -s "clientId=$MOBILE_CLIENT_ID" \
  -s "name=O'kit mobile" \
  -s "description=Client public OIDC de l'application Expo O'kit" \
  -s enabled=true \
  -s protocol=openid-connect \
  -s publicClient=true \
  -s bearerOnly=false \
  -s standardFlowEnabled=true \
  -s implicitFlowEnabled=false \
  -s directAccessGrantsEnabled=false \
  -s serviceAccountsEnabled=false \
  -s fullScopeAllowed=false \
  -s 'redirectUris=["okit://auth","http://localhost:19006","http://localhost:19006/*","https://dams5-07.students.polytech.lange.xyz","https://dams5-07.students.polytech.lange.xyz/*"]' \
  -s 'webOrigins=["http://localhost:19006","https://dams5-07.students.polytech.lange.xyz"]' \
  -s 'attributes."pkce.code.challenge.method"=S256' \
  -s 'defaultClientScopes=["basic","profile","email","roles","web-origins"]' \
  -s 'optionalClientScopes=["address","phone","offline_access"]' >/dev/null

ensure_client_exists "$SELLER_CLIENT_ID"
SELLER_CLIENT_UUID=$(client_uuid "$SELLER_CLIENT_ID")

kcadm update "clients/$SELLER_CLIENT_UUID" -r "$REALM" \
  -s "clientId=$SELLER_CLIENT_ID" \
  -s "name=O'kit seller backoffice" \
  -s "description=Client public OIDC du backoffice React réservé aux employés O'kit" \
  -s enabled=true \
  -s protocol=openid-connect \
  -s publicClient=true \
  -s bearerOnly=false \
  -s standardFlowEnabled=true \
  -s implicitFlowEnabled=false \
  -s directAccessGrantsEnabled=false \
  -s serviceAccountsEnabled=false \
  -s fullScopeAllowed=false \
  -s 'redirectUris=["http://localhost:5173","http://localhost:5173/*","https://dams5-07.students.polytech.lange.xyz/backoffice","https://dams5-07.students.polytech.lange.xyz/backoffice/*"]' \
  -s 'webOrigins=["http://localhost:5173","https://dams5-07.students.polytech.lange.xyz"]' \
  -s 'attributes."pkce.code.challenge.method"=S256' \
  -s 'attributes."login_theme"=okit-seller' \
  -s 'defaultClientScopes=["basic","profile","email","roles","web-origins"]' \
  -s 'optionalClientScopes=["offline_access"]' >/dev/null

ensure_direct_roles_mapper "$SELLER_CLIENT_UUID"

DEFAULT_ROLE="default-roles-$REALM"
if ! kcadm get-roles -r "$REALM" --rname "$DEFAULT_ROLE" \
  --fields name --format csv --noquotes | grep -Fx "$BUYER_ROLE" >/dev/null; then
  kcadm add-roles -r "$REALM" --rname "$DEFAULT_ROLE" --rolename "$BUYER_ROLE"
fi

if kcadm get-roles -r "$REALM" --rname "$DEFAULT_ROLE" \
  --fields name --format csv --noquotes | grep -Fx "$SELLER_ROLE" >/dev/null; then
  kcadm remove-roles -r "$REALM" --rname "$DEFAULT_ROLE" --rolename "$SELLER_ROLE"
fi

for client_uuid_value in "$MOBILE_CLIENT_UUID" "$SELLER_CLIENT_UUID"; do
  ensure_scope_mapping "$client_uuid_value" "$BUYER_ROLE"
  ensure_scope_mapping "$client_uuid_value" "$SELLER_ROLE"
done

kcadm get "roles/$BUYER_ROLE" -r "$REALM" --fields name >/dev/null
kcadm get "roles/$SELLER_ROLE" -r "$REALM" --fields name >/dev/null
kcadm get "clients/$MOBILE_CLIENT_UUID" -r "$REALM" \
  --fields clientId,enabled,publicClient,standardFlowEnabled,directAccessGrantsEnabled,fullScopeAllowed >/dev/null
kcadm get "clients/$SELLER_CLIENT_UUID" -r "$REALM" \
  --fields clientId,enabled,publicClient,standardFlowEnabled,directAccessGrantsEnabled,fullScopeAllowed >/dev/null

echo "Realm '$REALM', roles '$BUYER_ROLE'/'$SELLER_ROLE' and clients '$MOBILE_CLIENT_ID'/'$SELLER_CLIENT_ID' reconciled successfully."
KEYCLOAK_SCRIPT
