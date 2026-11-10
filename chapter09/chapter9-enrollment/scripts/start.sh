#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
docker compose up --build -d
printf '%s\n' '默认页面：http://localhost:8081；后端日志：docker compose logs -f backend'
