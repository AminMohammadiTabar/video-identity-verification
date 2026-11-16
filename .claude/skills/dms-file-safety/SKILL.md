---
name: dms-file-safety
description: Implement or review DMS file uploads, downloads, physical deletion, soft deletion, storage paths, access policy and audit.
---
# DMS file safety

Read `security-audit`, `concurrency-review`, `test-quality`, `api-contract-review` and `http-observability` before changing DMS.

- Validate subsystem and owner types/identifiers server-side. Pre-registration key is `SYSTEM-AUTH-REGISTER`.
- Generate UUID storage names. Never expose filesystem paths in API DTOs or logs.
- Store bytes outside web root. Reject traversal/symlinks and enforce payload length, extension and signature checks.
- Logical deletion must prevent download. Purge requires DELETED state; retain metadata and support idempotent retry after a crash.
- Filesystem and database aren't one atomic transaction. Preserve compensation/recovery and record limitations.
- DMS is independent; HTTP Basic ADMIN and scoped AUTH_SERVICE principals. AUTH calls DMS over HTTPS except loopback development.
- Durable DMS Audit belongs only to `dms.audit_events`. Successful metadata/state changes and Audit share the local DB transaction; failures use independent transactions. Do not add a central Audit dependency.
- Audit actor should distinguish ADMIN and AUTH_SERVICE. Do not persist personal email or file bytes. Audit events are append-only.
- Trace context arrives through W3C `traceparent` from the auto-instrumented AUTH RestClient; keep Trace ID/Span ID separate from Request ID.
- Test malformed media, invalid owner, access denials, upload/download/delete/purge and Audit transaction behavior.
