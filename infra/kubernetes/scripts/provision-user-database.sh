#!/bin/sh

set -eu

NAMESPACE=${NAMESPACE:-polytech-dams5-07}
POSTGRES_WORKLOAD=${POSTGRES_WORKLOAD:-deployment/postgres-db}
USER_SECRET=${USER_SECRET:-user-service-secrets}

password_base64=$(kubectl -n "$NAMESPACE" get secret "$USER_SECRET" \
  -o jsonpath='{.data.database-password}')

if [ -z "$password_base64" ]; then
  echo "Secret $USER_SECRET does not contain database-password." >&2
  exit 1
fi

REMOTE_SCRIPT=$(cat <<'REMOTE_SCRIPT'
set -eu

IFS= read -r password_base64
app_db_password=$(printf '%s' "$password_base64" | base64 -d)
admin_user=${POSTGRES_USER:-postgres}

psql --username "$admin_user" --dbname postgres \
  --set ON_ERROR_STOP=1 \
  --set app_password="$app_db_password" <<'SQL'
SELECT format('CREATE ROLE okit_user LOGIN PASSWORD %L', :'app_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'okit_user') \gexec
SELECT format('ALTER ROLE okit_user WITH LOGIN PASSWORD %L', :'app_password') \gexec
SQL

if ! psql --username "$admin_user" --dbname postgres --tuples-only --no-align \
  --command "SELECT 1 FROM pg_database WHERE datname = 'okit_users'" | grep -qx 1; then
  createdb --username "$admin_user" --owner okit_user okit_users
fi

psql --username "$admin_user" --dbname postgres --set ON_ERROR_STOP=1 \
  --command 'ALTER DATABASE okit_users OWNER TO okit_user' >/dev/null

echo "Database okit_users and role okit_user are ready."
REMOTE_SCRIPT
)

printf '%s\n' "$password_base64" | \
  kubectl -n "$NAMESPACE" exec -i "$POSTGRES_WORKLOAD" -- sh -c "$REMOTE_SCRIPT"
