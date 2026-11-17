---
date: 2026-11-17
status: Approved design baseline
title: Video Identity Verification --- Phase 7 Roadmap
version: 1.0
---

# Phase 7 --- Generic Workflow Engine

## 1. Purpose

Phase 7 creates a generic Workflow Definition and Publication service.
It owns workflow definitions, versions, validation, publication, and
distribution of immutable published snapshots.

It does **not** own business workflow instances, the current state of
business records, or transition history. AUTH, DMS, and future consumer
services own those records and their histories.

Workflow is an independent Spring Boot application and a sibling of AUTH
and DMS in the repository. It is not a Maven dependency of either
service.

## 2. Current status

  --------------------------------------------------------------------------
  Step              Work package      Status            Completion detail
  ----------------- ----------------- ----------------- --------------------
  7.1               Domain Design &   **Complete**      Domain boundaries,
                    Implementation                      aggregates, value
                                                        objects, versioning,
                                                        Guard DSL AST,
                                                        canonical JSON
                                                        serializer, and unit
                                                        tests implemented.

  7.2               Database Design   **Complete**      ERD, tables,
                    & Persistence                       constraints, indexes,
                                                        Flyway V2-V4
                                                        migrations applied,
                                                        JPA entities (Lombok),
                                                        repositories, and
                                                        integration tests
                                                        verified.

  7.3               Domain &          **In Progress**   Core domain models
                    Application                         and persistence
                    Implementation                      adapters/repositories
                                                        implemented; use cases,
                                                        ports, and mappers
                                                        in progress.

  7.4               Guard & Rule      **In Progress**   Guard AST sealed
                    Engine                              hierarchy, parser
                                                        (safety limits), and
                                                        deterministic evaluator
                                                        implemented with unit
                                                        tests; context schema
                                                        integration in progress.

  7.5               Definition        Not started       Implement
                    Publication                         Publication Job
                                                        orchestration,
                                                        worker claiming,
                                                        leases, retries, and
                                                        recovery.

  7.6               Redis Integration Not started       Store immutable
                                                        versioned snapshots
                                                        and atomically
                                                        maintain the
                                                        active-version
                                                        pointer.

  7.7               RabbitMQ & EDA    Not started       Implement
                                                        transactional outbox
                                                        publishing,
                                                        confirms, retries,
                                                        and event contracts.

  7.8               Admin Panel       Not started       Implement Workflow
                                                        administration UI
                                                        using Spring MVC and
                                                        Mustache.

  7.9               Consumer          Not started       Specify the
                    Integration                         read-only Redis
                    Contract                            contract and
                                                        consumer-side
                                                        runtime integration.

  7.10              Testing & Quality In Progress       Unit tests (domain,
                    Assurance                           guard DSL) and
                                                        database integration
                                                        tests active and passing.
  --------------------------------------------------------------------------

**Status distinction:** Steps 7.1 and 7.2 are fully implemented and verified with tests. Steps 7.3 and 7.4 have their domain structures and persistence foundations in place with unit/integration tests; application use cases, mappers, Redis integration, and RabbitMQ publishing are actively in progress according to this roadmap.

## 3. Approved architecture

### 3.1 Independent applications

-   `auth/`, `dms/`, and `workflow/` remain independent Spring Boot
    applications.
-   They are sibling directories for IDE/workspace convenience only.
-   Do not create sibling Maven dependencies, a shared runtime, or a
    shared database schema.
-   Workflow must not introduce compile-time dependencies on AUTH or
    DMS.
-   Each service owns its database schema and audit records.

### 3.2 Workflow ownership

Workflow owns: - Stable Workflow business keys and definitions. -
Numbered, immutable published versions. - State and Transition
definitions. - Guard expressions and per-version Context Schemas. -
Definition validation. - Publication jobs and publication lifecycle. -
Published snapshots in Redis. - Publication notification events through
RabbitMQ.

Workflow does **not** own: - Business workflow instances. - Current
state of a user, file, verification request, or other business record. -
Business transition history. - Consumer-specific authorization
decisions. - Consumer-specific Context values.

### 3.3 Runtime communication

-   Workflow is the only writer of Workflow Definition data in
    PostgreSQL and Workflow keys in Redis.
-   Consumer services perform **read-only Redis operations** for
    Workflow definitions.
-   Consumers do not call Workflow synchronously to decide a transition.
-   RabbitMQ distributes publication notifications. Consumers then read
    the corresponding snapshot from Redis.
-   Redis is the published-definition read store; RabbitMQ is
    notification transport.
-   Consumer runtime state and history remain local to the consumer
    service.

