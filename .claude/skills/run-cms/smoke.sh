#!/usr/bin/env bash
# Driver for the cms (card-management-service) Spring Boot API.
# Builds (if needed), launches the app in the background, waits for
# readiness, then runs a curl-based smoke flow. See SKILL.md for details.
#
# Usage:
#   .claude/skills/run-cms/smoke.sh start   # build+launch, wait for readiness, print PID/log
#   .claude/skills/run-cms/smoke.sh verify  # run the curl smoke flow against a running app
#   .claude/skills/run-cms/smoke.sh stop    # kill whatever is listening on the app port
#
# Must be run from the repo root (card-management-service/).

set -euo pipefail

PORT=8081
CONTEXT=cms
BASE="http://localhost:${PORT}/${CONTEXT}"
JAR=target/cms-service.jar
APP_LOG="${TMPDIR:-/tmp}/cms-app.log"
BUILD_LOG="${TMPDIR:-/tmp}/cms-build.log"

need_mirror_settings() {
  # repo.mofid.dev (the internal Artifactory declared in pom.xml/settings.xml)
  # is often unreachable from a sandboxed/offline container. When online
  # builds fail against it, retry once through a temp settings.xml that
  # mirrors everything to public Maven Central via the corporate proxy
  # (read from the existing http(s)_proxy env vars — nothing hardcoded).
  local f
  f="$(mktemp /tmp/cms-mirror-settings.XXXXXX.xml)"
  local proxy_block=""
  if [ -n "${https_proxy:-${HTTPS_PROXY:-}}" ]; then
    local p="${https_proxy:-${HTTPS_PROXY}}"
    # p looks like http://user:pass@host:port
    local hostport="${p#*://}"; hostport="${hostport#*@}"
    local userpass="${p#*://}"; userpass="${userpass%%@*}"
    local user="${userpass%%:*}"; local pass="${userpass#*:}"
    local host="${hostport%%:*}"; local port="${hostport##*:}"
    proxy_block="<proxies><proxy><id>corp-proxy</id><active>true</active><protocol>http</protocol><host>${host}</host><port>${port}</port><username>${user}</username><password>${pass}</password><nonProxyHosts>localhost|127.0.0.1</nonProxyHosts></proxy></proxies>"
  fi
  cat > "$f" <<EOF
<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0">
  ${proxy_block}
  <mirrors>
    <mirror>
      <id>central-mirror</id>
      <mirrorOf>central,snapshots,*</mirrorOf>
      <url>https://repo.maven.apache.org/maven2</url>
    </mirror>
  </mirrors>
</settings>
EOF
  echo "$f"
}

build() {
  if [ -f "$JAR" ]; then
    echo "Jar already built at $JAR, skipping build (rm it to force a rebuild)."
    return
  fi
  echo "Building $JAR (offline first, using the warm ~/.m2 cache)..."
  if mvn -o -ntp package -DskipTests -s settings.xml > "$BUILD_LOG" 2>&1; then
    echo "Offline build succeeded."
    return
  fi
  echo "Offline build failed (see $BUILD_LOG) - likely missing plugin deps." >&2
  echo "Retrying online through the central-mirror proxy workaround..." >&2
  local mirror_settings
  mirror_settings="$(need_mirror_settings)"
  if mvn -ntp package -DskipTests -s "$mirror_settings" > "$BUILD_LOG" 2>&1; then
    echo "Online build succeeded via mirror (this can take ~30-40 min the first time; ~/.m2 is warm afterwards)."
  else
    echo "Build failed. Last 60 lines of $BUILD_LOG:" >&2
    tail -60 "$BUILD_LOG" >&2
    exit 1
  fi
}

ensure_deps() {
  # dev profile expects MSSQL on localhost:1433 (database "onboarding") and
  # Redis on localhost:6379. Reuse anything already listening on those ports
  # before starting cms's own docker-compose stack - see Gotchas in SKILL.md.
  if ! (exec 3<>/dev/tcp/127.0.0.1/1433) 2>/dev/null; then
    echo "Nothing listening on :1433, starting src/main/docker/mssql.yml ..."
    docker compose -f src/main/docker/mssql.yml up --wait
  else
    exec 3>&- 2>/dev/null || true
    echo "Reusing existing service on :1433"
  fi
  if ! (exec 3<>/dev/tcp/127.0.0.1/6379) 2>/dev/null; then
    echo "Nothing listening on :6379, starting src/main/docker/redis.yml ..."
    docker compose -f src/main/docker/redis.yml up --wait
  else
    exec 3>&- 2>/dev/null || true
    echo "Reusing existing service on :6379"
  fi
}

