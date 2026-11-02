#!/bin/sh

set -eu

: "${KEYCLOAK_ADMIN_PASSWORD:?Define KEYCLOAK_ADMIN_PASSWORD in the environment}"
: "${SELLER_EMAIL:?Define SELLER_EMAIL in the environment}"
: "${SELLER_TEMPORARY_PASSWORD:?Define SELLER_TEMPORARY_PASSWORD in the environment}"

KEYCLOAK_ADMIN_USERNAME=${KEYCLOAK_ADMIN_USERNAME:-admin}
SELLER_FIRST_NAME=${SELLER_FIRST_NAME:-}
SELLER_LAST_NAME=${SELLER_LAST_NAME:-}
SELLER_PASSWORD_TEMPORARY=${SELLER_PASSWORD_TEMPORARY:-true}

case "$SELLER_EMAIL" in
  *@*) ;;
  *)
    echo 'SELLER_EMAIL must be an email address.' >&2
    exit 1
    ;;
esac

case "$SELLER_PASSWORD_TEMPORARY" in
  true|false) ;;
  *)
    echo 'SELLER_PASSWORD_TEMPORARY must be true or false.' >&2
    exit 1
    ;;
esac

SCRIPT_DIRECTORY=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
REPOSITORY_ROOT=$(CDPATH= cd -- "$SCRIPT_DIRECTORY/../.." && pwd)

export OKIT_KEYCLOAK_ADMIN_USERNAME=$KEYCLOAK_ADMIN_USERNAME
export OKIT_KEYCLOAK_ADMIN_PASSWORD=$KEYCLOAK_ADMIN_PASSWORD
export OKIT_SELLER_EMAIL=$SELLER_EMAIL
export OKIT_SELLER_FIRST_NAME=$SELLER_FIRST_NAME
export OKIT_SELLER_LAST_NAME=$SELLER_LAST_NAME
export OKIT_SELLER_TEMPORARY_PASSWORD=$SELLER_TEMPORARY_PASSWORD
export OKIT_SELLER_PASSWORD_TEMPORARY=$SELLER_PASSWORD_TEMPORARY

cd "$REPOSITORY_ROOT"

./infra/keycloak/reconcile-local-realm.sh >/dev/null

docker compose exec -T \
  -e OKIT_KEYCLOAK_ADMIN_USERNAME \
  -e OKIT_KEYCLOAK_ADMIN_PASSWORD \
  -e OKIT_SELLER_EMAIL \
  -e OKIT_SELLER_FIRST_NAME \
  -e OKIT_SELLER_LAST_NAME \
  -e OKIT_SELLER_TEMPORARY_PASSWORD \
  -e OKIT_SELLER_PASSWORD_TEMPORARY \
  keycloak sh -s <<'KEYCLOAK_SCRIPT'
set -eu

KCADM=/opt/keycloak/bin/kcadm.sh
CONFIG=$(mktemp)
REALM=okit
CREATED_USER_ID=

cleanup() {
  status=$?
  if [ "$status" -ne 0 ] && [ -n "$CREATED_USER_ID" ]; then
    "$KCADM" delete "users/$CREATED_USER_ID" --config "$CONFIG" -r "$REALM" >/dev/null 2>&1 || true
  fi
  rm -f "$CONFIG"
  exit "$status"
}
trap cleanup EXIT

"$KCADM" config credentials \
  --config "$CONFIG" \
  --server http://127.0.0.1:8080 \
  --realm master \
  --user "$OKIT_KEYCLOAK_ADMIN_USERNAME" \
  --password "$OKIT_KEYCLOAK_ADMIN_PASSWORD" >/dev/null

existing_user_id=$("$KCADM" get users --config "$CONFIG" -r "$REALM" \
  -q "username=$OKIT_SELLER_EMAIL" --fields id --format csv --noquotes)
if [ -n "$existing_user_id" ]; then
  echo "A Keycloak account already exists for $OKIT_SELLER_EMAIL." >&2
  exit 1
fi

CREATED_USER_ID=$("$KCADM" create users --config "$CONFIG" -r "$REALM" \
  -s "username=$OKIT_SELLER_EMAIL" \
  -s "email=$OKIT_SELLER_EMAIL" \
  -s "firstName=$OKIT_SELLER_FIRST_NAME" \
  -s "lastName=$OKIT_SELLER_LAST_NAME" \
  -s enabled=true \
  -s emailVerified=false \
  -i)

if [ "$OKIT_SELLER_PASSWORD_TEMPORARY" = true ]; then
  "$KCADM" set-password --config "$CONFIG" -r "$REALM" \
    --userid "$CREATED_USER_ID" \
    --new-password "$OKIT_SELLER_TEMPORARY_PASSWORD" \
    --temporary >/dev/null
else
  "$KCADM" set-password --config "$CONFIG" -r "$REALM" \
    --userid "$CREATED_USER_ID" \
    --new-password "$OKIT_SELLER_TEMPORARY_PASSWORD" >/dev/null
fi

if "$KCADM" get-roles --config "$CONFIG" -r "$REALM" --uid "$CREATED_USER_ID" \
  --fields name --format csv --noquotes | grep -Fx BUYER >/dev/null; then
  "$KCADM" remove-roles --config "$CONFIG" -r "$REALM" \
    --uid "$CREATED_USER_ID" --rolename BUYER >/dev/null
fi

"$KCADM" add-roles --config "$CONFIG" -r "$REALM" \
  --uid "$CREATED_USER_ID" --rolename SELLER >/dev/null

direct_roles=$("$KCADM" get-roles --config "$CONFIG" -r "$REALM" --uid "$CREATED_USER_ID" \
  --fields name --format csv --noquotes)
printf '%s\n' "$direct_roles" | grep -Fx SELLER >/dev/null
if printf '%s\n' "$direct_roles" | grep -Fx BUYER >/dev/null; then
  echo 'The seller account still has BUYER directly assigned after provisioning.' >&2
  exit 1
fi

CREATED_USER_ID=
echo "Seller account created for $OKIT_SELLER_EMAIL with SELLER directly assigned."
KEYCLOAK_SCRIPT
