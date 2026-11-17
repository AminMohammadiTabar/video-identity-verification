---
date: 2026-11-17
status: Approved
title: Phase 7 --- Step 7.2 Database Design Specification
version: 1.0
---

# Step 7.2 --- Database Design Specification

## 1. Purpose and authority

This is the authoritative relational database specification for the
Workflow service.

It defines tables, columns, keys, constraints, indexes, Flyway
migrations, and persistence rules for asynchronous publication and the
Transactional Outbox.

The approved domain semantics are in `PHASE_7_STEP_1_DOMAIN_DESIGN.md`.
Do not use database design to introduce business-instance ownership into
Workflow.

## 2. Existing repository constraints

Before editing files, inspect the current repository and read
`AGENTS.md`.

Current known constraints: - `workflow/` is an independent Spring Boot
application. - Workflow uses its own PostgreSQL schema named
`workflow`. - Workflow has an H2 profile with a separate Flyway
location. - Existing `V1__create_audit_events.sql` exists in both
migration locations. - Existing `workflow.audit_events` is independent
and must remain unchanged. - Hibernate uses `ddl-auto: validate`; Flyway
owns schema changes. - Java version is 25 and Spring Boot version is the
one already declared in `workflow/pom.xml`.

Do not modify or renumber V1. New migrations start at V2.

## 3. Database decisions

  -----------------------------------------------------------------------
  Concern                             Decision
  ----------------------------------- -----------------------------------
  Production relational DB            PostgreSQL

  Local/test relational DB            H2

  Schema                              `workflow`

  Migration tool                      Flyway

  Internal IDs                        BIGINT Identity

  Event IDs                           UUID

  JSON storage                        TEXT for PostgreSQL/H2 portability

  Timestamp storage                   `TIMESTAMP WITH TIME ZONE`

  Audit                               Existing `workflow.audit_events`,
                                      unchanged

  Cross-service FKs                   None

  ORM schema management               Hibernate validate only; never
                                      create/update schema
  -----------------------------------------------------------------------

JSON text is validated in the application layer. Do not rely on
database-specific JSON operators or PostgreSQL-only JSONB features in
the shared migration set.

## 4. Tables

New tables: 1. `workflow.workflow_definitions` 2.
`workflow.workflow_versions` 3. `workflow.workflow_states` 4.
`workflow.workflow_transitions` 5. `workflow.workflow_publication_jobs`
6. `workflow.workflow_outbox_events`

Existing table: - `workflow.audit_events` --- unchanged.

### 4.1 workflow_definitions

  -----------------------------------------------------------------------------
  Column               SQL type                          Null Rule
  -------------------- ---------------- --------------------- -----------------
  id                   BIGINT Identity                     No PK

  workflow_key         VARCHAR(100)                        No Unique, stable
                                                              business key

  name                 VARCHAR(200)                        No Non-empty

  description          VARCHAR(1000)                      Yes Optional

  status               VARCHAR(20)                         No ACTIVE, DISABLED,
                                                              ARCHIVED

  active_version_id    BIGINT                             Yes Same-Definition
                                                              Version FK

  pending_version_id   BIGINT                             Yes Same-Definition
                                                              Version FK

  created_at           TIMESTAMP WITH                      No Current timestamp
                       TIME ZONE                              by default

  created_by           VARCHAR(128)                        No Actor identifier

  updated_at           TIMESTAMP WITH                      No Current timestamp
                       TIME ZONE                              by default

  updated_by           VARCHAR(128)                        No Actor identifier

  row_version          BIGINT                              No Optimistic lock,
                                                              default 0
  -----------------------------------------------------------------------------

Constraints: - PK on `id`. - Unique `workflow_key`. - Status check. -
Active and pending Version cannot be the same. - Composite FKs ensure
active/pending Versions belong to this Definition. - Do not physically
delete Definitions.

### 4.2 workflow_versions

  -------------------------------------------------------------------------------------
  Column                    SQL type                          Null Rule
  ------------------------- ---------------- --------------------- --------------------
  id                        BIGINT Identity                     No PK

  definition_id             BIGINT                              No FK to Definition

  version_number            INTEGER                             No Positive, unique per
                                                                   Definition

  status                    VARCHAR(24)                         No DRAFT,
                                                                   PUBLISH_PENDING,
                                                                   PUBLISHED,
                                                                   PUBLICATION_FAILED

  context_schema_json       TEXT                                No JSON Schema Draft
                                                                   2020-12

  snapshot_json             TEXT                               Yes Canonical immutable
                                                                   Snapshot

  checksum                  CHAR(64)                           Yes SHA-256 hex

  snapshot_schema_version   SMALLINT                            No Positive, default 1

  created_at                TIMESTAMP WITH                      No Creation time
                            TIME ZONE                              

  created_by                VARCHAR(128)                        No Actor identifier

  published_at              TIMESTAMP WITH                     Yes Set after successful
                            TIME ZONE                              activation

  row_version               BIGINT                              No Optimistic lock,
                                                                   default 0
  -------------------------------------------------------------------------------------