start() {
  build
  ensure_deps
  echo "Launching: java -jar $JAR --spring.profiles.active=dev"
  nohup java -jar "$JAR" --spring.profiles.active=dev > "$APP_LOG" 2>&1 &
  local pid=$!
  echo "PID: $pid  Log: $APP_LOG"
  echo "Waiting for readiness..."
  for _ in $(seq 1 60); do
    if grep -qE "Started CmsApp|APPLICATION FAILED TO START" "$APP_LOG" 2>/dev/null; then
      break
    fi
    sleep 2
  done
  if grep -q "APPLICATION FAILED TO START" "$APP_LOG"; then
    echo "App failed to start. Log tail:" >&2
    tail -60 "$APP_LOG" >&2
    exit 1
  fi
  if ! grep -q "Started CmsApp" "$APP_LOG"; then
    echo "Timed out waiting for startup. Log tail:" >&2
    tail -60 "$APP_LOG" >&2
    exit 1
  fi
  echo "Started. PID=$pid"
}

verify() {
  echo "=== readiness ==="
  curl -sf "$BASE/management/health/readiness"; echo
  echo "=== swagger-ui ==="
  curl -s -o /dev/null -w "HTTP:%{http_code}\n" "$BASE/swagger-ui/index.html"
  echo "=== api-docs ==="
  curl -s -o /dev/null -w "HTTP:%{http_code}\n" "$BASE/v3/api-docs"

  echo "=== CRUD: lookup categories (no auth required - anonymous permitAll route) ==="
  echo "-- create --"
  curl -s -w "\nHTTP:%{http_code}\n" -X POST "$BASE/api/v1/basic/categories" \
    -H "Content-Type: application/json" \
    -d '{"code":"DRVR01","title":"Driver Smoke Test Category"}'
  echo "-- get --"
  curl -s -w "\nHTTP:%{http_code}\n" "$BASE/api/v1/basic/categories/DRVR01"
  echo "-- update --"
  curl -s -w "\nHTTP:%{http_code}\n" -X PUT "$BASE/api/v1/basic/categories/DRVR01" \
    -H "Content-Type: application/json" \
    -d '{"code":"DRVR01","title":"Driver Smoke Test Category Updated"}'
  echo "-- delete --"
  curl -s -w "\nHTTP:%{http_code}\n" -X DELETE "$BASE/api/v1/basic/categories/DRVR01"
  echo "-- get after delete (expect 404) --"
  curl -s -w "\nHTTP:%{http_code}\n" "$BASE/api/v1/basic/categories/DRVR01"

  echo "=== backoffice basic-auth gate ==="
  echo "-- no credentials (expect 401) --"
  curl -s -o /dev/null -w "HTTP:%{http_code}\n" -X POST "$BASE/api/v1/backoffice/cards/blocks" \
    -H "Content-Type: application/json" -d '{"pan":"1234567890123456","reason":"test"}'
  echo "-- with default dev credentials sadad-card:changeme (expect past-auth, e.g. 404 card-not-found) --"
  curl -s -o /dev/null -w "HTTP:%{http_code}\n" -u "sadad-card:changeme" -X POST "$BASE/api/v1/backoffice/cards/blocks" \
    -H "Content-Type: application/json" -d '{"pan":"1234567890123456","reason":"test"}'
}

stop() {
  local pids
  pids="$(lsof -ti:${PORT} -sTCP:LISTEN 2>/dev/null || true)"
  if [ -n "$pids" ]; then
    echo "Killing PID(s) on :${PORT}: $pids"
    kill $pids
  else
    echo "Nothing listening on :${PORT}"
  fi
}

case "${1:-}" in
  start) start ;;
  verify) verify ;;
  stop) stop ;;
  *) echo "Usage: $0 {start|verify|stop}" >&2; exit 1 ;;
esac
