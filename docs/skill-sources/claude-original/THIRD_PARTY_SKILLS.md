# Third-party skills

The skills below were reviewed and imported from external, third-party repositories on 2026-08-16, cherry-picked for relevance to this project (Java 21 / Spring Boot 4.1 / JHipster microservice, no database, Feign + resilience4j, OAuth2 resource server). Each was read in full before import to check for quality and for anything that looked like a prompt-injection/bad-practice risk - none found. This file exists so the source/license of each is traceable; it is not itself a skill.

Sources considered and why some were skipped entirely: see the conversation this was decided in, or ask - short version is `affaan-m/ECC` and `spring-ai-community/spring-ai-agent-utils` aren't Claude Code skill collections at all (an entire competing agent-orchestration framework, and a Java *library*, respectively), `jdubois/dr-jskill` is a from-scratch project generator (not applicable to an existing service), and `Jeffallan/claude-skills`/`anthropics/skills`/`alirezarezvani/claude-skills` are large generic marketplaces where almost everything is out of scope for this specific stack (React/NestJS/marketing/compliance/document-manipulation/etc.) - only one skill from each of the latter three turned out to be a genuinely additive, non-duplicate fit.

| Skill | Source repo | License | Notes |
|---|---|---|---|
| `git-commit` | [decebals/claude-code-java](https://github.com/decebals/claude-code-java) | MIT | |
| `changelog-generator` | decebals/claude-code-java | MIT | |
| `issue-triage` | decebals/claude-code-java | MIT | |
| `java-code-review` | decebals/claude-code-java | MIT | |
| `api-contract-review` | decebals/claude-code-java | MIT | |
| `concurrency-review` | decebals/claude-code-java | MIT | |
| `performance-smell-detection` | decebals/claude-code-java | MIT | |
| `test-quality` | decebals/claude-code-java | MIT | |
| `maven-dependency-audit` | decebals/claude-code-java | MIT | |
| `security-audit` | decebals/claude-code-java | MIT | |
| `architecture-review` | decebals/claude-code-java | MIT | |
| `solid-principles` | decebals/claude-code-java | MIT | |
| `design-patterns` | decebals/claude-code-java | MIT | |
| `clean-code` | decebals/claude-code-java | MIT | |
| `spring-boot-patterns` | decebals/claude-code-java | MIT | mentions JPA/repository patterns generically - this project has no database, ignore those parts |
| `java-migration` | decebals/claude-code-java | MIT | kept for future use; this project is already on current Java/Spring Boot |
| `logging-patterns` | decebals/claude-code-java | MIT | |
| `jspecify` | [sivaprasadreddy/sivalabs-agent-skills](https://github.com/sivaprasadreddy/sivalabs-agent-skills) | MIT | this project already depends on jspecify (`org.jspecify.annotations.Nullable`); the skill's Maven-plugin setup steps assume `nullability-maven-plugin`, which this project doesn't currently use - the annotation-usage guidance is the relevant part |
| `microservices-architect` | [Jeffallan/claude-skills](https://github.com/Jeffallan/claude-skills) | MIT | this service is one microservice in a larger platform (mellat-provider-service/address-service/CMS-onboarding) - DDD/resilience/observability guidance applies |
| `adversarial-reviewer` | [alirezarezvani/claude-skills](https://github.com/alirezarezvani/claude-skills) (`engineering-team/skills/adversarial-reviewer`) | MIT | framework-agnostic - three hostile-reviewer personas (Saboteur/New Hire/Security Auditor) forcing a genuine second look instead of a self-agreeing review |

Not imported from these same repos, but worth knowing exist if ever needed:

- **decebals/claude-code-java**: `jpa-patterns` - skipped, this project has no database (`databaseType: no`).
- **sivaprasadreddy/sivalabs-agent-skills**: `spring-boot` (overlaps with `spring-boot-patterns` above - picked one), `spring-modulith-verifier` (this project isn't structured as a Spring Modulith app), `pair-programming` (generic workflow, not review-focused).
- **Jeffallan/claude-skills**: `java-architect`, `spring-boot-engineer`, `code-reviewer`, `security-reviewer`/`secure-code-guardian`, `test-master`, `architecture-designer`, `api-designer`, `monitoring-expert`, `debugging-wizard`, `legacy-modernizer` - all reasonable, but each substantially overlaps a skill already imported from decebals above; not duplicated to avoid a cluttered/contradictory skill set.
- **alirezarezvani/claude-skills**: `senior-architect`, `senior-backend`, `tdd-guide`, `code-reviewer` (under `audit/`) - same overlap reasoning.
- **anthropics/skills** (official): mostly document-manipulation (PDF/DOCX/PPTX/XLSX) and creative/design skills - nothing code-review-relevant to this stack found.
