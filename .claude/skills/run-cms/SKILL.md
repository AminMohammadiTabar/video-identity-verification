---
name: run-cms
description: Build, run, and drive the cms card-management-service Spring Boot API. Use when asked to start cms, run it, build it, launch the backend, or curl/exercise its REST endpoints (lookup categories, backoffice card blocks, health/readiness, swagger).
---

`cms` is a JHipster/Spring Boot 3 (Java 21) microservice REST API — there
is no UI. Drive it with `.claude/skills/run-cms/smoke.sh`, which builds
the jar if needed, launches it in the background, waits for readiness,
and (in `verify` mode) runs a curl-based smoke flow. All paths below are
relative to the repo root.

## Prerequisites

Already present in a normal dev container: Java 21 (`java -version`),
system Maven (`mvn -version` — NOT `./mvnw`, see Gotchas), Docker, and a
warm `~/.m2/repository` cache. Nothing to `apt-get install` for a build —
this is a plain Maven/Java project.

The dev profile needs:
- **MSSQL** reachable at `localhost:1433`, database `onboarding` (yes,
  `onboarding`, not `cms` — that's what `application-dev.yml` points at).
- **Redis** reachable at `localhost:6379`.

`smoke.sh` checks both ports before starting anything and reuses
whatever's already listening there (see Gotchas — this box already had
both running for a sibling project). If neither is up, it falls back to
`docker compose -f src/main/docker/mssql.yml up --wait` and
`.../redis.yml up --wait`.

## Build

```bash
.claude/skills/run-cms/smoke.sh start
```

This builds `target/cms-service.jar` if it doesn't exist yet (skips the
build otherwise — `rm target/cms-service.jar` to force a rebuild), tries
offline first (`mvn -o -ntp package -DskipTests -s settings.xml`), and
only if that fails falls back to an online build through a temporary
Maven `settings.xml` that mirrors everything to public Maven Central
through the corporate proxy (read from `$https_proxy` — see Gotchas).
The offline path takes ~1 min with a warm cache; the online fallback can
take **~30-40 minutes** the first time it needs to fetch missing plugin
jars, but after that the `~/.m2` cache is warm and subsequent builds
(including the wrapper's own, once fixed) are fast and can go fully
offline.

## Run (agent path)

```bash
.claude/skills/run-cms/smoke.sh start   # build (if needed) + launch, wait for readiness
.claude/skills/run-cms/smoke.sh verify  # curl-based smoke flow against the running app
.claude/skills/run-cms/smoke.sh stop    # kill whatever is listening on :8081
```

`start` launches `java -jar target/cms-service.jar --spring.profiles.active=dev`
in the background, logs to `/tmp/cms-app.log`, and blocks until either
`Started CmsApp` or `APPLICATION FAILED TO START` appears in the log
(prints the PID either way). It takes about 20s to boot.

`verify` exercises, against `http://localhost:8081/cms`:
- `/management/health/readiness` (expect `{"status":"UP",...}` with DB UP)
- `/swagger-ui/index.html` and `/v3/api-docs` (expect HTTP 200)
- a full CRUD cycle against `/api/v1/basic/categories` (create → get →
  update → delete → get-after-delete-404) — this route needs no auth
  (see Gotchas on the security model) and has no upstream dependencies,
  so it's the most reliable smoke target in this API.
- the `/api/v1/backoffice/cards/blocks` basic-auth gate: 401 with no
  credentials, then past-auth (404 card-not-found, since the test PAN
  doesn't exist) with `-u sadad-card:changeme` (the default dev
  credentials from `application.yml`).

`stop` runs `lsof -ti:8081 -sTCP:LISTEN | xargs kill`.

Most other REST resources under `src/main/java/.../web/rest/*Resource.java`
call out to upstream Feign clients (onboarding-ms, bmi-provider,
inventory-ms, mcab-provider, etc. — see the `feign.client.*` block in
`application-dev.yml`) that aren't running in this environment, so they
aren't good smoke-test targets unless those upstreams are also stood up.

## Run (human path)

`npm run backend:start` / `./mvnw` also start it (foreground, with
devtools hot-reload) — but see Gotchas, the wrapper is broken in this
environment. `java -jar target/cms-service.jar --spring.profiles.active=dev`
is the equivalent one-shot, foreground version of what `smoke.sh start`
does in the background.

## Test

```bash
mvn -o -ntp test -Dtest=LookupCategoryMapperTest,LookupCategoryDTOTest -s settings.xml
```

Confirmed working offline (`BUILD SUCCESS`, 2 tests). The full suite
(`mvn -o -ntp verify` / `npm run backend:unit:test`) wasn't run in full
here — some tests in `src/test` use Testcontainers (see
`RedisTestContainer.java`) and will need Docker + whatever images those
pull.

## Gotchas

- **`./mvnw` is broken in this environment**: it fails immediately with
  `distributionUrl is not valid, must match *-bin.zip or maven-mvnd-*.zip`
  even though the URL in `.mvn/wrapper/maven-wrapper.properties` looks
  like a normal `-bin.zip` URL — the wrapper jar in this checkout
  rejects it anyway. Use the system `mvn` (3.8.7, already installed)
  with `-s settings.xml` instead; that's what `smoke.sh` does.
- **`repo.mofid.dev` (the internal Artifactory in `pom.xml`/`settings.xml`)
  is unreachable from this box**, even through the configured corporate
  proxy (`$https_proxy`) — it just times out. Plain `https://repo.maven.apache.org/maven2`
  *is* reachable through that same proxy. `smoke.sh`'s build fallback
  exploits this: a temp `settings.xml` with `<mirrorOf>*</mirrorOf>`
  pointing at Maven Central, proxied through `$https_proxy`. This is
  only needed the first time any given plugin/dependency isn't already
  in `~/.m2` — `mvn -o` (offline) works fine once the cache is warm.
- **`docker compose -f src/main/docker/services.yml up --wait` fails
  with "address already in use"** on ports 1433 and 6379 if this box
  already has other services (e.g. a sibling `onboarding-ms` checkout)
  running MSSQL/Redis on the default ports — which was the case here
  (`onboarding-mssql-1`, `onboarding-redis-1`, both healthy, already
  had the `onboarding` database cms's own dev profile expects). Check
  the ports first; don't blindly bring up cms's own compose stack.
- **The security model is easy to misread**: `/api/**` is `permitAll`
  *unless* the request carries an `Authorization` header, in which case
  it's routed through the JWT resource-server filter (which validates
  against `spring.security.oauth2.resourceserver.jwt.jwk-set-uri`, an
  internal IAM host not reachable here). Concretely: don't send an
  `Authorization` header and most `/api/**` endpoints work anonymously;
  `/api/admin/**` needs a real ADMIN-scoped JWT (not obtainable in this
  environment); `/api/v1/backoffice/**` uses a separate HTTP Basic
  filter chain with credentials from `application.backoffice.basic-auth.users`
  in `application.yml` (`sadad-card` / `changeme` by default).

## Troubleshooting

- **`Plugin ... could not be resolved ... Cannot access central
  (https://repo.mofid.dev/artifactory/maven) in offline mode`**: the
  `~/.m2` cache is missing that plugin/artifact and there's no network
  path to the internal Artifactory. Run the online build through the
  mirror fallback (`smoke.sh` does this automatically); expect it to be
  slow the first time.
- **`failed to bind host port for 127.0.0.1:6379 ... address already in
  use`** from `docker compose ... up`: something else already owns that
  port — check `docker ps -a` and `ss -ltnp` before starting cms's own
  containers (see Gotchas above).