Constraints: - PK on `id`. - FK `definition_id` to
`workflow_definitions(id)`. - Unique
`(definition_id, version_number)`. - Unique `(id, definition_id)` to
support same-Definition composite FKs. - Positive version number and
Snapshot schema version. - A PUBLISHED Version must have Snapshot JSON,
checksum, and published timestamp. - Published content is immutable at
the application layer; production DB permissions should also restrict
destructive operations where feasible.

### 4.3 workflow_states

  Column             SQL type            Null Rule
  ------------------ ----------------- ------ ------------------------------
  id                 BIGINT Identity       No PK
  version_id         BIGINT                No FK to Version
  state_key          VARCHAR(100)          No Unique per Version
  name               VARCHAR(200)          No Required
  description        VARCHAR(1000)        Yes Optional
  state_type         VARCHAR(20)           No INITIAL, INTERMEDIATE, FINAL
  display_order      INTEGER               No Default 0, non-negative
  ui_metadata_json   TEXT                 Yes Presentation-only JSON

Constraints: - PK on `id`. - FK to Version. - Unique
`(version_id, state_key)`. - Unique `(id, version_id)` for Transition
composite FKs. - State type check. - Non-negative display order. - State
key and name must not be blank.

The "exactly one INITIAL and at least one FINAL" rules are validated
before publication in the application layer. They cannot be expressed as
ordinary row-level CHECK constraints.

### 4.4 workflow_transitions

  Column                  SQL type            Null Rule
  ----------------------- ----------------- ------ ------------------------------
  id                      BIGINT Identity       No PK
  version_id              BIGINT                No Owning Version
  transition_key          VARCHAR(100)          No Unique per Version
  name                    VARCHAR(200)          No Required
  description             VARCHAR(1000)        Yes Optional
  from_state_id           BIGINT                No Source State in same Version
  to_state_id             BIGINT                No Target State in same Version
  guard_expression_json   TEXT                 Yes Restricted Guard DSL
  guard_schema_version    SMALLINT              No Positive, default 1
  display_order           INTEGER               No Default 0, non-negative

Constraints: - PK on `id`. - Unique `(version_id, transition_key)`. -
Composite FK `(from_state_id, version_id)` to
`workflow_states(id, version_id)`. - Composite FK
`(to_state_id, version_id)` to `workflow_states(id, version_id)`. -
Positive Guard schema version. - Non-negative display order. -
Transition key and name must not be blank. - If Guard JSON is present,
it must not be blank.

Composite foreign keys prevent a Transition from connecting States from
different Workflow Versions.

### 4.5 workflow_publication_jobs

  ----------------------------------------------------------------------------
  Column               SQL type                          Null Rule
  -------------------- ---------------- --------------------- ----------------
  id                   BIGINT Identity                     No PK

  version_id           BIGINT                              No Unique FK to
                                                              Version

  status               VARCHAR(32)                         No PENDING,
                                                              PROCESSING,
                                                              RETRY_WAIT,
                                                              SUCCEEDED,
                                                              FAILED

  attempt_count        INTEGER                             No Default 0,
                                                              non-negative

  max_attempts         INTEGER                             No Default 5,
                                                              positive

  next_attempt_at      TIMESTAMP WITH                     Yes Retry schedule
                       TIME ZONE                              

  locked_by            VARCHAR(128)                       Yes Worker
                                                              identifier

  locked_until         TIMESTAMP WITH                     Yes Lease expiry
                       TIME ZONE                              

  last_error_code      VARCHAR(100)                       Yes Sanitized code

  last_error_message   VARCHAR(1000)                      Yes Sanitized,
                                                              bounded message

  created_at           TIMESTAMP WITH                      No Creation time
                       TIME ZONE                              

  started_at           TIMESTAMP WITH                     Yes First/most
                       TIME ZONE                              recent start

  completed_at         TIMESTAMP WITH                     Yes Terminal
                       TIME ZONE                              completion

  updated_at           TIMESTAMP WITH                      No Last update
                       TIME ZONE                              

  row_version          BIGINT                              No Optimistic lock,
                                                              default 0
  ----------------------------------------------------------------------------

