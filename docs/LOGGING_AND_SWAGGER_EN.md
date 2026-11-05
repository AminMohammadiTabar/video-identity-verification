# Logging and Swagger UI — Step 4.1

## Scope

Each of `auth`, `dms`, and `workflow` remains an **independent Spring Boot project**. Each includes its own `observability/HttpAuditFilter.java`, logging settings, and tests. There are no sibling Maven dependencies and no new infrastructure requirements.

## HTTP request logs (all three services)

For every incoming HTTP request, including Swagger, static assets and 401/403 responses:

- `http.request.started`: service, `requestId` (MDC), client IP (MDC), method, **URI path without query parameters**, remote socket IP, sanitized bounded User-Agent, advertised request length.
- `http.request.completed`: service, `requestId`, method, path, `remoteIp`, `clientIp`, `actor` (`user:<UUID>` for database-backed AUTH users, otherwise `authenticated` or `anonymous`), HTTP status, elapsed time in milliseconds, outcome.
- The HTTP response includes `X-Request-ID`. A safe caller-provided `X-Request-ID` is propagated; an unsafe ID is replaced with a server-generated UUID. MDC entries are cleaned up/restored after request dispatch.
- For asynchronous servlet requests, completion is logged from the async listener.
- The logger uses Spring Boot's native **Logstash JSON** structured format, on console and rotating files.

### Logs on disk

| Service | File (relative to its working directory) |
| --- | --- |
| AUTH | `./logs/auth.json.log` |
| DMS | `./logs/dms.json.log` |
| WORKFLOW | `./logs/workflow.json.log` |

Override with `APP_LOG_FILE`. File rolling policy: 20 MB per file, 14 days of history, 300 MB total size cap. Log files are excluded from Git. Configure access permissions, retention and secure aggregation in deployment: IPs are personal data.

### Event/action audit

- AUTH: `AUTH_REGISTER` (SUCCESS after DB commit, DUPLICATE, INVALID_INPUT), `AUTH_LOGIN` (SUCCESS/DENIED), `AUTH_LOGOUT` (SUCCESS).
- DMS: `UPLOAD`, `READ_METADATA`, `DOWNLOAD_REQUESTED`, `LOGICAL_DELETE`, `PHYSICAL_PURGE`, and upload failure.
- WORKFLOW: `INFO` (the service is still only a scaffold).

Action events are **operational logs**, not durable forensic/compliance audit records. A separate, append-only, durable audit store/outbox would be required for strong non-repudiation. `DOWNLOAD_REQUESTED` means authorization + file path preparation succeeded, not proof that the client received every byte.

### Sensitive data safeguards

The filter **does not read or log** passwords, request/response bodies, `Authorization`, cookies, CSRF tokens, session IDs, query strings or multipart bytes. It does not log the AUTH principal email. AUTH loads a custom ID-backed `AccountPrincipal`, allowing access logs and login/logout events to use its user UUID. DMS currently uses one configured Basic administrative account, and records the authenticated flag without exposing its password or username.

### Real IP behind reverse proxies

By default, the logged `clientIp` is the direct socket IP (`remoteIp`); a supplied `X-Forwarded-For` header is **not** trusted. To use original client IPs when behind nginx or a tunnel, explicitly set, for example:

```powershell
$env:AUDIT_TRUSTED_PROXIES = '127.0.0.1,10.0.0.10'
```

Only configure this for socket IP addresses of reverse proxies you control. The edge proxy **must strip client-supplied `Forwarded` and `X-Forwarded-*` headers** before adding its own authoritative value. Do not enable trusting arbitrary internet origins. Avoid logging all raw headers.

## AUTH Swagger UI without login

When Swagger is enabled, the following AUTH routes are now public, irrespective of the chosen Spring profile:

- `GET /swagger-ui.html` (springdoc redirects to the Swagger assets, **not** to the login page)
- `GET /swagger-ui/index.html`
- `/swagger-ui/**`
- `/v3/api-docs/**` and `/v3/api-docs.yaml`

**This does not permit access to protected application APIs**, which still require their own authentication/CSRF checks. Swagger UI does not automatically authenticate API calls.

Swagger remains enabled by default only in `h2`/`local` profiles. In production the endpoints are disabled unless you explicitly opt in using `SWAGGER_ENABLED=true`. Publicly exposing Swagger in a deployed environment is not recommended without a separate network restriction.

## Commands to run on Windows

In each independent project directory, with JDK 25:

```powershell
.\mvnw.cmd clean verify
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=h2' # AUTH and DMS
```

For WORKFLOW use `local` instead of `h2` and set `DMS_API_PASSWORD` for DMS. Request example:

```powershell
curl.exe -i http://localhost:8080/swagger-ui.html
curl.exe -i http://localhost:8080/v3/api-docs
curl.exe -i http://localhost:8081/api/v1/dms/files/00000000-0000-0000-0000-000000000000
```

Check the `X-Request-ID` header and the JSON log lines for each request. The DMS example should return 401 without Basic credentials.

## Verification limits

Run Maven tests and the real HTTP smoke checks on JDK 25. The static Java parser and YAML checks included with the source only verify syntax/structure; they do not prove runtime behavior.
