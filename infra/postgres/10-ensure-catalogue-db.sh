#!/bin/sh
set -eu

catalogue_db_name="${CATALOGUE_DB_NAME:-okit_catalogue}"
catalogue_db_username="${CATALOGUE_DB_USERNAME:-okit_catalogue}"
catalogue_db_password="${CATALOGUE_DB_PASSWORD:?Define CATALOGUE_DB_PASSWORD in .env}"

psql \
  --set=ON_ERROR_STOP=1 \
  --username "$POSTGRES_USER" \
  --dbname "$POSTGRES_DB" \
  --set=catalogue_db_name="$catalogue_db_name" \
  --set=catalogue_db_username="$catalogue_db_username" \
  --set=catalogue_db_password="$catalogue_db_password" <<'SQL'
SELECT format(
  'CREATE ROLE %I LOGIN PASSWORD %L',
  :'catalogue_db_username',
  :'catalogue_db_password'
)
WHERE NOT EXISTS (
  SELECT FROM pg_catalog.pg_roles WHERE rolname = :'catalogue_db_username'
) \gexec

SELECT format(
  'ALTER ROLE %I PASSWORD %L',
  :'catalogue_db_username',
  :'catalogue_db_password'
) \gexec

SELECT format(
  'CREATE DATABASE %I OWNER %I',
  :'catalogue_db_name',
  :'catalogue_db_username'
)
WHERE NOT EXISTS (
  SELECT FROM pg_database WHERE datname = :'catalogue_db_name'
) \gexec

SELECT format(
  'ALTER DATABASE %I OWNER TO %I',
  :'catalogue_db_name',
  :'catalogue_db_username'
) \gexec
SQL
