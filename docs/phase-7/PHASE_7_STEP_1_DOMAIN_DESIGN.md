---
date: 2026-11-17
status: Approved
title: Phase 7 --- Step 7.1 Domain Design Specification
version: 1.0
---

# Step 7.1 --- Domain Design Specification

## 1. Purpose and authority

This is the authoritative domain specification for the Generic Workflow
Engine in Phase 7.

It records approved domain concepts and invariants for coding agents. If
implementation convenience conflicts with a rule here, the domain rule
wins. If a required behavior is unspecified, do not invent a
cross-service or business rule; raise the ambiguity to the project
owner.

Related documents: - `PHASE_7_ROADMAP.md` -
`PHASE_7_STEP_2_DATABASE_DESIGN.md` - Repository `AGENTS.md`

## 2. Bounded context and ownership

### 2.1 Workflow bounded context

Workflow owns: - Workflow identity (`workflow_key`) and definitions. -
Numbered Workflow Versions. - State and Transition definitions. - Guard
expressions. - Per-Version Context Schema. - Definition validation. -
Publication lifecycle and Publication Jobs. - Immutable snapshots in
Redis. - Publication notifications through RabbitMQ.

### 2.2 Explicitly outside Workflow

Workflow must not own or persist: - Business workflow instances for
users, files, verification requests, or other records. - Current state
of a business instance. - Business transition history. -
Consumer-specific Context values. - Consumer-specific authorization/role
decisions. - AUTH or DMS entities or foreign keys.

For example, AUTH owns the current verification request status and its
history. Workflow supplies the definition AUTH reads to validate a
requested transition.

### 2.3 Runtime interaction

1.  Workflow publishes an immutable versioned snapshot to Redis.
2.  Workflow emits a publication notification through RabbitMQ.
3.  A consumer receives the notification and reads the snapshot from
    Redis.
4.  When a business action occurs, the consumer reads the definition
    from Redis, validates the Transition and Guard locally, and updates
    its own database.
5.  The consumer records its own business audit/history.

Consumers have read-only access to Workflow Redis keys. They do not call
Workflow synchronously to decide whether a Transition is valid.

## 3. Architecture boundaries

Use DDD concepts with Hexagonal / Ports-and-Adapters boundaries.

Suggested package layout (adapt to the existing package root; do not
create a second application root):

``` text
com.example.videokyc.workflow
├── domain
│   ├── definition
│   ├── version
│   ├── state
│   ├── transition
│   ├── publication
│   └── shared
├── application
│   ├── port
│   │   ├── in
│   │   └── out
│   └── service
├── adapter
│   ├── in
│   │   ├── web
│   │   └── worker
│   └── out
│       ├── persistence
│       ├── redis
│       └── messaging
└── config
```

Keep the Domain layer free of Spring MVC, Mustache, Redis client,
RabbitMQ client, and JPA-specific behavior wherever practical. Do not
introduce abstractions without a use case.

### 3.1 Aggregate boundaries

  -----------------------------------------------------------------------
  Aggregate Root          Owns                    Does not own
  ----------------------- ----------------------- -----------------------
  `WorkflowDefinition`    Stable key, descriptive State/Transition graph
                          metadata,               contents
                          administrative status,  
                          active-version          
                          reference,              
                          pending-version         
                          reference,              
                          optimistic-lock version 

  `WorkflowVersion`       Version number,         Business instances
                          lifecycle status,       
                          Context Schema, States, 
                          Transitions, immutable  
                          Snapshot and checksum   

  `PublicationJob`        Publication attempt     Definition graph
                          lifecycle, lease, retry 
                          schedule, safe failure  
                          details                 

  `OutboxEvent`           Durable event           RabbitMQ broker state
                          publication state       
  -----------------------------------------------------------------------

`WorkflowState` and `WorkflowTransition` are children of the
`WorkflowVersion` aggregate. A Guard is a value object owned by a
Transition. Context Schema is a value object owned by a Version.

A Definition and a Version are related by `definition_id`, but the
Version graph is managed through the Version aggregate. Do not load and
mutate every historical Version whenever Definition metadata changes.

## 4. Domain entities and value objects

### 4.1 WorkflowDefinition

