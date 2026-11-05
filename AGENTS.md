# Agent instructions — Video Identity Verification

This file is intended for Antigravity and other repository-aware coding agents; Claude Code should read it alongside `CLAUDE.md`.

## Architecture (non-negotiable)
- `auth/`, `dms/`, and `workflow/` are **independent Spring Boot applications**, not a single Maven multi-module project. Each has its own `pom.xml`, Maven wrapper, main class and HTTP port (8080/8081/8082).
- They are siblings only to share an IDE window. Do not make them depend on one another, create a shared Maven parent, or introduce a shared runtime/JVM.
- `auth`: Spring MVC, Mustache, Spring Security, registration/login, USER/ADMIN, inactive upload UI. `VerificationStatus` enum is defined locally; no running workflow or upload to DMS yet.
- `dms`: disk-backed API, file metadata, soft delete, physical purge. Store files as `SUBSYSTEM/YYYY/MM/DD/OWNER_ID/UUID.ext`. A pre-registration owner may be `SYSTEM-AUTH-REGISTER`.
- `workflow`: Spring Boot scaffold, OpenAPI and status endpoint only. Do not silently implement workflow engine or RabbitMQ.
- Postgres schema per service (`auth`, `dms`) with Flyway; H2 separate local-only migration scripts. Redis/RabbitMQ are future work.

## Security and API
- Do not turn off CSRF for AUTH's session-backed form and registration API.
- Account self-registration must not grant ADMIN. Require ADMIN role for `/admin`; DMS API requires privileged HTTP Basic in the MVP and HTTPS when network exposed.
- Swagger/OpenAPI belongs in **each** project. AUTH Swagger endpoints must be anonymous-accessible **whenever explicitly enabled**, but are **disabled by default outside local/h2 development** (including production). Never expose APIs by making application business routes permitAll. Do not fabricate endpoints.
- All HTTP requests must be access-logged with correlation ID, client IP, HTTP status and latency, including rejected requests. Do not log secrets, password hashes, tokens, full URLs with query strings, private filesystem paths, video payloads, cookies, or personal emails. Trust X-Forwarded-For only from explicitly configured proxies, with sanitized edge forwarding. Do not relax path validation or upload size/type restrictions.

## Workflow for new tasks
1. Read relevant general skills from `.agents/skills` (Antigravity) or `.claude/skills` (Claude Code) plus project-specific `video-kyc-development`, `auth-security-mvc`, `dms-file-safety`, or `quality-gate-video-kyc`, or `http-observability`.
2. Inspect existing tests, dependencies, migrations and API contracts before changes.
3. Implement minimal maintainable change; add negative/security tests and update docs.
4. Run `python scripts/verify_workspace.py` and per-service `./mvnw clean verify` if Java 25, Maven and external dependencies are available.
5. Record verified results and remaining risk. Do not confuse static checks with integration tests.

## Original skills provenance
The 24 user-provided skills are preserved under `docs/skill-sources/claude-original`. 20 general skills are active in both agent directories; four Mellat-specific skills are archived rather than activated because their assumptions and scripts target a different application and could weaken security here. Project-specific skills above are additional.

The extra `http-observability` project skill is available for logging, observability and safe audit updates.
