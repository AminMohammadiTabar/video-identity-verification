---
name: video-kyc-development
description: Design or modify AUTH, DMS, or WORKFLOW in this independent Spring Boot workspace, preserving agreed phase boundaries.
---
# Video KYC development contract

Read `AGENTS.md` / `CLAUDE.md` at the workspace root and the affected service `pom.xml`, `application*.yml`, Flyway scripts, tests and controllers before editing.

## Non-negotiable architecture
- Three independent Maven/Spring Boot projects in sibling directories: auth (8080), dms (8081), workflow (8082). No shared parent POM, Java API invocation, module dependency, entity reuse, or cross-schema FK.
- AUTH MVC/Mustache/Spring Security; the authorized AUTH-to-DMS connection uses HTTP REST only and backend-only credentials.
- AUTH verification is attempt-based. Each submission after a rejection creates a new immutable-history attempt. A transient DMS upload failure retries the same attempt. APPROVED is final.
- AUTH decisions, durable Audit and email-outbox enqueue share one local transaction. SMTP delivery is opt-in and uses a local transactional outbox, not RabbitMQ.
- Each service owns a separate `audit_events` table and Flyway migration. No central Audit Service.
- WORKFLOW has JPA/Flyway only for its own Audit store. Do not invent a dynamic workflow engine or transitions in this phase.
- DMS owns files and metadata. Store under SUBSYSTEM/YYYY/MM/DD/OWNER_ID/UUID.extension; never use submitted paths.
- PostgreSQL/Flyway are authoritative; H2 uses separate local migrations.

## Audit, tracing and privacy
- Successful business Audit joins the local business transaction; failure/security Audit that must survive rollback uses `REQUIRES_NEW`.
- Keep logs, Audit and Traces separate. Request ID != Trace ID != Span ID.
- Use Micrometer/OpenTelemetry and W3C propagation. Preserve Boot's auto-configured `RestClient.Builder` for AUTH→DMS propagation.
- Never log/store passwords, password hashes, tokens, cookies, CSRF/session IDs, video bytes, multipart bodies or unnecessary personal data. IP/User-Agent access and retention must be controlled.
- Audit is never sampled; Trace sampling is configurable. Production DB runtime user should not have UPDATE/DELETE privileges on Audit tables.

## Implementation loop
1. Read relevant architecture, security, observability, concurrency, API-contract and test skills.
2. Implement minimal correct change, keeping public DTOs and persistence entities separate.
3. Test success, invalid input, unauthorized access, rollback, concurrency, attempts and outbox retry cases as applicable.
4. Run `python scripts/verify_workspace.py`, `java scripts/JavaSyntaxCheck.java`, and each `./mvnw clean verify` when Java 25 and dependencies are available.
5. Record actual outcomes and residual risks in `docs/STEP_6_QUALITY_REVIEW_FA.md`. Never claim unrun tests passed.