Constraints: - One Job per Version (`UNIQUE(version_id)`). - Attempt
count non-negative. - Maximum attempts positive. - PROCESSING requires
`locked_by` and `locked_until`. - Non-PROCESSING statuses must have both
lease columns null. - SUCCEEDED requires `completed_at`. - Manual retry
reuses the same Job row.

### 4.6 workflow_outbox_events

  ----------------------------------------------------------------------------
  Column               SQL type                          Null Rule
  -------------------- ---------------- --------------------- ----------------
  id                   BIGINT Identity                     No PK

  event_id             UUID                                No Globally unique
                                                              event ID

  aggregate_type       VARCHAR(64)                         No Aggregate type

  aggregate_id         BIGINT                              No Internal
                                                              aggregate ID;
                                                              not a
                                                              cross-service ID

  event_type           VARCHAR(150)                        No Versioned event
                                                              name

  event_version        SMALLINT                            No Positive

  routing_key          VARCHAR(200)                        No RabbitMQ routing
                                                              key

  payload_json         TEXT                                No Event payload
                                                              JSON

  headers_json         TEXT                               Yes Optional
                                                              metadata JSON

  correlation_id       UUID                               Yes Correlation ID

  causation_id         UUID                               Yes Causation ID

  traceparent          VARCHAR(128)                       Yes W3C traceparent

  status               VARCHAR(20)                         No PENDING,
                                                              PROCESSING,
                                                              RETRY_WAIT,
                                                              PUBLISHED,
                                                              FAILED

  attempt_count        INTEGER                             No Default 0,
                                                              non-negative

  next_attempt_at      TIMESTAMP WITH                     Yes Retry schedule
                       TIME ZONE                              

  locked_by            VARCHAR(128)                       Yes Publisher worker

  locked_until         TIMESTAMP WITH                     Yes Lease expiry
                       TIME ZONE                              

  created_at           TIMESTAMP WITH                      No Creation time
                       TIME ZONE                              

  published_at         TIMESTAMP WITH                     Yes Successful
                       TIME ZONE                              broker
                                                              confirmation

  last_error_code      VARCHAR(100)                       Yes Sanitized code

  last_error_message   VARCHAR(1000)                      Yes Sanitized
                                                              message
  ----------------------------------------------------------------------------

Constraints: - PK on `id`. - Unique `event_id`. - Positive event
version. - Non-negative attempt count. - PROCESSING requires a worker
and lease. - Non-PROCESSING statuses must have both lease columns
null. - PUBLISHED requires `published_at`. - No FK on
`(aggregate_type, aggregate_id)` because it is a polymorphic
infrastructure reference.

## 5. Entity Relationship Diagram

``` mermaid
erDiagram
    workflow_definitions ||--o{ workflow_versions : owns
    workflow_versions ||--o{ workflow_states : contains
    workflow_versions ||--o{ workflow_transitions : contains
    workflow_versions ||--o| workflow_publication_jobs : has
    workflow_states ||--o{ workflow_transitions : source
    workflow_states ||--o{ workflow_transitions : target

    workflow_definitions {
        bigint id PK
        varchar workflow_key UK
        varchar status
        bigint active_version_id FK
        bigint pending_version_id FK
        bigint row_version
    }

    workflow_versions {
        bigint id PK
        bigint definition_id FK
        integer version_number
        varchar status
        text context_schema_json
        text snapshot_json
        char checksum
        smallint snapshot_schema_version
    }

    workflow_states {
        bigint id PK
        bigint version_id FK
        varchar state_key
        varchar state_type
    }

    workflow_transitions {
        bigint id PK
        bigint version_id FK
        varchar transition_key
        bigint from_state_id FK
        bigint to_state_id FK
        text guard_expression_json
        smallint guard_schema_version
    }

    workflow_publication_jobs {
        bigint id PK
        bigint version_id FK_UK
        varchar status
        integer attempt_count
        timestamp next_attempt_at
        timestamp locked_until
    }

    workflow_outbox_events {
        bigint id PK
        uuid event_id UK
        varchar aggregate_type
        bigint aggregate_id
        varchar event_type
        varchar status
        timestamp published_at
    }
```

`workflow_definitions.active_version_id` and `pending_version_id` are
additional composite FKs to
`(workflow_versions.id, workflow_versions.definition_id)`. They are
omitted from the Mermaid relationship lines to keep the diagram
readable.

