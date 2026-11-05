# Review release — changes from Step 4

- Preserved complete independent AUTH, DMS, WORKFLOW projects and existing features.
- Imported the 24 user-supplied Claude skills to `docs/skill-sources/claude-original/`.
- Activated the 20 broadly applicable skills in both `.claude/skills/` and `.agents/skills/`.
- Added four project-specific skills for architecture, AUTH, DMS, and the quality gate.
- Added `CLAUDE.md` and `AGENTS.md` for safe automatic instruction discovery.
- Strengthened email validation at service and bootstrap boundaries, and refined DB duplicate error handling.
- Hardened DMS symlink checks to include all parents of configured storage root.
- Improved AUTH CSRF endpoint semantics/documentation and exact REST method authorization.
- Added structured, non-sensitive DMS operation logs.
- Added focused tests and the static workspace verification script.
- Added detailed Persian audit, skill usage, residual risks, and run instructions.

**Build evidence:** static checks pass; Java 25 Maven builds and actual Spring integration tests remain unrun in this environment.


## HTTP access logging / AUTH Swagger — Nov 2026

- All three independent apps: native JSON console + rolling file logging; request start/completion with remote/client IP, correlation ID, method/path, status, duration, safe user agent, authenticated flag.
- Explicit proxy trust policy for forwarded IPs; request ID validation; no body/token/cookie logging.
- AUTH: public Swagger UI/OpenAPI routes when enabled; protected business API and CSRF unchanged; login/register/logout action logs.
- DMS: structured file operations and failure events. WORKFLOW: information action event.
- Added tests for logging, proxy spoofing, MDC restoration, security and Swagger non-local profile regression.
- No Maven integration tests were executed in this environment.
