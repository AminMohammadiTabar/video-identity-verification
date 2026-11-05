# Video Identity Verification — Step 4, Reviewed with Agent Skills

> This is a reviewed continuation of the Step 4 codebase, **not Step 5**. All existing functionality is preserved; it adds targeted safety fixes, tests, Claude Code / Antigravity skills and a documented quality gate. Full review: [`docs/QUALITY_REVIEW_FA.md`](docs/QUALITY_REVIEW_FA.md).

**Three fully independent Spring Boot 4.1.2 projects in one developer workspace**: `auth/`, `dms/`, `workflow/`.
There is **no Maven parent POM, no inter-project Maven dependency and no shared JVM**.
Each project has its own Maven wrapper, independent main application class and port.

## AUTH integration-test fix — 401 vs 403

See [`docs/AUTH_401_403_FIX_FA.md`](docs/AUTH_401_403_FIX_FA.md) for the correction to
`AuthFlowH2IntegrationTest`: an anonymous request with a valid CSRF token
receives `401`, while an authenticated request denied by `denyAll()` receives `403`.
The Spring Security policy itself was not loosened.


## Agent skills included

Open **this workspace root** in Claude Code or Google Antigravity. Skills are discoverable from `.claude/skills/` and `.agents/skills/` respectively; project rules are in `CLAUDE.md` and `AGENTS.md`.

- **20 general skills** from your archive active in each agent directory.
- **4 project-specific skills** additionally active: `video-kyc-development`, `auth-security-mvc`, `dms-file-safety`, `quality-gate-video-kyc`.
- **All 24 original skills preserved**, unmodified, under `docs/skill-sources/claude-original/`. The 4 bank-specific originals are archived, not auto-activated.

Read the Persian guide: [`docs/SKILLS_USAGE_FA.md`](docs/SKILLS_USAGE_FA.md).
Run `python scripts/verify_workspace.py` for structural checks. Run `java scripts/JavaSyntaxCheck.java` to parse the existing Java 21-compatible syntax without dependencies. **Neither command compiles the services or runs Maven tests.**

## Scope of Step 4

| Service | Port | Implemented | Not yet implemented |
|---|---:|---|---|
| **AUTH** | `8080` | Browser registration, login/logout using Spring Security, USER/ADMIN roles, BCrypt password hashing, Mustache pages, placeholder video chooser, JSON registration/account/CSRF API, OpenAPI + Swagger UI | File submission to DMS, verification decisions, emails, workflow execution |
| **DMS** | `8081` | Disk-backed upload/download, metadata, logical delete, physical purge, HTTP Basic, documented OpenAPI + Swagger UI | AUTH integration, asynchronous events |
| **WORKFLOW** | `8082` | Independent Boot application, scaffold status endpoint, OpenAPI + Swagger UI | State machine engine, transitions, persistence, admin workflow panel |

This is **three independent applications in a single directory**, not a modular monolith.

## Technology

- Java 25 LTS; Spring Boot 4.1.2; Maven 3.9.12 wrapper per app
- Spring MVC; Spring Security; Mustache (AUTH only)
- Spring Data JPA and Flyway (AUTH/DMS)
- PostgreSQL with separate `auth` and `dms` schemas; file-based H2 alternative for local development
- Local disk for DMS; **storage directory order**: `SUBSYSTEM/YYYY/MM/DD/OWNER_ID/FILE_UUID.ext`
- OpenAPI 3 via **springdoc-openapi-starter-webmvc-ui 3.2.0** in all services

## Quick start — Windows PowerShell, H2

Install JDK 25; ensure `java -version` reports Java 25. Import the three child `pom.xml` files into the same IntelliJ window.
From the workspace root, open one terminal tab per service:

**AUTH**

```powershell
cd auth
.\mvnw.cmd clean verify
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=h2'
```