## 6. Index strategy

  --------------------------------------------------------------------------------------------------
  Table                       Index                                          Purpose
  --------------------------- ---------------------------------------------- -----------------------
  workflow_definitions        Unique `(workflow_key)`                        Resolve stable Workflow
                                                                             key

  workflow_definitions        `(status)`                                     Admin filtering

  workflow_versions           Unique `(definition_id, version_number)`       Version identity

  workflow_versions           `(definition_id, status)`                      Version
                                                                             listing/lifecycle

  workflow_states             Unique `(version_id, state_key)`               Resolve State key

  workflow_states             `(version_id, display_order)`                  Ordered UI listing

  workflow_transitions        Unique `(version_id, transition_key)`          Resolve Transition key

  workflow_transitions        `(version_id, from_state_id, display_order)`   List outgoing
                                                                             Transitions

  workflow_publication_jobs   `(status, next_attempt_at, created_at)`        Due Job selection

  workflow_publication_jobs   `(status, locked_until)`                       Expired lease recovery

  workflow_outbox_events      Unique `(event_id)`                            Event deduplication

  workflow_outbox_events      `(status, next_attempt_at, created_at)`        Due Outbox selection

  workflow_outbox_events      `(status, locked_until)`                       Expired publisher lease
                                                                             recovery

  workflow_outbox_events      `(aggregate_type, aggregate_id, created_at)`   Event investigation by
                                                                             aggregate
  --------------------------------------------------------------------------------------------------

Do not add speculative indexes on every column. Revisit index design
using query plans and observed workload.

## 7. Flyway migration plan

Existing V1 remains unchanged.

Create these files in both migration locations:

``` text
workflow/src/main/resources/db/migration/
  V1__create_audit_events.sql          # existing; do not edit
  V2__create_workflow_definition_schema.sql
  V3__create_workflow_publication_schema.sql
  V4__create_workflow_outbox_schema.sql

workflow/src/main/resources/db/migration-h2/
  V1__create_audit_events.sql          # existing; do not edit
  V2__create_workflow_definition_schema.sql
  V3__create_workflow_publication_schema.sql
  V4__create_workflow_outbox_schema.sql
```

The DDL below intentionally uses constructs supported by both PostgreSQL
and H2. Keep PostgreSQL and H2 migration files semantically identical
and test both Flyway locations.

## 8. Migration V2 --- definitions, versions, states, transitions

File: `V2__create_workflow_definition_schema.sql`

