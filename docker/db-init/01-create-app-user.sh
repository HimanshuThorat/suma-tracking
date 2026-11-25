#!/bin/sh
# Runs ONCE, the first time the database container starts with an empty volume.
# - Creates the app user suma_app (NOT a superuser) with the password you typed in the start script.
# - Creates the database suma_tracking, owned by suma_app.
# - Takes away the password of the "postgres" superuser, so nobody can log in as it over the network.
#   (An administrator can still use it from inside the container: docker compose exec db psql -U postgres)
set -e
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres -v app_pw="$POSTGRES_PASSWORD" <<'EOSQL'
CREATE ROLE suma_app LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD :'app_pw';
CREATE DATABASE suma_tracking OWNER suma_app;
ALTER ROLE postgres PASSWORD NULL;
EOSQL
