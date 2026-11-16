# Agent instructions — Video Identity Verification

This file is intended for Antigravity and other repository-aware coding agents; Claude Code should read it alongside `CLAUDE.md`.

## Architecture (non-negotiable)
- `auth/`, `dms/`, and `workflow/` are **independent Spring Boot applications**, not a single Maven multi-module project. Each has its own `pom.xml`, Maven wrapper, main class and HTTP port (8080/8081/8082).
- They are siblings only to share an IDE window. Do not make them depend on one another, create a shared Maven parent, or introduce a shared runtime/JVM.
- `auth`: Spring MVC, Mustache, Spring Security, registration/login, USER/ADMIN, operational video upload through a backend-only HTTP client to DMS, verification requests, and admin review decisions. The status enum remains local; no separate WORKFLOW engine.
- `dms`: disk-backed API, file metadata, soft delete, physical purge. Store files as `SUBSYSTEM/YYYY/MM/DD/OWNER_ID/UUID.ext`. A pre-registration owner may be `SYSTEM-AUTH-REGISTER`.
- `workflow`: Spring Boot scaffold with its own JPA/Flyway store only for local `workflow.audit_events`. Do not silently implement workflow engine, dynamic states, transitions or RabbitMQ.
- Each service owns its own Postgres schema and Flyway migrations (`auth`, `dms`, `workflow`); H2 has separate local-only migrations. Redis/RabbitMQ remain future work.
- AUTH verification is attempt-based: every resubmission after REJECTED creates a new `verification_requests` row; transient DMS failure retries the same attempt. APPROVED cannot be resubmitted.
- Email decisions are written to AUTH's transactional `email_outbox`. SMTP delivery is opt-in (`EMAIL_NOTIFICATIONS_ENABLED=true`) and uses retry/backoff; no RabbitMQ is involved.

## Security and API
- Do not turn off CSRF for AUTH's session-backed form and registration API.
- Account self-registration must not grant ADMIN. Require ADMIN role for `/admin`; DMS API requires HTTP Basic with separate ADMIN and scoped AUTH_SERVICE credentials and HTTPS when network exposed.
- Swagger/OpenAPI belongs in **each** project. AUTH Swagger endpoints must be anonymous-accessible **whenever explicitly enabled**, but are **disabled by default outside local/h2 development** (including production). Never expose APIs by making application business routes permitAll. Do not fabricate endpoints.
- All HTTP requests must be access-logged with correlation ID, client IP, HTTP status and latency, including rejected requests. Do not log secrets, password hashes, tokens, full URLs with query strings, private filesystem paths, video payloads, cookies, or personal emails. Trust X-Forwarded-For only from explicitly configured proxies, with sanitized edge forwarding. Do not relax path validation or upload size/type restrictions.

## Audit and tracing invariants
- Audit is independent in each service: `auth.audit_events`, `dms.audit_events`, and `workflow.audit_events`. Never create a central Audit Service or cross-schema foreign key without explicit approval.
- Business success audit rows must join the same local transaction as the business state change. Failure/security audit rows that must survive rollback use `REQUIRES_NEW`.
- Audit is append-only at application level. In production, separate Migration Owner from Runtime DB User and deny UPDATE/DELETE on Audit tables to the runtime account.
- Keep Operational Logs, durable Audit, and Distributed Traces distinct. `X-Request-ID`, W3C `traceId`, and `spanId` are different identifiers.
- Use Micrometer/OpenTelemetry and W3C propagation. AUTH must use Spring Boot's auto-configured `RestClient.Builder` so trace context propagates to DMS.
- Never record passwords, hashes, cookies, session IDs, CSRF/JWT tokens, video bytes, multipart bodies or unnecessary personal data in Audit, Log or Trace. IP and User-Agent need controlled access and retention.
- Audit must never be sampled. Trace sampling may be configured; default local sampling is 100%, production should be explicitly configured.

## Workflow for new tasks
1. Read relevant general skills from `.agents/skills` (Antigravity) or `.claude/skills` (Claude Code) plus project-specific `video-kyc-development`, `auth-security-mvc`, `dms-file-safety`, `quality-gate-video-kyc`, and `http-observability`.
2. Inspect existing tests, dependencies, migrations and API contracts before changes.
3. Implement minimal maintainable change; add negative/security tests and update docs.
4. Run `python scripts/verify_workspace.py`, `java scripts/JavaSyntaxCheck.java`, and per-service `./mvnw clean verify` if Java 25, Maven and external dependencies are available.
5. Test Audit transaction behavior (REQUIRED vs REQUIRES_NEW), attempt history, email outbox retries, DMS lifecycle and W3C trace propagation when applicable.
6. Record verified results and remaining risk. Do not confuse static checks with integration tests.

## Original skills provenance
The 24 user-provided skills are preserved under `docs/skill-sources/claude-original`. 20 general skills are active in both agent directories; four Mellat-specific skills are archived rather than activated because their assumptions and scripts target a different application and could weaken security here. Five project-specific skills above are active in both agent directories.

The extra `http-observability` project skill is available for logging, observability and safe audit updates.