``` sql
CREATE TABLE workflow.workflow_definitions (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    workflow_key VARCHAR(100) NOT NULL,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(1000),
    status VARCHAR(20) NOT NULL,
    active_version_id BIGINT,
    pending_version_id BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(128) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(128) NOT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_workflow_definitions_key UNIQUE (workflow_key),
    CONSTRAINT ck_workflow_definitions_key_nonblank
        CHECK (LENGTH(TRIM(workflow_key)) > 0),
    CONSTRAINT ck_workflow_definitions_name_nonblank
        CHECK (LENGTH(TRIM(name)) > 0),
    CONSTRAINT ck_workflow_definitions_status
        CHECK (status IN ('ACTIVE', 'DISABLED', 'ARCHIVED')),
    CONSTRAINT ck_workflow_definitions_versions_different
        CHECK (
            active_version_id IS NULL
            OR pending_version_id IS NULL
            OR active_version_id <> pending_version_id
        ),
    CONSTRAINT ck_workflow_definitions_row_version
        CHECK (row_version >= 0)
);

CREATE TABLE workflow.workflow_versions (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    definition_id BIGINT NOT NULL,
    version_number INTEGER NOT NULL,
    status VARCHAR(24) NOT NULL,
    context_schema_json TEXT NOT NULL,
    snapshot_json TEXT,
    checksum CHAR(64),
    snapshot_schema_version SMALLINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(128) NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_workflow_versions_definition
        FOREIGN KEY (definition_id)
        REFERENCES workflow.workflow_definitions(id)
        ON DELETE RESTRICT,
    CONSTRAINT uq_workflow_versions_number
        UNIQUE (definition_id, version_number),
    CONSTRAINT uq_workflow_versions_id_definition
        UNIQUE (id, definition_id),
    CONSTRAINT ck_workflow_versions_number
        CHECK (version_number > 0),
    CONSTRAINT ck_workflow_versions_status
        CHECK (status IN ('DRAFT', 'PUBLISH_PENDING', 'PUBLISHED', 'PUBLICATION_FAILED')),
    CONSTRAINT ck_workflow_versions_context_schema_nonblank
        CHECK (LENGTH(TRIM(context_schema_json)) > 0),
    CONSTRAINT ck_workflow_versions_snapshot_schema_version
        CHECK (snapshot_schema_version > 0),
    CONSTRAINT ck_workflow_versions_checksum_length
        CHECK (checksum IS NULL OR LENGTH(TRIM(checksum)) = 64),
    CONSTRAINT ck_workflow_versions_row_version
        CHECK (row_version >= 0),
    CONSTRAINT ck_workflow_versions_published_data
        CHECK (
            status <> 'PUBLISHED'
            OR (
                snapshot_json IS NOT NULL
                AND checksum IS NOT NULL
                AND published_at IS NOT NULL
            )
        )
);

ALTER TABLE workflow.workflow_definitions
    ADD CONSTRAINT fk_workflow_definitions_active_version
    FOREIGN KEY (active_version_id, id)
    REFERENCES workflow.workflow_versions(id, definition_id)
    ON DELETE RESTRICT;

ALTER TABLE workflow.workflow_definitions
    ADD CONSTRAINT fk_workflow_definitions_pending_version
    FOREIGN KEY (pending_version_id, id)
    REFERENCES workflow.workflow_versions(id, definition_id)
    ON DELETE RESTRICT;

CREATE TABLE workflow.workflow_states (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    version_id BIGINT NOT NULL,
    state_key VARCHAR(100) NOT NULL,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(1000),
    state_type VARCHAR(20) NOT NULL,
    display_order INTEGER NOT NULL DEFAULT 0,
    ui_metadata_json TEXT,
    CONSTRAINT fk_workflow_states_version
        FOREIGN KEY (version_id)
        REFERENCES workflow.workflow_versions(id)
        ON DELETE RESTRICT,
    CONSTRAINT uq_workflow_states_key
        UNIQUE (version_id, state_key),
    CONSTRAINT uq_workflow_states_id_version
        UNIQUE (id, version_id),
    CONSTRAINT ck_workflow_states_key_nonblank
        CHECK (LENGTH(TRIM(state_key)) > 0),
    CONSTRAINT ck_workflow_states_name_nonblank
        CHECK (LENGTH(TRIM(name)) > 0),
    CONSTRAINT ck_workflow_states_type
        CHECK (state_type IN ('INITIAL', 'INTERMEDIATE', 'FINAL')),
    CONSTRAINT ck_workflow_states_display_order
        CHECK (display_order >= 0)
);

CREATE TABLE workflow.workflow_transitions (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    version_id BIGINT NOT NULL,
    transition_key VARCHAR(100) NOT NULL,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(1000),
    from_state_id BIGINT NOT NULL,
    to_state_id BIGINT NOT NULL,
    guard_expression_json TEXT,
    guard_schema_version SMALLINT NOT NULL DEFAULT 1,
    display_order INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT uq_workflow_transitions_key
        UNIQUE (version_id, transition_key),
    CONSTRAINT fk_workflow_transitions_version
        FOREIGN KEY (version_id)
        REFERENCES workflow.workflow_versions(id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_workflow_transitions_from_state
        FOREIGN KEY (from_state_id, version_id)
        REFERENCES workflow.workflow_states(id, version_id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_workflow_transitions_to_state
        FOREIGN KEY (to_state_id, version_id)
        REFERENCES workflow.workflow_states(id, version_id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_workflow_transitions_key_nonblank
        CHECK (LENGTH(TRIM(transition_key)) > 0),
    CONSTRAINT ck_workflow_transitions_name_nonblank
        CHECK (LENGTH(TRIM(name)) > 0),
    CONSTRAINT ck_workflow_transitions_guard_nonblank
        CHECK (
            guard_expression_json IS NULL
            OR LENGTH(TRIM(guard_expression_json)) > 0
        ),
    CONSTRAINT ck_workflow_transitions_guard_schema_version
        CHECK (guard_schema_version > 0),
    CONSTRAINT ck_workflow_transitions_display_order
        CHECK (display_order >= 0)
);

CREATE INDEX idx_workflow_definitions_status
    ON workflow.workflow_definitions (status);

CREATE INDEX idx_workflow_versions_definition_status
    ON workflow.workflow_versions (definition_id, status);

CREATE INDEX idx_workflow_states_version_order
    ON workflow.workflow_states (version_id, display_order);

CREATE INDEX idx_workflow_transitions_from_state
    ON workflow.workflow_transitions (version_id, from_state_id, display_order);
```

## 9. Migration V3 --- Publication Jobs

File: `V3__create_workflow_publication_schema.sql`

