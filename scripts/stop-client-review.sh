#!/usr/bin/env bash
set -euo pipefail
# These containers are created only by start-client-review.sh and have no persistent volumes.
prefix="${REVIEW_PREFIX:-tadpoles-client-review}"
[[ "$prefix" =~ ^tadpoles-[a-z0-9-]+$ ]] || exit 1
docker rm -f "$prefix-ui" "$prefix-api" "$prefix-db" "$prefix-provider"
docker network rm "$prefix"