Fields: - Internal `id`: BIGINT Identity, persistence identity only. -
`workflowKey`: stable business key, unique, immutable after creation. -
`name`: required display name. - `description`: optional description. -
`status`: `ACTIVE`, `DISABLED`, or `ARCHIVED`. - `activeVersionId`:
nullable reference to the currently active published Version. -
`pendingVersionId`: nullable reference to the one Version currently
being published. - `createdAt`, `createdBy`, `updatedAt`, `updatedBy`. -
`rowVersion`: optimistic-lock value.

Rules: - `workflowKey` is required, unique, and immutable. - A
Definition can have many Versions. - A Definition can have at most one
pending publication at a time. - Active and pending Version references,
when set, must point to Versions belonging to this Definition. - Active
and pending Version IDs cannot be equal. - `DISABLED` means consumers
must not start new transitions using the Definition, even if an active
snapshot remains in Redis. - `ARCHIVED` is a terminal administrative
status for normal UI operations; do not physically delete the
Definition. - `ACTIVE` means administratively enabled. A Definition
without an active published Version is not executable.

### 4.2 WorkflowKey

-   Non-empty, trimmed string.
-   Maximum length: 100 characters.
-   Stable across Versions.
-   Used in Redis keys and consumer contracts.
-   Must not be replaced with the database ID.
-   Recommended initial format: uppercase letters, digits, underscore,
    and hyphen.

### 4.3 WorkflowVersion

Fields: - Internal `id`: BIGINT Identity. - `definitionId`. -
`versionNumber`: positive integer, unique within the Definition. -
`status`: `DRAFT`, `PUBLISH_PENDING`, `PUBLISHED`,
`PUBLICATION_FAILED`. - `contextSchema`: required JSON Schema
document. - `snapshotJson`: nullable until Snapshot generation. -
`checksum`: nullable until Snapshot generation. -
`snapshotSchemaVersion`: positive integer, initially 1. - `createdAt`,
`createdBy`, `publishedAt`. - `rowVersion`: optimistic-lock value.

Rules: - Version numbers are monotonically increasing within a
Definition. Do not reuse a number, including after failed publication. -
A Version can be edited only while `DRAFT`. - A failed Version can be
manually retried for publication; it must not become editable again. To
change content, create a new Draft Version. - A `PUBLISHED` Version is
immutable. - Publishing a new Version does not mutate or delete the
previous published Version. - A Version owns exactly one Context
Schema. - The Snapshot is the complete consumer-facing definition for
that Version. - The Snapshot must not contain internal database IDs.

Version lifecycle:

``` text
DRAFT
  |
  | request publication after successful validation
  v
PUBLISH_PENDING
  |                    |
  | publication OK     | retry limit exhausted / unrecoverable failure
  v                    v
PUBLISHED         PUBLICATION_FAILED
                       |
                       | explicit admin retry (same immutable content)
                       v
                  PUBLISH_PENDING
```

### 4.4 VersionNumber

-   Positive integer.
-   Unique for a given Definition.
-   Monotonically increasing.
-   Not a database primary key.

### 4.5 WorkflowState

Fields: - Internal `id`: BIGINT Identity. - `versionId`. - `stateKey`. -
`name`. - Optional `description`. - `stateType`: `INITIAL`,
`INTERMEDIATE`, or `FINAL`. - `displayOrder`. - Optional `uiMetadata`.

Rules: - `stateKey` is non-empty and unique within a Version. - A
Version must contain exactly one `INITIAL` State before publication. - A
Version must contain at least one `FINAL` State before publication. - A
State belongs to exactly one Version. - A State cannot be used as a
Transition endpoint in another Version. - State graph changes are
permitted only while the owning Version is `DRAFT`. - `uiMetadata` is
presentation metadata only; it must not control runtime authorization or
business logic.

### 4.6 StateKey

-   Non-empty string.
-   Maximum length: 100 characters.
-   Unique within a Version.
-   Stable within that Version.
-   Recommended initial format: uppercase letters, digits, underscore,
    and hyphen.

### 4.7 WorkflowTransition

Fields: - Internal `id`: BIGINT Identity. - `versionId`. -
`transitionKey`. - `name`. - Optional `description`. - `fromStateId`. -
`toStateId`. - Optional `guardExpression`. - `guardSchemaVersion`,
initially 1. - `displayOrder`.

