---
name: quality-gate-video-kyc
description: Run structural, syntax, build, integration, security and regression checks for all three independent Video KYC services.
---
# Video KYC quality gate

1. Run `python scripts/verify_workspace.py` from the workspace root.
2. Run `java scripts/JavaSyntaxCheck.java` (syntax parse only; not type checking).
3. With JDK 25 and Maven dependencies available, run `./mvnw clean verify` separately in AUTH, DMS and WORKFLOW.
4. Verify H2 and PostgreSQL Flyway migrations, Hibernate `validate`, and independent service startup.
5. AUTH tests: registration, login/logout, CSRF 401/403 behavior, verification attempt history, rejection reason, resubmission, upload failure retry, admin role checks, duplicate decision conflict and email-outbox enqueue.
6. Audit tests: success Audit joins the business transaction; `REQUIRES_NEW` failure Audit survives rollback; per-service tables are independent and append-only at application level.
7. DMS tests: file signature/path safety, AUTH_SERVICE scope, upload/download/logical delete/purge, failure/reconciliation and durable Audit.
8. WORKFLOW tests: its own H2/PostgreSQL Audit persistence without adding an engine.
9. Tracing tests/inspection: W3C traceparent propagation AUTH→DMS, traceId/spanId in logs and Audit context, no sensitive attributes. If no Collector is running, verify export is disabled and requests still succeed.
10. Review secrets, PII, CSRF, authorization, SMTP settings, DB privileges and Swagger exposure.
11. Report exact commands and outcomes. Never label static checks as Maven builds or tests; do not claim unrun checks passed.
