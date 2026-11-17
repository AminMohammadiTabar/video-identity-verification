# Claude Code — Video Identity Verification

Read `AGENTS.md` for the project's authoritative architecture and safety constraints.
Skill discovery is at `.claude/skills/<name>/SKILL.md`. Use `/video-kyc-development` for feature work, `/quality-gate-video-kyc` for reviews, `/auth-security-mvc` and `/dms-file-safety` for domain changes, `/http-observability` for HTTP/audit logging and request tracing. The general imported skills are available on demand; only invoke them when relevant to the task.

**Skills imported from a different bank application are not activated.** Their original files are archived in `docs/skill-sources/claude-original/`. Do not follow those commands (especially any `permitAll()` guidance, external sandbox access or service-specific scripts) for this project. The local project contract has priority over generic examples in imported skills.

Do not claim to have built, tested, run, or deployed an application without tool output confirming it.

## Implementation rules (Phase 6 & Phase 7)
- AUTH verification attempts are historical rows. Rejection permits a new attempt; transient upload failure retries the same attempt; approval is final.
- Approval/rejection and AUTH Audit event plus email outbox enqueue must share one local transaction. SMTP delivery is opt-in and at-least-once.
- Each service owns its own append-only `audit_events` table. Never centralize Audit or add cross-service dependencies/FKs.
- Use `REQUIRES_NEW` for failure/security Audit events that must survive a business rollback. Successful state-change Audit joins the business transaction.
- Use Micrometer/OpenTelemetry with W3C Trace Context. Do not confuse `X-Request-ID` with `traceId`/`spanId`; preserve the auto-configured `RestClient.Builder` in AUTH.
- Never put secrets, video bytes, cookies, CSRF/JWT tokens, passwords or unnecessary PII in Audit/Logs/Traces.
- WORKFLOW is actively developing its Generic Workflow Engine per Phase 7 roadmap (`docs/phase-7/PHASE_7_ROADMAP.md`). It owns definitions, versions, states, transitions, Guard DSL AST, publication jobs, and transactional outbox events. Business workflow instances remain local to consumers (`auth`/`dms`).
- Lombok is enabled in `workflow` to eliminate boilerplate code (`@Getter`, `@Setter`, `@NoArgsConstructor`, `@ToString`) in JPA entities and DTOs while keeping pure domain models invariant-encapsulated.