``` sql
CREATE TABLE workflow.workflow_publication_jobs (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    version_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 5,
    next_attempt_at TIMESTAMP WITH TIME ZONE,
    locked_by VARCHAR(128),
    locked_until TIMESTAMP WITH TIME ZONE,
    last_error_code VARCHAR(100),
    last_error_message VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_workflow_publication_jobs_version
        FOREIGN KEY (version_id)
        REFERENCES workflow.workflow_versions(id)
        ON DELETE RESTRICT,
    CONSTRAINT uq_workflow_publication_jobs_version
        UNIQUE (version_id),
    CONSTRAINT ck_workflow_publication_jobs_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'RETRY_WAIT', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT ck_workflow_publication_jobs_attempt_count
        CHECK (attempt_count >= 0),
    CONSTRAINT ck_workflow_publication_jobs_max_attempts
        CHECK (max_attempts > 0),
    CONSTRAINT ck_workflow_publication_jobs_lease
        CHECK (
            (
                status = 'PROCESSING'
                AND locked_by IS NOT NULL
                AND locked_until IS NOT NULL
            )
            OR
            (
                status <> 'PROCESSING'
                AND locked_by IS NULL
                AND locked_until IS NULL
            )
        ),
    CONSTRAINT ck_workflow_publication_jobs_succeeded_time
        CHECK (status <> 'SUCCEEDED' OR completed_at IS NOT NULL),
    CONSTRAINT ck_workflow_publication_jobs_row_version
        CHECK (row_version >= 0)
);

CREATE INDEX idx_workflow_publication_jobs_due
    ON workflow.workflow_publication_jobs (status, next_attempt_at, created_at);

CREATE INDEX idx_workflow_publication_jobs_lease
    ON workflow.workflow_publication_jobs (status, locked_until);
```

## 10. Migration V4 --- Transactional Outbox

File: `V4__create_workflow_outbox_schema.sql`

``` sql
CREATE TABLE workflow.workflow_outbox_events (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    event_id UUID NOT NULL,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id BIGINT NOT NULL,
    event_type VARCHAR(150) NOT NULL,
    event_version SMALLINT NOT NULL,
    routing_key VARCHAR(200) NOT NULL,
    payload_json TEXT NOT NULL,
    headers_json TEXT,
    correlation_id UUID,
    causation_id UUID,
    traceparent VARCHAR(128),
    status VARCHAR(20) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE,
    locked_by VARCHAR(128),
    locked_until TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP WITH TIME ZONE,
    last_error_code VARCHAR(100),
    last_error_message VARCHAR(1000),
    CONSTRAINT uq_workflow_outbox_event_id UNIQUE (event_id),
    CONSTRAINT ck_workflow_outbox_aggregate_type
        CHECK (LENGTH(TRIM(aggregate_type)) > 0),
    CONSTRAINT ck_workflow_outbox_event_type
        CHECK (LENGTH(TRIM(event_type)) > 0),
    CONSTRAINT ck_workflow_outbox_event_version
        CHECK (event_version > 0),
    CONSTRAINT ck_workflow_outbox_payload_nonblank
        CHECK (LENGTH(TRIM(payload_json)) > 0),
    CONSTRAINT ck_workflow_outbox_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'RETRY_WAIT', 'PUBLISHED', 'FAILED')),
    CONSTRAINT ck_workflow_outbox_attempt_count
        CHECK (attempt_count >= 0),
    CONSTRAINT ck_workflow_outbox_lease
        CHECK (
            (
                status = 'PROCESSING'
                AND locked_by IS NOT NULL
                AND locked_until IS NOT NULL
            )
            OR
            (
                status <> 'PROCESSING'
                AND locked_by IS NULL
                AND locked_until IS NULL
            )
        ),
    CONSTRAINT ck_workflow_outbox_published_time
        CHECK (status <> 'PUBLISHED' OR published_at IS NOT NULL)
);

CREATE INDEX idx_workflow_outbox_due
    ON workflow.workflow_outbox_events (status, next_attempt_at, created_at);

CREATE INDEX idx_workflow_outbox_lease
    ON workflow.workflow_outbox_events (status, locked_until);

CREATE INDEX idx_workflow_outbox_aggregate
    ON workflow.workflow_outbox_events (aggregate_type, aggregate_id, created_at);
```

## 11. PostgreSQL and H2 parity

For each V2--V4 migration: - Create a PostgreSQL migration under
`db/migration`. - Create the matching H2 migration under
`db/migration-h2`. - Keep table/column names, constraints, defaults, and
index semantics identical. - The SQL shown in this document is the
baseline for both engines. - If a syntax adjustment is required for H2,
document it and prove semantic equivalence with tests. - Do not change
the existing H2 V1 audit migration. - Ensure schema `workflow` exists
before creating qualified tables; current Flyway configuration is
expected to create/manage it. - Run Flyway from an empty PostgreSQL
schema and an empty H2 database. - Run Hibernate schema validation after
migrations.