Rules: - `transitionKey` is unique within a Version. - Source and target
States must belong to the same Version as the Transition. - A Transition
cannot originate from a `FINAL` State. - A Transition may have no Guard.
No Guard means there is no additional Guard condition; it does not
bypass consumer authorization or other validation. - Multiple
Transitions between the same pair of States are not prohibited solely by
the pair; each Transition must have a unique key. - Do not prohibit
self-transitions unless the project owner explicitly approves it. - A
Transition is immutable after its Version is published.

### 4.8 TransitionKey

-   Non-empty string.
-   Maximum length: 100 characters.
-   Unique within a Version.

## 5. Guard DSL

### 5.1 Approved structure

Guard is a restricted JSON Abstract Syntax Tree (AST), not a
general-purpose expression language.

Supported logical nodes: - `ALL`: every child must evaluate to true. -
`ANY`: at least one child must evaluate to true. - `NOT`: exactly one
child expression, logically negated.

Supported leaf operators: - `EQ` - `NE` - `GT` - `GTE` - `LT` - `LTE` -
`IN` - `NOT_IN` - `EXISTS` - `NOT_EXISTS`

Initial limits: - Maximum tree depth: 8. - Maximum total nodes: 100. -
Maximum Guard JSON size: 32 KB. - Empty `ALL`/`ANY` child lists are
invalid. - `NOT` must have exactly one child. - Leaf nodes must contain
a valid `path` and supported operator. - `EXISTS` and `NOT_EXISTS` do
not require a comparison `value`. - Other operators require a value of a
compatible JSON type.

Example:

``` json
{
  "type": "ALL",
  "conditions": [
    {
      "path": "verification.documentsComplete",
      "operator": "EQ",
      "value": true
    },
    {
      "type": "ANY",
      "conditions": [
        {
          "path": "verification.riskScore",
          "operator": "LTE",
          "value": 50
        },
        {
          "path": "verification.manualApproval",
          "operator": "EQ",
          "value": true
        }
      ]
    }
  ]
}
```

### 5.2 Path convention

Use the dotted path convention shown in the approved examples, such as
`verification.riskScore`.

For the first version: - Each path segment must be a simple JSON
property name. - Do not support array indexes, wildcard traversal,
executable expressions, method calls, or arbitrary path languages. - If
a property name needs dots or special path syntax, raise the requirement
before expanding the contract.

### 5.3 Evaluation semantics

-   Evaluation is deterministic: the same Guard and Context produce the
    same result.
-   Evaluation is side-effect-free.
-   No implicit String/Number/Boolean coercion.
-   Numeric comparisons accept JSON numeric values only.
-   `IN` and `NOT_IN` require an array of compatible values.
-   `EXISTS` means the path is present; a present property with JSON
    null is still present.
-   `NOT_EXISTS` means the path is absent.
-   A type mismatch is an evaluation/validation error, not a silently
    coerced comparison.
-   A missing path for a comparison operator must not be replaced with a
    default value. Recommended behavior: missing path makes the leaf
    false; malformed or type-invalid Context is rejected before Guard
    evaluation.
-   Do not let `NOT` hide malformed Context or type errors.

### 5.4 Security constraints

Never execute: - Java/Kotlin or other source code. - SpEL or Spring
expression strings. - JavaScript. - SQL. - Reflection or arbitrary class
loading. - User-defined functions or network calls.

Guard evaluation must not access databases, files, HTTP endpoints,
Redis, or RabbitMQ.

### 5.5 Guard validation against Context Schema

At publication time: - Parse the Guard. - Validate AST structure and
limits. - Validate every path against the Version's Context Schema where
possible. - Check operator compatibility with the declared JSON Schema
type. - Reject unknown operators and malformed nodes. - Do not publish a
Guard that references an undeclared path unless the Schema explicitly
permits additional properties and compatibility can be established
safely.

## 6. Context Schema

### 6.1 Approved standard

Each Workflow Version must have its own JSON Schema using JSON Schema
Draft 2020-12.

The schema is stored in `workflow_versions.context_schema_json` and
included in the published Snapshot.

### 6.2 Ownership and runtime

-   The consumer creates the actual Context from its own business data.
-   Workflow stores only the Schema, not the actual Context.
-   The consumer reads the Version Snapshot from Redis.
-   The consumer validates Context against the Version Schema before
    Guard evaluation.
