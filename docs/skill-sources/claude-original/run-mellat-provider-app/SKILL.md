---
name: run-mellat-provider-app
description: Build, launch, and drive mellatProviderApp (a Spring Boot microservice with no app-level auth) — start Redis, run the jar, and curl its endpoints. Use when asked to run/start/build/test the app or verify a change against the live server.
---

mellatProviderApp is a headless Spring Boot API (no UI) with no app-level authentication at all - it's only ever called by other in-cluster microservices, gated entirely by Kubernetes NetworkPolicy. It exposes two domain REST resources - `/api/base-data/**` (province/city/income-range/education-level/branch lookups) and `/api/customers/**` (customer-onboarding submission/status, named/shaped to mirror bmi-provider-service's own resource of the same name) - plus actuator endpoints under `/management/**`, all `permitAll()`. Drive it via `.claude/skills/run-mellat-provider-app/smoke.sh`, run from the repo root. All paths below are relative to the repo root.

## Prerequisites

Java 21, Docker, and a working Python (used inline for JSON/JWT parsing) — all already present in this container. Nothing extra to `apt-get install`. On a fresh Windows checkout, `python3` can resolve to a Microsoft Store stub that fails at runtime rather than being absent from `PATH` - `smoke.sh` detects this itself (tries `python3 --version`, falls back to `python`) and there's nothing to configure by hand.

The app's downstream infra image is pulled through an internal mirror in this environment, not directly from Docker Hub:

```bash
docker pull docker.mofid.dev/redis:8.8.0 && docker tag docker.mofid.dev/redis:8.8.0 redis:8.8.0
```
(A direct pull of `redis:8.8.0` 403s/times out from this network — retag from the mirror instead, matching the name `src/main/docker/redis.yml` expects.)

## Build

`mvnw` isn't executable in a fresh checkout of this branch (`Permission denied` on first run) — fix once with `chmod +x mvnw`.

```bash
chmod +x mvnw
./mvnw -q -DskipTests clean package
```
Produces `target/mellat-provider-app-0.0.1-SNAPSHOT.jar`. First run took ~1 min.

## Run (agent path)

Everything below is one script: `.claude/skills/run-mellat-provider-app/smoke.sh`. It builds, brings up infra, launches the jar, and runs a curl-based smoke test proving public/protected/RBAC routing all work.

```bash
.claude/skills/run-mellat-provider-app/smoke.sh all
```

This runs, in order:

| step | what it does |
|---|---|
| `build` | `./mvnw -q -DskipTests clean package` |
| `infra` | `docker compose -f src/main/docker/redis.yml up -d` |
| `run` | `nohup java -jar target/mellat-provider-app-*.jar --spring.profiles.active=dev > /tmp/mellat-provider-app.log 2>&1 &` |
| `wait` | polls `http://localhost:8088/mellat-provider/management/health` until it responds |
| `smoke` | the curl checks below |

Each step is also callable on its own (useful when infra is already up):

```bash
.claude/skills/run-mellat-provider-app/smoke.sh infra   # idempotent, safe to re-run
.claude/skills/run-mellat-provider-app/smoke.sh run
.claude/skills/run-mellat-provider-app/smoke.sh wait
.claude/skills/run-mellat-provider-app/smoke.sh smoke
```

`smoke` prints one `PASS`/`FAIL` line per check and exits non-zero if anything failed. Verified output on a clean run:

```
PASS  public health -> 200
PASS  public info -> 200
PASS  unimplemented /api/authenticate (JHipster's own login endpoint was never added) -> 404
PASS  /api/base-data/provinces needs no token -> 200 (past auth)
PASS  /api/customers/track/x needs no token -> 400 (past auth)
PASS  /management/env needs no token -> 200
PASS  /v3/api-docs is open (no token needed) -> 200
```

That's the real behavior under test in this repo (`SecurityConfiguration`'s `.anyRequest().permitAll()`), not just "the server started." The domain-resource checks only assert the request got past the security filter (not a specific success code), since they call out to a real Mellat sandbox this smoke test doesn't control.

## Crawling Mellat's own base data (DBS-1747)

`address-service` needs a one-time, offline snapshot of Bank Mellat's own province/city codes (to build its `MELLAT` institution-mapping seed data) - not a live per-request lookup. Rather than re-implementing Mellat's login flow (RS256 client-assertion JWT, see `MellatAuthTokenService`) in a second, throwaway script, the crawl goes through this app's own endpoints (no token needed):

```bash
.claude/skills/run-mellat-provider-app/smoke.sh crawl [outfile]   # default outfile: /tmp/mellat-base-data-crawl.json
```