## 12. Publication persistence and transaction boundaries

### 12.1 Requesting publication

Within one PostgreSQL transaction: 1. Lock the Definition row. 2. Verify
Definition status is `ACTIVE`. 3. Verify `pending_version_id` is null.
4. Verify target Version belongs to the Definition and is `DRAFT`. 5.
Validate the Version and its graph. 6. Create one Publication Job with
`PENDING`. 7. Change Version to `PUBLISH_PENDING`. 8. Set Definition
`pending_version_id`. 9. Commit.

The HTTP layer returns `202 Accepted` with a publication/job identifier
and current status. It must not wait for Redis or RabbitMQ.

### 12.2 Claiming Jobs

PostgreSQL workers may use `SELECT ... FOR UPDATE SKIP LOCKED` to claim
work. A claim must: - Set status to `PROCESSING`. - Increment attempt
count. - Set `locked_by`. - Set `locked_until`. - Commit the claim
quickly; do not hold a database transaction open during Redis/network
operations.

Example PostgreSQL selection pattern:

``` sql
SELECT id
FROM workflow.workflow_publication_jobs
WHERE status IN ('PENDING', 'RETRY_WAIT')
  AND (next_attempt_at IS NULL OR next_attempt_at <= CURRENT_TIMESTAMP)
ORDER BY created_at, id
FOR UPDATE SKIP LOCKED
LIMIT 1;
```

Selection and claim update must occur in one transaction.

H2 may not provide identical concurrency semantics for `SKIP LOCKED`.
Use an H2-compatible claim implementation for local tests, or test the
worker concurrency contract against PostgreSQL Testcontainers. Do not
claim that H2 proves PostgreSQL locking behavior.

Expired `PROCESSING` leases must be recoverable. A worker may continue
only while it owns a valid lease or has safely reclaimed it.

### 12.3 Completing publication

After Redis Snapshot storage and active-pointer activation succeed, one
PostgreSQL transaction must: - Mark Version `PUBLISHED`. - Set
`published_at`. - Set Definition `active_version_id` to the new
Version. - Clear Definition `pending_version_id`. - Mark Publication Job
`SUCCEEDED`. - Set `completed_at`. - Insert the Outbox Event.

The Outbox row and successful publication state must commit atomically.

### 12.4 Crash window

Redis and PostgreSQL are not a distributed transaction.

If Redis active pointer changes but the final PostgreSQL transaction
fails: - Do not immediately mark the Version `PUBLICATION_FAILED`. - On
retry/recovery, read the Redis active pointer. - If it already points to
the same candidate Version and checksum, finish the PostgreSQL
transaction idempotently. - If it points to the expected previous
Version, retry pointer activation. - If it points to an unexpected
Version, stop and reconcile; do not overwrite blindly. - Do not clear
`pending_version_id` while Redis may point to the candidate. - Keep the
old Version Snapshot.

If the Outbox Publisher fails after publication completion, the Version
remains `PUBLISHED`; only the Outbox event is retried.

## 13. Retry and failure policy

Initial publication retry schedule: - 5 seconds - 15 seconds - 45
seconds - 2 minutes - 5 minutes

Defaults: - `max_attempts = 5`. - `attempt_count` increments for each
worker attempt. - Use jitter if appropriate. - Retry transient
Redis/network/temporary database failures. - Do not retry deterministic
validation failures. - When automatic attempts are exhausted and it is
safe to do so, set Job `FAILED` and Version `PUBLICATION_FAILED`. -
Manual retry reuses the same Job and Version; it must not mutate Version
content. - Preserve a sanitized error code/message and audit
administrative retry.

A Job must not be terminally failed while Redis may already point to its
candidate Version. Reconciliation takes precedence.

## 14. Outbox behavior

-   Insert the publication event in the same PostgreSQL transaction that
    marks the Version PUBLISHED.
-   Publisher claims rows with a lease and publishes outside the claim
    transaction.
-   Use RabbitMQ publisher confirms.
-   Treat unroutable/returned messages as failures, not successful
    delivery.
-   Mark `PUBLISHED` only after confirmation and routing checks succeed.
-   A crash after broker acceptance but before DB status update can
    cause duplicate delivery.
-   Delivery is at-least-once; consumers must be idempotent.
-   Use `event_id` as deduplication identity.
-   Keep event payload free of business Context values and sensitive
    information.

Initial publication event: - `event_type`:
`workflow.definition.published` - `event_version`: `1` - `routing_key`:
`workflow.definition.published`