-   Context validation is deterministic and local to the consumer
    runtime.
-   Do not send sensitive or unnecessary personal data in Context.
-   Schema changes require a new Workflow Version.

Example:

``` json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "type": "object",
  "properties": {
    "verification": {
      "type": "object",
      "properties": {
        "documentsComplete": { "type": "boolean" },
        "riskScore": {
          "type": "integer",
          "minimum": 0,
          "maximum": 100
        },
        "videoReviewed": { "type": "boolean" }
      },
      "required": [
        "documentsComplete",
        "riskScore",
        "videoReviewed"
      ],
      "additionalProperties": false
    }
  },
  "required": ["verification"],
  "additionalProperties": false
}
```

### 6.3 Schema safety

-   Validate the Schema itself before publication.
-   Do not resolve remote `$ref` URLs at runtime.
-   External network reference resolution is disabled.
-   If local `$ref` support is implemented, it must be bounded to the
    same Schema document and covered by tests.
-   Define maximum Schema size and nesting limits in configuration.
-   Keep the exact JSON Schema dialect in the Snapshot.

## 7. Workflow Snapshot

### 7.1 Purpose

A Snapshot is the immutable, consumer-facing representation of one
published Workflow Version.

It contains: - Snapshot schema version. - `workflowKey`. -
`versionNumber`. - Context Schema and dialect. - States with stable
`stateKey` values. - Transitions with stable keys, source/target state
keys, and optional Guard. - Guard DSL schema version, when present. -
Only consumer-relevant metadata.

It must not contain: - Database primary keys. - JPA implementation
details. - Mutable Publication Job status. - User credentials or
sensitive Context values. - Internal filesystem paths or
service-specific implementation details.

### 7.2 Canonicalization and checksum

-   Serialize Snapshot as deterministic canonical JSON.
-   Encode as UTF-8.
-   Compute SHA-256 over the exact canonical bytes.
-   Store the 64-character hexadecimal checksum in PostgreSQL and the
    Redis pointer.
-   Consumers may verify the checksum before use.
-   Snapshot schema version is independent of Workflow version number
    and Guard DSL version.

The canonicalization algorithm must be explicitly selected and covered
by cross-test vectors before implementation. Do not rely on incidental
Jackson property ordering.

## 8. Publication domain

### 8.1 PublicationJob

Statuses: - `PENDING` - `PROCESSING` - `RETRY_WAIT` - `SUCCEEDED` -
`FAILED`

Rules: - One Publication Job per Version. - A failed Job can be manually
retried by resetting the same Job to `PENDING`; do not create a second
Job for the same Version. - `attemptCount` is non-negative. - A
processing Job must have a worker identity and lease expiry. - Expired
processing leases are eligible for recovery. - Error code/message must
be sanitized and bounded. - The Job becomes `SUCCEEDED` only after Redis
activation and the PostgreSQL publication-completion transaction are
confirmed.

### 8.2 Job lifecycle

``` text
PENDING -----------+
                   |
RETRY_WAIT --------+----> PROCESSING ----> SUCCEEDED
                              |
                              +-----------> RETRY_WAIT
                              |
                              +-----------> FAILED
```

`FAILED` can be explicitly retried by an authorized administrator. Retry
uses the same immutable Version content and same Job row.

### 8.3 Retry policy

Initial default: - Maximum automatic attempts: 5. - Backoff: 5 seconds,
15 seconds, 45 seconds, 2 minutes, 5 minutes. - Add jitter if needed to
prevent synchronized retries. - The retry count and schedule must be
testable. - Do not retry permanent validation errors as transient
Redis/network errors.

## 9. Publication consistency protocol

PostgreSQL and Redis do not share a distributed transaction. Use a
recoverable, idempotent protocol.

1.  In one PostgreSQL transaction:
    -   Lock the Definition row.
    -   Verify Definition is enabled and no other Version is pending.
    -   Verify target Version is `DRAFT`.
    -   Validate Definition, Version, State, Transition, Guard, and
        Context Schema.
    -   Create/confirm the single Publication Job.
    -   Set Version to `PUBLISH_PENDING`.
    -   Set Definition `pending_version_id`.
    -   Commit.
