#!/bin/sh

set -eu

: "${KC_BOOTSTRAP_ADMIN_USERNAME:?KC_BOOTSTRAP_ADMIN_USERNAME is required}"
: "${KC_BOOTSTRAP_ADMIN_PASSWORD:?KC_BOOTSTRAP_ADMIN_PASSWORD is required}"
: "${OKIT_MS_COMMANDE_CLIENT_SECRET:?OKIT_MS_COMMANDE_CLIENT_SECRET is required}"

KCADM=/opt/keycloak/bin/kcadm.sh
CONFIG=$(mktemp /tmp/kcadm-config.XXXXXX)
USER_PROFILE_CONFIG=$(mktemp /tmp/user-profile.XXXXXX)
SERVER_URL=${KEYCLOAK_SERVER_URL:-http://127.0.0.1:8080}
REALM=okit

cleanup() {
  rm -f "$CONFIG" "$USER_PROFILE_CONFIG"
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
  --user "$KC_BOOTSTRAP_ADMIN_USERNAME" \
  --password "$KC_BOOTSTRAP_ADMIN_PASSWORD" >/dev/null

if ! kcadm get "realms/$REALM" >/dev/null 2>&1; then
  echo "Realm '$REALM' is missing; the versioned import must complete first." >&2
  exit 1
fi

kcadm update "realms/$REALM" \
  -s enabled=true \
  -s sslRequired=external \
  -s registrationAllowed=true \
  -s registrationEmailAsUsername=true \
  -s resetPasswordAllowed=false \
  -s rememberMe=true \
  -s loginWithEmailAllowed=true \
  -s duplicateEmailsAllowed=false \
  -s verifyEmail=false \
  -s 'smtpServer={}' >/dev/null

cat >"$USER_PROFILE_CONFIG" <<'USER_PROFILE_JSON'
{
  "unmanagedAttributePolicy": "ADMIN_EDIT",
  "attributes": [
    {
      "name": "username",
      "displayName": "${username}",
      "validations": {
        "length": { "min": 3, "max": 255 },
        "username-prohibited-characters": {},
        "up-username-not-idn-homograph": {}
      },
      "permissions": { "view": ["admin", "user"], "edit": ["admin", "user"] },
      "multivalued": false
    },
    {
      "name": "email",
      "displayName": "${email}",
      "validations": { "email": {}, "length": { "max": 255 } },
      "required": { "roles": ["user"] },
      "permissions": { "view": ["admin", "user"], "edit": ["admin", "user"] },
      "multivalued": false
    },
    {
      "name": "firstName",
      "displayName": "${firstName}",
      "validations": { "length": { "max": 255 }, "person-name-prohibited-characters": {} },
      "permissions": { "view": ["admin"], "edit": ["admin"] },
      "multivalued": false
    },
    {
      "name": "lastName",
      "displayName": "${lastName}",
      "validations": { "length": { "max": 255 }, "person-name-prohibited-characters": {} },
      "permissions": { "view": ["admin"], "edit": ["admin"] },
      "multivalued": false
    }
  ],
  "groups": []
}
USER_PROFILE_JSON

kcadm update users/profile -r "$REALM" -f "$USER_PROFILE_CONFIG" >/dev/null

ensure_role() {
  role_name=$1
  role_description=$2
  if ! kcadm get "roles/$role_name" -r "$REALM" >/dev/null 2>&1; then
    kcadm create roles -r "$REALM" -s "name=$role_name" >/dev/null
  fi
  kcadm update "roles/$role_name" -r "$REALM" -s "description=$role_description" >/dev/null
}

client_uuid() {
  kcadm get clients -r "$REALM" -q "clientId=$1" --fields id --format csv --noquotes
}

ensure_client() {
  client_id=$1
  if [ -z "$(client_uuid "$client_id")" ]; then
    kcadm create clients -r "$REALM" -s "clientId=$client_id" >/dev/null
  fi
}

ensure_scope_mapping() {
  client_id=$1
  role_name=$2
  if ! kcadm get "clients/$client_id/scope-mappings/realm" -r "$REALM" \
    --fields name --format csv --noquotes | grep -Fx "$role_name" >/dev/null; then
    role_id=$(kcadm get "roles/$role_name" -r "$REALM" --fields id --format csv --noquotes)
    kcadm create "clients/$client_id/scope-mappings/realm" -r "$REALM" \
      --body "[{\"id\":\"$role_id\",\"name\":\"$role_name\"}]" >/dev/null
  fi
}

ensure_direct_roles_mapper() {
  client_id=$1
  mapper_name=okit-direct-realm-roles
  if ! kcadm get "clients/$client_id/protocol-mappers/models" -r "$REALM" \
    --fields name --format csv --noquotes | grep -Fx "$mapper_name" >/dev/null; then
    kcadm create "clients/$client_id/protocol-mappers/models" -r "$REALM" \
      -s "name=$mapper_name" \
      -s protocol=openid-connect \
      -s protocolMapper=okit-direct-realm-roles-mapper \
      -s consentRequired=false >/dev/null
  fi
}

ensure_role BUYER "Acheteur autorisé à utiliser l'application mobile O'kit"
ensure_role SELLER "Employé O'kit autorisé à utiliser le backoffice vendeur"
ensure_role SVC_COMMANDE "Compte de service de ms-commande"

ensure_client okit-mobile
mobile_uuid=$(client_uuid okit-mobile)
kcadm update "clients/$mobile_uuid" -r "$REALM" \
  -s clientId=okit-mobile \
  -s "name=O'kit mobile" \
  -s enabled=true -s protocol=openid-connect -s publicClient=true -s bearerOnly=false \
  -s standardFlowEnabled=true -s implicitFlowEnabled=false \
  -s directAccessGrantsEnabled=false -s serviceAccountsEnabled=false -s fullScopeAllowed=false \
  -s 'redirectUris=["okit://auth","http://localhost:19006","http://localhost:19006/*","https://dams5-07.students.polytech.lange.xyz","https://dams5-07.students.polytech.lange.xyz/*"]' \
  -s 'webOrigins=["http://localhost:19006","https://dams5-07.students.polytech.lange.xyz"]' \
  -s 'attributes."pkce.code.challenge.method"=S256' \
  -s 'defaultClientScopes=["basic","profile","email","roles","web-origins"]' \
  -s 'optionalClientScopes=["address","phone","offline_access"]' >/dev/null

ensure_client okit-seller-web
seller_uuid=$(client_uuid okit-seller-web)
kcadm update "clients/$seller_uuid" -r "$REALM" \
  -s clientId=okit-seller-web \
  -s "name=O'kit seller backoffice" \
  -s enabled=true -s protocol=openid-connect -s publicClient=true -s bearerOnly=false \
  -s standardFlowEnabled=true -s implicitFlowEnabled=false \
  -s directAccessGrantsEnabled=false -s serviceAccountsEnabled=false -s fullScopeAllowed=false \
  -s 'redirectUris=["http://localhost:5173","http://localhost:5173/*","https://dams5-07.students.polytech.lange.xyz/backoffice","https://dams5-07.students.polytech.lange.xyz/backoffice/*"]' \
  -s 'webOrigins=["http://localhost:5173","https://dams5-07.students.polytech.lange.xyz"]' \
  -s 'attributes."pkce.code.challenge.method"=S256' \
  -s 'attributes."login_theme"=okit-seller' \
  -s 'defaultClientScopes=["basic","profile","email","roles","web-origins"]' \
  -s 'optionalClientScopes=["offline_access"]' >/dev/null
ensure_direct_roles_mapper "$seller_uuid"

ensure_client ms-commande
service_uuid=$(client_uuid ms-commande)
kcadm update "clients/$service_uuid" -r "$REALM" \
  -s clientId=ms-commande \
  -s "name=ms-commande (service)" \
  -s enabled=true -s protocol=openid-connect -s publicClient=false -s bearerOnly=false \
  -s clientAuthenticatorType=client-secret \
  -s "secret=$OKIT_MS_COMMANDE_CLIENT_SECRET" \
  -s standardFlowEnabled=false -s implicitFlowEnabled=false \
  -s directAccessGrantsEnabled=false -s serviceAccountsEnabled=true -s fullScopeAllowed=false \
  -s 'defaultClientScopes=["roles"]' >/dev/null

default_role="default-roles-$REALM"
if ! kcadm get-roles -r "$REALM" --rname "$default_role" \
  --fields name --format csv --noquotes | grep -Fx BUYER >/dev/null; then
  kcadm add-roles -r "$REALM" --rname "$default_role" --rolename BUYER >/dev/null
fi
if kcadm get-roles -r "$REALM" --rname "$default_role" \
  --fields name --format csv --noquotes | grep -Fx SELLER >/dev/null; then
  kcadm remove-roles -r "$REALM" --rname "$default_role" --rolename SELLER >/dev/null
fi

for client_id in "$mobile_uuid" "$seller_uuid"; do
  ensure_scope_mapping "$client_id" BUYER
  ensure_scope_mapping "$client_id" SELLER
done
ensure_scope_mapping "$service_uuid" SVC_COMMANDE

service_account_username=$(kcadm get "clients/$service_uuid/service-account-user" -r "$REALM" \
  --fields username --format csv --noquotes)
if ! kcadm get-roles -r "$REALM" --uusername "$service_account_username" \
  --fields name --format csv --noquotes | grep -Fx SVC_COMMANDE >/dev/null; then
  kcadm add-roles -r "$REALM" --uusername "$service_account_username" \
    --rolename SVC_COMMANDE >/dev/null
fi

kcadm get "roles/BUYER" -r "$REALM" --fields name >/dev/null
kcadm get "roles/SELLER" -r "$REALM" --fields name >/dev/null
kcadm get "clients/$mobile_uuid" -r "$REALM" --fields clientId,enabled,publicClient >/dev/null
kcadm get "clients/$seller_uuid" -r "$REALM" --fields clientId,enabled,publicClient >/dev/null
kcadm get "clients/$service_uuid" -r "$REALM" --fields clientId,enabled,serviceAccountsEnabled >/dev/null

echo "Realm '$REALM' reconciled: roles and OAuth2 clients are ready."
