---
name: http-observability
description: Implement and review safe JSON HTTP request logging, durable per-service Audit, client IP handling, correlation IDs, OpenTelemetry tracing and Swagger access.
---
# HTTP observability and durable Audit — Video KYC

Read `logging-patterns`, `security-audit`, `test-quality`, `AGENTS.md` and the step-six design before editing.

## Keep the three data products separate
- Operational Logs: request lifecycle, errors and diagnostic details.
- Durable Audit: business/security events persisted in each service's own `audit_events` table.
- Distributed Traces: spans correlated across services and exported through OTLP.

## HTTP logging
- Every HTTP request (including 401/403 and Swagger) is logged on entry and completion with status/duration, Request ID, IP and safe actor metadata.
- Preserve MDC cleanup and async safety. Do not log body, query string, Authorization/Cookie/CSRF headers, passwords, session IDs, JWTs, video bytes, personal emails or private file paths.
- Validate `X-Request-ID` before propagation and return it in the response.
- Trust Forwarded/X-Forwarded-For only when the direct socket address is in an explicit trusted proxy list and ingress strips spoofable headers.

## Durable Audit
- AUTH writes only `auth.audit_events`; DMS writes only `dms.audit_events`; WORKFLOW writes only `workflow.audit_events`.
- No central Audit Service, no cross-service FK, no Maven shared module.
- Successful business state changes and Audit writes share the local transaction. Failure/security events that must survive rollback use `REQUIRES_NEW`.
- Keep the repository insert-only at application level. Production DB runtime account must not have UPDATE/DELETE on Audit tables.
- Use stable event names, actor/subject/target IDs, outcome and reason codes. Keep free-text rejection reasons out of Audit details.
- Audit is not sampled. Trace sampling must never control whether an Audit event is persisted.

## Distributed Tracing
- Use Spring Boot's supported Micrometer/OpenTelemetry starter and W3C Trace Context.
- Request ID, Trace ID and Span ID are different identifiers.
- AUTH must construct its DMS client from the auto-configured `RestClient.Builder`; do not replace it with a raw RestClient if trace propagation is required.
- Include traceId/spanId in structured logs and Audit context when available. Do not put PII/secrets in span attributes or baggage.
- OTLP export is configurable; lack of a Collector must not break business requests. Local sampling may be 100%; production sampling is configurable.

Keep Swagger anonymously accessible in AUTH when enabled, without weakening business endpoint authorization. Swagger is disabled by default outside local development.