Visit `http://localhost:8080` or `http://localhost:8080/register` and create a user account.
Registration grants only `ROLE_USER`. Sign in at `/login` and visit `/verification/upload`.
The video selection field is **UI-only**: nothing is sent to the server, to DMS or to the database.
A new account starts conceptually in `NOT_SUBMITTED`; the enum remains in the AUTH source.

**DMS**

```powershell
cd dms
$env:DMS_API_USERNAME = 'dms-admin'
$env:DMS_API_PASSWORD = 'use-a-unique-long-local-password'
.\mvnw.cmd clean verify
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=h2'
```

Only DMS admin credentials can access DMS file APIs; HTTP Basic is meant for localhost or HTTPS, never cleartext over the public internet.

**WORKFLOW**

```powershell
cd workflow
.\mvnw.cmd clean verify
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=local'
```

`GET http://localhost:8082/api/v1/workflow/info` returns `NOT_IMPLEMENTED` by design.

## Swagger UI and OpenAPI

| Service | Swagger UI | OpenAPI JSON (grouped) |
|---|---|---|
| AUTH | http://localhost:8080/swagger-ui.html | http://localhost:8080/v3/api-docs/auth-api |
| DMS | http://localhost:8081/swagger-ui.html | http://localhost:8081/v3/api-docs/dms-api |
| WORKFLOW | http://localhost:8082/swagger-ui.html | http://localhost:8082/v3/api-docs/workflow-api |

The default ungrouped document is also available at `/v3/api-docs` (and YAML at `/v3/api-docs.yaml`).
`springdoc` produces annotated response codes, request schemas and security declarations:

- **AUTH:** Cookie-based Spring Security session scheme (`JSESSIONID`) for `/api/v1/auth/me`. The Mustache login/logout forms are **not** misrepresented as JSON endpoints. The public JSON registration endpoint requires CSRF protection; Swagger's *Try it out* works after obtaining the token from `GET /api/v1/auth/csrf` and supplying it as `X-CSRF-TOKEN` **with the same session cookie**. Signing in is done at `/login`.
- **DMS:** OpenAPI HTTP Basic scheme `dmsBasic` with documented multipart upload fields, binary download, error responses and the soft-delete/purge lifecycle. Click **Authorize** to provide DMS credentials.
- **WORKFLOW:** Only an honest scaffold `GET /api/v1/workflow/info` endpoint; no imaginary state machine API.

Swagger UI and `/v3/api-docs` are **enabled in `h2` or `local` profiles (bound to 127.0.0.1), disabled by default elsewhere (including production)**. If deliberately enabled in production, AUTH and DMS documentation requires an administrator login; WORKFLOW exposes only a public scaffold status and should stay behind network access controls. To intentionally override in a trusted environment, set `SWAGGER_ENABLED=true`. Avoid exposing API documentation publicly without perimeter access controls.

## Initial ADMIN account (optional, explicit bootstrap)

For the first admin (fresh AUTH DB only), configure these *before* starting AUTH:

```powershell
$env:AUTH_BOOTSTRAP_ADMIN_ENABLED = 'true'
$env:AUTH_BOOTSTRAP_ADMIN_EMAIL = 'admin@example.com'
$env:AUTH_BOOTSTRAP_ADMIN_PASSWORD = 'replace-with-a-private-strong-password'
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=h2'
```

The bootstrap creates an admin only when no `ROLE_ADMIN` user exists; it **never upgrades an existing regular user's account**.
Do not commit credentials. Disable the bootstrap flag after initial provisioning. `/admin` requires `ROLE_ADMIN` and is a protected **placeholder**—review and approval are outside this phase.

## Auth API

| Method | Route | Authentication / CSRF | Response |
|---|---|---|---|
| GET | `/api/v1/auth/csrf` | Public; establishes/uses browser session | `200` with token/headerName |
| POST | `/api/v1/auth/registrations` | Public; `X-CSRF-TOKEN` required | `201`, safe account ID/email, `409` on duplicates |
| GET | `/api/v1/auth/me` | Browser session required | `200`, ID/email/roles; `401` for anonymous |
| GET | `/login`, `/register` | Public browser pages | HTML |
| POST | `/login`, `/register`, `/logout` | Browser form endpoints; CSRF required | Redirect/HTML |
| GET | `/verification/upload` | Signed-in session | UI only: no upload |
| GET | `/admin` | `ROLE_ADMIN` | Placeholder dashboard |