## 4. Approved decisions

  -------------------------------------------------------------------------------
  Concern                             Approved decision
  ----------------------------------- -------------------------------------------
  Application                         Independent Spring Boot application

  Java                                Java 25, matching current Workflow project

  Architecture                        DDD with Hexagonal / Ports-and-Adapters
                                      boundaries

  Relational DB                       PostgreSQL

  Local/test DB                       H2

  Migrations                          Flyway

  Definition read store               Redis

  Event distribution                  RabbitMQ with EDA

  State model                         FSM, one active state per business instance

  Versioning                          Immutable published versions

  Internal relational IDs             `BIGINT GENERATED BY DEFAULT AS IDENTITY`

  Distributed Event ID                UUID

  Workflow external identity          Stable `workflow_key` string

  Guard                               Restricted, nested JSON DSL

  Guard logic                         `ALL`, `ANY`, `NOT`

  Context                             JSON Schema Draft 2020-12 per Workflow
                                      Version

  Publication                         Asynchronous Publication Job

  Audit                               Existing independent
                                      `workflow.audit_events`; do not centralize

  Snapshot                            Canonical JSON, UTF-8, SHA-256 checksum

  Consumer access                     Read-only Redis
  -------------------------------------------------------------------------------

## 5. Phase 7 work packages

### Step 7.1 --- Domain Design

**Status: Design complete.**

Approved concepts: - `WorkflowDefinition` - `WorkflowVersion` -
`WorkflowState` - `WorkflowTransition` - `PublicationJob` -
`OutboxEvent`

Approved value objects: - `WorkflowKey` - `VersionNumber` - `StateKey` -
`TransitionKey` - `GuardExpression` - `ContextSchema` -
`WorkflowSnapshot` - `SnapshotChecksum` - `EventId`, `CorrelationId`,
`CausationId`

Approved invariants: - A Definition has a stable, unique
`workflow_key`. - A Definition can have many numbered versions. - A
Version is editable only while `DRAFT`. - A published Version is
immutable. - Before publication, each Version has exactly one `INITIAL`
State and at least one `FINAL` State. - Each Transition connects two
States belonging to the same Version. - Guard is an optional value
object owned by a Transition. - Context Schema belongs to a Version. -
Workflow does not persist business instances or consumer state history.

Guard decisions: - Nested AST with `ALL`, `ANY`, and `NOT`. - Leaf
operators: `EQ`, `NE`, `GT`, `GTE`, `LT`, `LTE`, `IN`, `NOT_IN`,
`EXISTS`, `NOT_EXISTS`. - No arbitrary code, scripts, SpEL, JavaScript,
SQL, or user-defined function execution. - Initial safety limits: depth
8, total nodes 100. - Deterministic and side-effect-free evaluation. -
No implicit type coercion.

Context decisions: - JSON Schema Draft 2020-12 per Version. - Context is
supplied by a consumer at evaluation time; Workflow does not persist
Context values. - Guard paths are checked against the Schema during
publication where statically possible.

Publication decisions: - Admin request creates a Publication Job and
changes the Version to `PUBLISH_PENDING`. - Previous active Version
remains active until the new snapshot is successfully activated in
Redis. - Publication must be idempotent and recoverable after process
failure.

### Step 7.2 --- Database Design

**Status: Design complete.**

Approved tables: - `workflow_definitions` - `workflow_versions` -
`workflow_states` - `workflow_transitions` -
`workflow_publication_jobs` - `workflow_outbox_events` - Existing
`workflow.audit_events` remains unchanged.

Storage decisions: - Internal IDs use BIGINT Identity. -
Event/correlation/causation IDs use UUID. - JSON documents use TEXT for
PostgreSQL/H2 portability and are validated in the application layer. -
PostgreSQL and H2 have matching Flyway migration sets. - Existing V1
audit migration is not edited or renumbered. - New migrations start at
V2.

Migration plan: - V2: definitions, versions, states, transitions,
constraints, and indexes. - V3: publication jobs and worker indexes. -
V4: outbox events and publisher indexes.

See `PHASE_7_STEP_2_DATABASE_DESIGN.md` for authoritative DDL.

### Step 7.3 --- Domain & Application Implementation

Implement: - Domain types and invariants from Step 7.1. - Application
use cases and ports. - Persistence adapters and mappings for the
approved schema. - Optimistic locking where specified. - Unit tests for
aggregate invariants and use cases.

Expected use cases: - Create Definition. - Update Definition metadata. -
Create Draft Version. - Add/update/remove States in a Draft. -
Add/update/remove Transitions in a Draft. - Validate a Draft. - Request
publication. - Read Definition and Version metadata. - Enable, disable,
or archive a Definition.

Do not implement business workflow instances in Workflow.

### Step 7.4 --- Guard & Rule Engine

