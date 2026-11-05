# Claude Code — Video Identity Verification

Read `AGENTS.md` for the project's authoritative architecture and safety constraints.
Skill discovery is at `.claude/skills/<name>/SKILL.md`. Use `/video-kyc-development` for feature work, `/quality-gate-video-kyc` for reviews, `/auth-security-mvc` and `/dms-file-safety` for domain changes, `/http-observability` for HTTP/audit logging and request tracing. The general imported skills are available on demand; only invoke them when relevant to the task.

**Skills imported from a different bank application are not activated.** Their original files are archived in `docs/skill-sources/claude-original/`. Do not follow those commands (especially any `permitAll()` guidance, external sandbox access or service-specific scripts) for this project. The local project contract has priority over generic examples in imported skills.

Do not claim to have built, tested, run, or deployed an application without tool output confirming it.