Example of session-preserving JSON registration using PowerShell:

```powershell
$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$csrf = Invoke-RestMethod -Uri 'http://localhost:8080/api/v1/auth/csrf' -WebSession $session
$body = @{ email='person@example.com'; password='a-unique-strong-secret-123' } | ConvertTo-Json
Invoke-RestMethod -Uri 'http://localhost:8080/api/v1/auth/registrations' `
  -Method Post -ContentType 'application/json' -Body $body `
  -WebSession $session -Headers @{ 'X-CSRF-TOKEN' = $csrf.token }
```

Passwords are stored with BCrypt (work factor 12), never returned from API responses. The password must be 12–72 UTF-8 bytes (BCrypt limit); email addresses are lowercased before persistence, with DB uniqueness enforcement.

## DMS API (unchanged operations; richer docs)

| Method | Route | Operation |
|---|---|---|
| `POST` | `/api/v1/dms/files` | Multipart upload (`subsystem`, `ownerType`, `ownerReference`, `file`) |
| `GET` | `/api/v1/dms/files/{id}` | Metadata |
| `GET` | `/api/v1/dms/files/{id}/content` | Active file bytes |
| `DELETE` | `/api/v1/dms/files/{id}` | Logical delete |
| `POST` | `/api/v1/dms/files/{id}/purge` | Physical delete after logical delete |

For an anonymous pre-registration asset in a **future** AUTH-DMS integration, use `ownerType=SYSTEM` and `ownerReference=SYSTEM-AUTH-REGISTER`.

Example multipart request:

```powershell
curl.exe -u 'dms-admin:use-a-unique-long-local-password' `
  -F 'subsystem=AUTH' -F 'ownerType=SYSTEM' `
  -F 'ownerReference=SYSTEM-AUTH-REGISTER' -F 'file=@C:\temp\sample.pdf' `
  http://localhost:8081/api/v1/dms/files
```

H2 consoles in the `h2` profile: AUTH `http://localhost:8080/h2-console`, DMS `http://localhost:8081/h2-console` (both bound to `127.0.0.1`).
The PostgreSQL Flyway migrations remain authoritative for production; H2-specific scripts are under each module's `db/migration-h2`.

## Independent builds

```powershell
cd auth; .\mvnw.cmd clean verify
cd ..\dms; .\mvnw.cmd clean verify
cd ..\workflow; .\mvnw.cmd clean verify
```

Every `pom.xml` uses only `org.springframework.boot:spring-boot-starter-parent` as its parent and **never depends on a sibling project**.

## Verification status

Source-level structural checks for POMs, YAML and cross-module dependencies were done during packaging, but **a successful Spring Boot build and execution of integration tests have not been demonstrated here**: the packaging environment has only Java 21, no Maven runtime/dependency cache and no Maven Central network access. Run all three `clean verify` commands on JDK 25 before production deployment.

See `docs/STEP_4_FA.md` for previous implementation notes and `docs/QUALITY_REVIEW_FA.md` for findings, code changes, tests, and remaining high-risk items. The three Maven builds are still required in a Java 25 environment; **do not treat this release as production-security certified**.


## Logging / HTTP observability & Swagger access

The three independent services now write JSON HTTP access and action logs, with request correlation
(`X-Request-ID`), IP handling behind explicitly trusted proxies, safe field selection and log rotation.
AUTH Swagger UI is anonymously reachable when enabled (not redirected to login). See
[`docs/LOGGING_AND_SWAGGER_FA.md`](docs/LOGGING_AND_SWAGGER_FA.md) for behavior, privacy rules and examples.