Exchange, queue, binding, DLQ, and exact RabbitMQ retry topology are
specified in Step 7.7.

## 15. JSON and checksum validation

-   Store Guard, Context Schema, Snapshot, payload, and headers as TEXT.
-   Validate JSON syntax before persistence.
-   Validate Context Schema using JSON Schema Draft 2020-12.
-   Validate Guard structure using the approved DSL validator.
-   Do not rely on database JSON parsing extensions.
-   Snapshot checksum is SHA-256 over deterministic canonical UTF-8
    JSON.
-   Explicitly select and test the canonicalization algorithm before
    production.
-   Checksum must be 64 hexadecimal characters.
-   Never compute checksum from incidental/non-deterministic object
    serialization.

## 16. Retention and deletion

-   Do not physically delete Workflow Definitions.
-   Do not automatically delete Published Versions.
-   Do not automatically delete versioned Redis snapshots.
-   Define Outbox retention before implementing cleanup.
-   Existing Audit is append-only at application level and is not part
    of new-table cleanup.
-   Any future cleanup must preserve the active Version and versions
    pinned by consumers.

## 17. Implementation tasks for the Database work package

The coding agent must: 1. Inspect current Workflow Flyway configuration
and both migration directories. 2. Preserve V1 exactly. 3. Add
PostgreSQL V2, V3, and V4 migrations. 4. Add matching H2 V2, V3, and V4
migrations. 5. Add JPA mappings only in the persistence adapter package;
do not leak JPA annotations into Domain objects unless the existing
architecture explicitly requires it. 6. Map `row_version` fields using
optimistic locking. 7. Represent composite same-Version foreign keys
correctly. 8. Keep Hibernate `ddl-auto: validate`. 9. Add migration
integration tests for PostgreSQL and H2. 10. Add repository tests for
uniqueness, FK enforcement, check constraints, and publication/outbox
query paths. 11. Verify Flyway upgrade from the existing V1 state, not
only from an empty schema. 12. Do not alter AUTH or DMS migrations.

## 18. Required tests and acceptance criteria

### Migration tests

-   Empty PostgreSQL schema migrates through V1--V4.
-   Existing PostgreSQL V1 schema upgrades through V2--V4.
-   Empty H2 database migrates through V1--V4.
-   Existing H2 V1 database upgrades through V2--V4.
-   Hibernate schema validation succeeds after migration.

### Constraint tests

-   Duplicate `workflow_key` rejected.
-   Duplicate `(definition_id, version_number)` rejected.
-   Duplicate `(version_id, state_key)` rejected.
-   Duplicate `(version_id, transition_key)` rejected.
-   Transition cannot reference a State from another Version.
-   Active/pending Version cannot belong to another Definition.
-   A Definition cannot point to the same Version as active and pending.
-   Invalid enum/status values rejected.
-   Negative attempts/version numbers/display orders rejected.
-   Duplicate event IDs rejected.
-   PROCESSING rows without leases rejected.
-   Non-PROCESSING rows with stale lease values rejected.
-   PUBLISHED Outbox rows without `published_at` rejected.
-   PUBLISHED Versions without Snapshot/checksum/published timestamp
    rejected.

### Query and concurrency tests

-   Due Publication Jobs can be claimed without two workers owning the
    same row.
-   Expired leases can be recovered.
-   A Definition cannot have two pending publications.
-   One Publication Job exists per Version.
-   Outbox publisher claims are lease-protected.
-   Duplicate event delivery is safe for consumers.

### Scope tests

-   No workflow-instance or consumer business-state tables are created.
-   No foreign key points to AUTH or DMS.
-   Existing `workflow.audit_events` remains unchanged.

## 19. Explicitly out of scope

Do not add: - Workflow instance tables. - State history tables for
AUTH/DMS. - Inbox tables before Workflow becomes a consumer. - Guard
tables. - Context value tables. - Event-to-transition binding tables. -
RabbitMQ exchange/queue declarations in SQL. - PostgreSQL-only JSONB
columns/operators in shared migrations. - Automatic deletion of
published versions. - Cross-service foreign keys. - Changes to the
existing Audit table or V1 migration.

## 20. Definition of done

Step 7.2 design is complete because this specification is approved.

Database implementation is complete only when: - V2--V4 exist in both
Flyway locations. - PostgreSQL and H2 migration paths pass. - Hibernate
validation passes. - Constraint and repository tests pass. - V1 and the
existing Audit schema are unchanged. - The resulting schema matches this
document or has an explicitly approved ADR for any deviation.
