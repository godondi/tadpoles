#!/bin/sh
# Local Compose fixture startup; hardened deployments exclude this service.
set -eu

psql -v ON_ERROR_STOP=1 -f /role_workspaces.sql

if [ "${TRADING_LOCAL_DEMO_SETUP:-false}" = "true" ] &&
   [ "${OPERATIONS_ACCESS_ENABLED:-false}" = "true" ]; then
    psql -v ON_ERROR_STOP=1 -f /operations_workspace.sql
fi
