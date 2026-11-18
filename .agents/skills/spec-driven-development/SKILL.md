---
name: spec-driven-development
description: Drive spec.md -> plan.md -> tasks.md for a ticket-named feature
  (e.g. DBS-1789) under specs/<TICKET>-<slug>/, gate-checking each phase
  before advancing, and tracking tasks.md checkbox completion as
  implementation proceeds. Use when asked to spec out, plan, or scope a
  ticket/feature, to create or advance specs/plan/tasks for a branch, to
  check SDD status, or when starting work on a feat/<TICKET> branch that
  has no specs/<TICKET>-* folder yet.
---

# Spec-Driven Development

In this repo, non-trivial feature work is planned before it's coded, under
`specs/<TICKET>-<slug>/`, mirroring the `feat/<TICKET>` branch naming
convention already in use. The flow is phase-gated:

1. `spec.md` — what/why: requirements, user stories, acceptance criteria
2. `plan.md` — how: architecture, affected modules, data model, API contract
3. `tasks.md` — an ordered checklist implementing plan.md
4. Implementation — code changes, referencing the spec/plan/tasks

Templates for all three files are in `references/`.

## Determining current phase

1. Resolve the ticket ID: from the current git branch (`feat/<TICKET>...`)
   if on one, else ask the user.
2. Look for `specs/<TICKET>-*/`. If none exists → phase = spec (new).
3. If `spec.md` exists but no `plan.md` → phase = plan.
4. If `plan.md` exists but no `tasks.md` → phase = tasks.
5. If `tasks.md` exists → phase = implementation/tracking; report checkbox
   completion, list unchecked tasks.

Always state which phase/file you're operating on before making changes.

## Phase 1: spec.md

- Ask the user (if not already provided in the conversation): problem/
  motivation, in/out of scope, user stories, acceptance criteria, affected
  provider(s)/domain(s).
- Create `specs/<TICKET>-<slug>/spec.md` from `references/spec-template.md`.
- **Gate before allowing plan.md**: Summary, Scope, at least one Acceptance
  Criterion, and Affected Domains must be non-empty and non-placeholder. If
  not met, say what's missing and stop — do not silently create plan.md.

## Phase 2: plan.md

- Requires `spec.md` to exist and pass the phase-1 gate (re-check; don't
  trust a stale `Status:` field).
- Explore the codebase for affected modules before drafting — existing
  entities, state machines, REST resources, the tail of
  `src/main/resources/config/liquibase/master.xml` — the way a senior
  engineer would before writing a design doc.
- Ask the user to confirm/clarify architecture choices the spec leaves open.
- Create `plan.md` from `references/plan-template.md`, filling the
  Liquibase/entity, state machine, REST contract, and testing sections
  concretely — not left as bare headers.
- **Gate before allowing tasks.md**: Architecture, Affected Modules, and
  Testing Strategy sections are filled; Liquibase/State Machine/API
  sections are either filled or explicitly marked "N/A" (not silently
  blank).

## Phase 3: tasks.md

- Requires `plan.md` to exist and pass its gate.
- Derive an ordered checklist directly from `plan.md`'s sections (one task
  cites one plan.md section), using `references/tasks-template.md`'s
  grouping (Data Layer, Domain/Service, State Machine, REST/API, Tests,
  Verification, Follow-ups).
- **Gate**: every `plan.md` section with concrete content has at least one
  corresponding task; a Tests group and a Verification group both exist.

## Tracking implementation (repeat invocations)

- When re-invoked on a feature that already has `tasks.md`, report status
  (X/Y done) and, when asked to work the next task or after code changes
  are made, offer to check off the corresponding box(es) — show what's
  being checked off rather than silently editing.
- Do not mark a task done on the user's say-so alone if the described code
  doesn't appear to exist yet — verify by reading the relevant file(s)
  first.

## What this skill does NOT do

- It does not write production code itself as part of the spec/plan phases.
- It does not force a spec for trivial changes. See the root `CLAUDE.md`
  for when a spec is required vs optional. If invoked on a trivial change,
  say so and suggest skipping SDD rather than forcing it through the gates.
