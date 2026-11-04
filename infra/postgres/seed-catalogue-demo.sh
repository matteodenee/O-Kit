#!/bin/sh
set -eu

catalogue_db_name="${CATALOGUE_DB_NAME:-okit_catalogue}"
seed_file="${1:-/opt/okit/catalogue-demo-data.sql}"

if [ ! -r "$seed_file" ]; then
  echo "Catalogue demo seed is not readable: $seed_file" >&2
  exit 1
fi

schema_ready="$(
  psql \
    --set=ON_ERROR_STOP=1 \
    --username "$POSTGRES_USER" \
    --dbname "$catalogue_db_name" \
    --tuples-only \
    --no-align \
    --command "SELECT to_regclass('public.produit') IS NOT NULL"
)"

if [ "$schema_ready" != "t" ]; then
  echo "Catalogue schema is missing. Start catalogue-service before running the demo seed." >&2
  exit 1
fi

psql \
  --set=ON_ERROR_STOP=1 \
  --username "$POSTGRES_USER" \
  --dbname "$catalogue_db_name" \
  --file "$seed_file"
