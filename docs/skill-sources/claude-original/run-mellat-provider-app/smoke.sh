#!/usr/bin/env bash
# Driver for running/driving mellatProviderApp end-to-end (build, infra, launch, curl smoke checks).
# Usage: .claude/skills/run-mellat-provider-app/smoke.sh <command>
#   build   - mvnw package (skip tests)
#   infra   - start redis via docker compose
#   run     - launch the packaged jar in the background with the dev profile
#   wait    - poll /management/health until UP
#   smoke   - run the curl checks against a running instance, print PASS/FAIL
#   crawl [outfile] - one-time crawl of Mellat's own province/city base data through
#                      this app's own /api/base-data/* endpoints (no token needed - see
#                      DBS-1747); writes combined JSON to outfile (default
#                      /tmp/mellat-base-data-crawl.json). Requires the app to be
#                      running against Bank Mellat's REAL sandbox, not the fake
#                      DO_NOT_CALL test placeholder - see feign.mellat.*.base-url.
#   all     - build + infra + run + wait + smoke
#   stop    - kill the running app (by port)
#   down    - stop app + docker compose containers
#
# Run from the repo root (mellatProviderApp/).

set -euo pipefail
cd "$(dirname "$0")/../../../"

APP_PORT=8088
# /mellat-provider is server.servlet.context-path (application.yml) - applied
# unconditionally in every profile. Every check below needs it; omitting it
# 404s rather than 200ing.
APP_URL=http://localhost:${APP_PORT}/mellat-provider
LOG=/tmp/mellat-provider-app.log
JAR=$(ls target/mellat-provider-app-*.jar 2>/dev/null | grep -v sources | head -1 || true)

# `python3` on a fresh Windows checkout can resolve to a Microsoft Store stub
# that fails at runtime rather than being genuinely absent from PATH (so a
# plain `command -v python3` check doesn't catch it) - actually try running
# it before trusting it, falling back to `python`.
if python3 --version > /dev/null 2>&1; then
  PYTHON=python3
else
  PYTHON=python
fi

build() {
  ./mvnw -q -DskipTests clean package
}

infra() {
  docker compose -f src/main/docker/redis.yml up -d
}

run() {
  [ -n "$JAR" ] || { echo "no jar found under target/ - run 'build' first" >&2; return 1; }
  nohup java -jar "$JAR" --spring.profiles.active=dev > "$LOG" 2>&1 &
  echo "launched pid $! -> logs at $LOG"
}

wait_up() {
  for i in $(seq 1 30); do
    curl -sf "${APP_URL}/management/health" > /dev/null 2>&1 && { echo "app is up"; return 0; }
    sleep 2
  done
  echo "app did not come up in time, see $LOG" >&2
  return 1
}

smoke() {
  local fail=0
  check() {
    local desc="$1" expected="$2"; shift 2
    local got
    got=$(curl -s -o /dev/null -w "%{http_code}" "$@")
    if [ "$got" = "$expected" ]; then
      echo "PASS  $desc -> $got"
    else
      echo "FAIL  $desc -> got $got, expected $expected"
      fail=1
    fi
  }
  # Like check(), but only asserts the request got PAST the security filter
  # (not 401/403) rather than a specific success code - used for endpoints
  # that call out to a real external sandbox (e.g. Mellat) this smoke test
  # doesn't control, so a downstream 502/503 there doesn't fail this check.
  check_authenticated() {
    local desc="$1"; shift
    local got
    got=$(curl -s -o /dev/null -w "%{http_code}" "$@")
    if [ "$got" != "401" ] && [ "$got" != "403" ]; then
      echo "PASS  $desc -> $got (past auth)"
    else
      echo "FAIL  $desc -> got $got, expected anything but 401/403"
      fail=1
    fi
  }

  check "public health"              200 "${APP_URL}/management/health"
  check "public info"                200 "${APP_URL}/management/info"
  check "unimplemented /api/authenticate (JHipster's own login endpoint was never added)" 404 "${APP_URL}/api/authenticate"
  # No app-level auth at all anywhere (SecurityConfiguration is
  # .anyRequest().permitAll()) - this service is only ever called by other
  # in-cluster microservices, gated entirely by Kubernetes NetworkPolicy. The
  # two domain resources also call out to a real Mellat sandbox this smoke
  # test doesn't control, so check_authenticated (not a specific status) is
  # what actually matters here: no token needed, and no 401/403 possible.
  check_authenticated "/api/base-data/provinces needs no token" "${APP_URL}/api/base-data/provinces"
  check_authenticated "/api/customers/track/x needs no token" "${APP_URL}/api/customers/track/x"
  check "/management/env needs no token"  200 "${APP_URL}/management/env"
  check "/v3/api-docs is open (no token needed)" 200 "${APP_URL}/v3/api-docs"

  return $fail
}

crawl() {
  local out="${1:-/tmp/mellat-base-data-crawl.json}"
  "$PYTHON" "$(dirname "$0")/crawl_base_data.py" "${APP_URL}" "${out}"
}

stop() {
  # lsof isn't available in Git Bash on Windows - fall back to netstat/taskkill
  # there instead of silently doing nothing (found the hard way: stop/down
  # left the app running with no error, only visible via a stale port bind).
  if command -v lsof > /dev/null 2>&1; then
    lsof -ti:${APP_PORT} -sTCP:LISTEN | xargs -r kill
  else
    local pid
    pid=$(netstat -ano 2>/dev/null | grep ":${APP_PORT} " | grep LISTENING | awk '{print $5}' | head -1)
    [ -n "$pid" ] && taskkill //F //PID "$pid" > /dev/null 2>&1
  fi
}

down() {
  stop || true
  docker compose -f src/main/docker/redis.yml down
}

case "${1:-}" in
  build) build ;;
  infra) infra ;;
  run) run ;;
  wait) wait_up ;;
  smoke) smoke ;;
  crawl) crawl "${2:-}" ;;
  all)
    build
    infra
    run
    wait_up
    smoke
    ;;
  stop) stop ;;
  down) down ;;
  *)
    echo "usage: $0 {build|infra|run|wait|smoke|crawl [outfile]|all|stop|down}" >&2
    exit 1
    ;;
esac
