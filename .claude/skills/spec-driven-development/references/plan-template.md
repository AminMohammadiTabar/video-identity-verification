# <TICKET>: Implementation Plan

**Status:** draft | reviewed | approved
**Depends on:** spec.md (must be approved first)

## Architecture / Approach

High-level design, chosen approach and why (vs alternatives considered).

## Affected Modules / Packages

List existing classes to be modified and new classes to be added, grouped
by layer (domain, repository, service, mapper, web/rest, client, config).

## State Machine Changes

(Only if applicable — this repo has per-provider state machines under
`statemachine/`, each with a `*StateMachineConfiguration`, `*Action`,
`enumeration/*Event`, `enumeration/*State`, and `listener/*Listener`.)

- New/changed states:
- New/changed events/transitions:
- Which provider(s): BMI / Mellat / both (mirrored)

## Data Model / Liquibase Changes

- New/changed entities and fields
- New changelog file: `src/main/resources/config/liquibase/<YYYY-MM-DD-HHMMSS>.yaml`
  (follow existing dated-file convention, included via `master.xml`)
- Any raw SQL fragments under `.../liquibase/sql/`
- Indexes, constraints, backfill/migration considerations
- Rollback strategy

## REST API Contract

For each new/changed endpoint:
- `METHOD /api/v1/...` — purpose
- Request params/body (DTO name, fields)
- Response (DTO name, fields, status codes)
- Auth/security notes (this repo: `/api/**` permitAll unless Authorization
  header present → JWT filter; `/api/admin/**` needs ADMIN JWT;
  `/api/v1/backoffice/**` uses HTTP Basic — state which chain applies)

## External Integrations

Third-party/provider clients affected (e.g. `client/onboarding/mellat`),
config properties, rate limits, kill-switches.

## Jobs / Scheduling

If a scheduled/polling job is involved: trigger mechanism, locking
strategy (this repo uses Redis distributed locks), retry/backoff policy,
idempotency guarantees.

## Testing Strategy

- Unit tests: which classes/mappers
- Integration tests (`*IT.java`, Testcontainers where relevant)
- Manual verification steps (curl commands, via run-cms skill if useful)

## Risks / Rollout

- Backward compatibility, feature flags/kill-switches, rollout order,
  monitoring/alerting to add.

## Open Design Questions

- ...
