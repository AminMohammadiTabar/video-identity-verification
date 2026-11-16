---
name: auth-security-mvc
description: Review or implement AUTH user signup/login/logout, MVC Mustache, roles, CSRF, verification attempts and notifications.
---
# AUTH security + MVC

Read `security-audit`, `spring-boot-patterns`, `java-code-review`, `test-quality`, and current AUTH security chains.

- Distinguish application login authentication (Spring Security sessions) from video identity verification.
- Never expose password hashes, credentials, CSRF tokens or email addresses in logs/Audit details.
- Validate registration at service and web boundaries; normalize email; hash with BCrypt; enforce DB uniqueness.
- Self-registration always receives `ROLE_USER`; ADMIN bootstrap is explicit and environment-controlled.
- Keep CSRF protection for session-backed forms and JSON registration. Enforce roles in SecurityFilterChain, not only the UI.
- DMS credentials remain backend-only. The browser never calls DMS directly.
- Verification attempts are historical. A rejected attempt allows a new row with an incremented attempt number; an upload failure retries the same row; an approved attempt is final.
- Admin rejection requires a bounded reason. The owner may see the reason; Audit stores only a stable reason code, not the free-text reason.
- Approval/rejection and email-outbox enqueue are one AUTH transaction. SMTP delivery is opt-in, retried with backoff, and may be at-least-once; do not claim exactly-once delivery.
- Every important AUTH action writes durable Audit in `auth.audit_events`; success joins the business transaction, failure/security Audit uses `REQUIRES_NEW` when it must survive rollback.
- Keep Swagger anonymous-accessible when enabled while business APIs remain protected. Preserve safe logging and W3C tracing.