Implement: - Versioned JSON Guard DSL. - AST parser and validator. -
Allowed node/operator/type validation. - Depth and node-count limits. -
Guard-path validation against Context Schema where possible. - Runtime
Context validation against the Version Schema. - Deterministic,
side-effect-free evaluation. - Structured evaluation results and safe
error codes.

Guard is not authorization. Spring Security and consumer-specific
authorization remain responsible for actor permissions.

### Step 7.5 --- Definition Publication

Implement: - Asynchronous Publication Job lifecycle. - Safe job claiming
across workers. - Worker leases and expired-lease recovery. - Canonical
Snapshot generation and SHA-256 checksum. - Idempotent Redis writes and
pointer activation. - PostgreSQL completion transaction and Outbox
insertion. - Crash-window reconciliation. - Controlled manual retry
after terminal failure.

### Step 7.6 --- Redis Integration

Implement: - Immutable versioned snapshots. - One active-version pointer
per Workflow key. - Atomic compare-and-set activation. - Consumer
read-only access. - No default TTL on published snapshots. - No
automatic deletion of old versions. - Fail-closed behavior for missing,
malformed, or checksum-mismatched snapshots.

Keys:

``` text
workflow:definitions:{WORKFLOW_KEY}:active
workflow:definitions:{WORKFLOW_KEY}:versions:{VERSION_NUMBER}
```

### Step 7.7 --- RabbitMQ & EDA

Implement: - Versioned publication event contract. - Outbox publisher. -
Publisher confirms and unroutable-message detection. - Retry/backoff and
Dead Letter strategy. - At-least-once delivery semantics; consumers must
be idempotent. - Correlation and W3C trace-context propagation.

RabbitMQ is not a substitute for Redis snapshot reads.

### Step 7.8 --- Admin Panel

Implement with Spring MVC and Mustache: - Workflow list/detail. -
Definition metadata management. - Version creation/editing. - State and
Transition management. - Guard editor and validation feedback. - Context
Schema editor/validation. - Publish request and Job status. -
Publication failure details and controlled retry. - Active and pending
version display.

All administrative routes must be secured.

### Step 7.9 --- Consumer Integration Contract

Define: - Redis read contract and key/value format. - Active-version
resolution and explicit version pinning. - Local transition-validation
steps. - Context validation and Guard evaluation contract. - Behavior
when Redis is unavailable or inconsistent. - Consumer-owned
state/history updates. - No cross-service database foreign keys.

### Step 7.10 --- Testing & Quality Assurance

Required coverage: - Domain unit tests. - Guard
parser/validator/evaluator tests. - JSON Schema compatibility tests. -
PostgreSQL and H2 Flyway tests. - JPA mapping/repository tests. -
Publication concurrency and lease recovery tests. - Redis
checksum/CAS/pointer tests. - Crash injection at publication
boundaries. - Outbox retry, publisher-confirm, duplicate-delivery, and
routing-failure tests. - Admin security tests. - End-to-end consumer
contract tests.

## 6. Non-negotiable instructions for coding agents

1.  Read repository `AGENTS.md` before changing code.
2.  Preserve the independent-applications architecture.
3.  Never add AUTH or DMS as Workflow Maven dependencies.
4.  Do not modify existing V1 audit migrations.
5.  Do not introduce workflow-instance or business-state-history tables
    in Workflow.
6.  Do not add Spring StateMachine or another FSM dependency unless
    explicitly approved.
7.  Never execute arbitrary expressions or user-supplied code in Guards.
8.  Consumer services must not write Workflow Redis keys.
9.  Do not use synchronous Workflow HTTP calls for runtime transition
    decisions.
10. Do not treat RabbitMQ events as the authoritative definition store.
11. Never mutate a published Version.
12. Do not silently change approved enums, table names, Redis keys, or
    event contracts.
13. Never log passwords, tokens, cookies, Context payloads, video data,
    or unnecessary personal data.
14. Keep Audit, Operational Logs, and Distributed Traces distinct.
15. Add negative-path, security, and recovery tests---not only
    happy-path tests.
16. Report exactly which commands/tests were run and which were not.

## 7. Design-stage completion

Steps 7.1 and 7.2 are complete as design work because: - Domain
boundaries and ownership are agreed. - Guard and Context approaches are
agreed. - Publication approach is agreed. - Relational tables and
relationships are agreed. - Internal ID strategy is agreed. - Migration
strategy is agreed. - Redis and Outbox responsibilities are
documented. - Remaining implementation work is explicitly listed.

The next implementation slice should be selected by the project owner.
Recommended sequence: implement the approved domain/persistence
foundation and V2--V4 migrations, then Guard and Publication.
