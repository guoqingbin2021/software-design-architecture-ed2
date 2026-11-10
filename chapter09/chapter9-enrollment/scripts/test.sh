#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
(cd frontend && npm ci && npm test && npm run build)
if [ "${1:-}" = "--integration" ]; then
  (cd backend && mvn verify)
else
  (cd backend && mvn test)
fi