2.  Worker claims the Job using a lease.
3.  Worker creates or reuses the deterministic Snapshot and checksum.
4.  Worker writes the immutable version key in Redis.
5.  Worker verifies stored Snapshot/checksum.
6.  Worker atomically changes the active pointer using compare-and-set
    against the expected previous active Version.
7.  In one PostgreSQL transaction:
    -   Mark Version `PUBLISHED`.
    -   Set `published_at`.
    -   Set Definition `active_version_id` to the new Version.
    -   Clear `pending_version_id`.
    -   Mark Job `SUCCEEDED`.
    -   Insert the publication Outbox Event.
8.  Outbox Publisher delivers the event to RabbitMQ independently.

Crash/recovery rules: - Before Redis pointer activation: old pointer
remains active; retry is safe. - After pointer activation but before
final PostgreSQL transaction: recovery must detect Redis already points
to the candidate Version and complete the PostgreSQL transaction
idempotently. - Do not mark a Version terminally failed or clear its
pending reference while Redis may already point to it. - If Redis points
to an unexpected Version, do not overwrite it blindly. Stop and
reconcile. - Previous active Snapshot remains stored. - RabbitMQ
unavailability does not roll back a successfully published Redis
Snapshot. Outbox remains retryable.

## 10. Redis contract

Keys:

``` text
workflow:definitions:{WORKFLOW_KEY}:active
workflow:definitions:{WORKFLOW_KEY}:versions:{VERSION_NUMBER}
```

Version key value: canonical Snapshot JSON.

Active pointer example:

``` json
{
  "workflowKey": "VIDEO_KYC",
  "version": 2,
  "checksum": "64-character-sha256-hex"
}
```

Rules: - Workflow is the only writer. - Consumers are read-only. -
Active pointer update is atomic and compare-and-set protected. -
Versioned snapshots are immutable. - No default TTL. - No automatic
deletion of old versions. - Missing or invalid active pointer must fail
closed; consumers must not guess a Version.

## 11. Outbox event concept

Initial event: - Event type: `workflow.definition.published` - Event
version: `1` - Routing key: `workflow.definition.published`

The final exchange, queue, binding, retry, and DLQ topology belongs to
Step 7.7.

Event payload contains only publication metadata, for example: -
`eventId` - `eventType` - `eventVersion` - `workflowKey` -
`versionNumber` - `checksum` - `publishedAt` - `correlationId` -
`causationId`, when available

Do not publish business Context values. Consumers read the Snapshot from
Redis.

Delivery is at-least-once. Consumers must deduplicate by `eventId` or
otherwise process idempotently.

## 12. Authorization and audit separation

-   Guard is not authorization.
-   Workflow Admin actions must be protected by Spring Security.
-   Consumer-specific role/permission checks remain in the consumer
    service.
-   A Guard cannot grant an actor permission to approve, reject, or
    modify a business record.
-   Keep Workflow audit records in `workflow.audit_events`.
-   Do not centralize AUTH, DMS, and Workflow audit tables.
-   Keep Operational Logs, Audit, and Distributed Traces distinct.
-   Never store passwords, hashes, access tokens, cookies, video bytes,
    or unnecessary personal data in Guard, Context Schema, Snapshot,
    Audit, or Logs.

## 13. Domain implementation acceptance criteria

A conforming implementation must demonstrate: - Valid WorkflowKey and
VersionNumber value objects. - Definition and Version lifecycle
invariants. - State/Transition ownership and same-Version constraints. -
Exactly one INITIAL and at least one FINAL at publication validation. -
Immutable published Version behavior. - Guard AST parsing and structural
validation. - Guard operator/type validation. - Context Schema
validation using the approved dialect. - Deterministic Snapshot
generation and checksum tests. - No arbitrary expression execution. - No
Workflow instance/current-state/history persistence. - No dependency
from Workflow to AUTH or DMS. - Unit tests for valid and invalid cases.

## 14. Explicitly out of scope

Do not implement: - Business workflow instances. - Consumer
state/history tables. - Dynamic execution of arbitrary user code. -
Remote calls to consumer services during Guard evaluation. - Automatic
state transitions. - Timer/scheduler transitions. - Event-to-transition
bindings. - Human task assignment/approval inbox. - Saga
orchestration. - A central Audit service. - Automatic deletion/retention
of published versions. - Distributed transactions across PostgreSQL and
Redis.

These may be considered later only through an explicit scope decision.
