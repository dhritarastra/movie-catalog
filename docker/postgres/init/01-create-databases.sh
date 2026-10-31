#!/bin/sh
# Runs once on a new volume. Each service gets its own role and database and can't reach the other's.
set -eu

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
  -v catalog_password="$CATALOG_DB_PASSWORD" \
  -v media_password="$MEDIA_DB_PASSWORD" <<'SQL'
CREATE ROLE catalog LOGIN PASSWORD :'catalog_password';
CREATE DATABASE catalog OWNER catalog;
CREATE ROLE media LOGIN PASSWORD :'media_password';
CREATE DATABASE media OWNER media;
REVOKE CONNECT ON DATABASE catalog FROM PUBLIC;
REVOKE CONNECT ON DATABASE media FROM PUBLIC;
SQL
