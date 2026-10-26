#!/bin/sh
# Runs once, when the Postgres volume is first initialised. Each service gets its own role
# and database, and connections to each database are limited to its owner, so neither
# service can reach the other's data.
set -eu

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
  -v catalog_password="$CATALOG_DB_PASSWORD" \
  -v media_password="$MEDIA_DB_PASSWORD" <<'SQL'
CREATE ROLE catalog LOGIN PASSWORD :'catalog_password';
CREATE DATABASE catalog OWNER catalog;
CREATE ROLE media LOGIN PASSWORD :'media_password';
CREATE DATABASE media OWNER media;
-- New databases let every role connect by default; restrict each to its owner.
REVOKE CONNECT ON DATABASE catalog FROM PUBLIC;
REVOKE CONNECT ON DATABASE media FROM PUBLIC;
SQL