Requires the app already running (`smoke.sh run` + `wait`) against Bank Mellat's **real** sandbox - `feign.mellat.*.base-url` must point at the actual environment, not the `DO_NOT_CALL` test placeholder. `application-dev.yml`'s own default (`https://stake.atlasdtco.com`) already is the real dev sandbox, so a plain `dev`-profile run normally just works, network access permitting. Fetches `GET /api/base-data/provinces`, then `GET /api/base-data/provinces/{id}/cities` for every province, and writes one combined JSON file (`{"provinces": [...], "citiesByProvince": {...}, "failedProvinceIds": [...]}`) via `crawl_base_data.py` (stdlib-only, no `requests`/`jq` needed). Exits non-zero and lists which provinces failed if any city lookup errored out - re-run once connectivity is fixed rather than treating a partial crawl as complete.

Stopping:

```bash
.claude/skills/run-mellat-provider-app/smoke.sh stop   # kills whatever's listening on :8088 (lsof, or netstat/taskkill on Windows where lsof isn't available)
.claude/skills/run-mellat-provider-app/smoke.sh down    # stop + docker compose down redis
```

## Reproducing the Mellat sandbox INVALID_TOKEN bug (no app needed)

`.claude/skills/run-mellat-provider-app/repro-invalid-token.sh` is a standalone curl+openssl script (no JVM, no app, no Redis) that signs the same RS256 client-assertion JWT `ClientAssertionJwtGenerator` does, logs into Mellat's real `/auth/login`, and calls `GET base-data/provinces` with the resulting token - reproducing a real, confirmed sandbox bug found via DBS-1747's crawl work: the token `/auth/login` issues (signed by `http://192.168.10.53:30888/realms/baas-realm`, per its own `iss` claim) is rejected by the base-data endpoint with `401 INVALID_TOKEN: "Token validation failed with all available public keys."` - the auth and base-data services don't appear to share a JWKS. Meant to be handed to Bank Mellat's own team as a minimal, app-independent repro.

## Testing Bank Mellat's sandbox directly from Postman

`.claude/skills/run-mellat-provider-app/mellat-sandbox-repro.postman.json` is an importable Postman collection that calls Bank Mellat's real sandbox directly - not this service's own API (see `postman/mellat-provider-service.postman_collection.json` for that, a separate, unrelated collection). Covers the full login flow (client-assertion JWT signed live in a pre-request script via the Web Crypto API) plus every base-data lookup and account-opening/tracking - i.e. everything `MellatBaseDataClient`/`MellatAccountClient` call. Useful for isolating whether a problem is on Mellat's side without this service in the loop at all.

## Direct invocation (the actual business logic)

The one piece of app-specific logic in this repo — `client/auth/MellatAuthTokenService` + `client/auth/Auth` (Feign client that RS256-signs a client-assertion JWT and posts it to Bank Mellat's real auth endpoint) — has **no HTTP entry point** yet and calls an external bank sandbox this container can't reach. It's covered by unit/slice tests, not the running server. To exercise it directly:

```bash
./mvnw test -Dtest=MellatAuthTokenServiceTest
```

## Run (human path)

```bash
./mvnw   # blocks, dev profile active by default, Ctrl-C to stop
```
Needs Redis already up (`smoke.sh infra`) — same requirement as the agent path, just without the background launch or the curl checks.

## Test

```bash
./mvnw test -Dtest=ClassName            # single class
./mvnw checkstyle:check                 # nohttp/checkstyle, fast
./mvnw verify                           # full suite: unit + integration + cucumber + archunit
```
`checkstyle:check` and individual unit test classes (e.g. `MellatAuthTokenServiceTest`) pass cleanly in seconds. `./mvnw verify` (the full suite, including Testcontainers-backed IT tests) needs Docker reachable but no other infra running - it starts/tears down its own Redis/WireMock containers per test class.

---

## Gotchas

- **`/api/authenticate` 404s, not 200.** It's listed as a public path in `SecurityConfiguration`, but no controller implements it - this service never added the JHipster login-form endpoint (there's no login at all anymore). A 404 here is expected, not a bug.
- **Docker Hub pulls fail from this network** (`403 Forbidden` / TLS handshake timeout) — pull through `docker.mofid.dev` and retag locally (see Prerequisites) instead of editing the compose files.
- **A local `HTTP_PROXY`/`HTTPS_PROXY` env var can silently break every `localhost` call** (`curl`/Python's `urllib` both honor it by default) - symptoms are a `502`/`000` instead of the real response, even though the app is genuinely up. Set `NO_PROXY=localhost,127.0.0.1` (and `no_proxy` lowercase, some tools only read one or the other) before running `smoke.sh` if this looks familiar.
