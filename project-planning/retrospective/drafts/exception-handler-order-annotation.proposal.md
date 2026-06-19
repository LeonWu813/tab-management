# Proposal: Enforce @Order(Ordered.HIGHEST_PRECEDENCE) on all module-specific @RestControllerAdvice beans

## Evidence

Phase 11 (AWS backend deployment), Problem 2:

> AuthExceptionHandler had NO @Order annotation, which defaults to Ordered.LOWEST_PRECEDENCE —
> same as GlobalExceptionHandler. Spring resolved the tie unpredictably, picking
> GlobalExceptionHandler first. InvalidCredentialsException fell through to the catch-all and
> returned HTTP 500 instead of 401. Fix: Added @Order(Ordered.HIGHEST_PRECEDENCE) to
> AuthExceptionHandler. ItemExceptionHandler, ReminderExceptionHandler, and
> AutoCleanupExceptionHandler already had @Order(Ordered.HIGHEST_PRECEDENCE) correctly.

This exact pattern was also logged as a Skill Recommendation in project-planning/status.md after
the MOD-002 incident (BUG-003: ItemNotFoundException, CategoryNotFoundException, and
BatchRateLimitExceededException all returned HTTP 500 because ItemExceptionHandler was missing
@Order). The Phase 11 incident confirms the pattern recurred for AuthExceptionHandler — a second
module's exception handler was missing the annotation.

The Skill Recommendation from status.md (agent: engineer-mod-item-management) reads:
> Pattern: Multiple @RestControllerAdvice beans require explicit @Order — when GlobalExceptionHandler
> uses @ExceptionHandler(Exception.class) and a module-specific handler defines specific exception
> handlers, Spring may route to the catch-all first if no @Order is set. Fix: annotate the specific
> handler with @Order(Ordered.HIGHEST_PRECEDENCE) and the catch-all with @Order(Ordered.LOWEST_PRECEDENCE).

The Phase 11 incident confirms this is a project-wide recurring failure, not a one-time event.

## Proposed Change

1. Add a new judgment item to `~/.claude/skills/engineer-checklist/SKILL.md`:

```
- If the module defines a @RestControllerAdvice exception handler class: it must be annotated
  with @Order(Ordered.HIGHEST_PRECEDENCE). Any project-wide catch-all @RestControllerAdvice
  (e.g., GlobalExceptionHandler with @ExceptionHandler(Exception.class)) must be annotated
  with @Order(Ordered.LOWEST_PRECEDENCE). Omitting @Order causes Spring to resolve ties
  non-deterministically, often routing specific exceptions to the catch-all and returning HTTP 500.
```

2. Add a new failure pattern entry to
`~/.claude/skills/qa-checklist/references/common-failure-patterns.md`:

```
## N. Spring @RestControllerAdvice tie-breaking: specific exceptions returning HTTP 500

When multiple @RestControllerAdvice beans exist (typically one global catch-all and several
module-specific handlers), Spring may route exceptions to the wrong handler if @Order is not set.
The most common symptom: a well-typed exception (e.g., InvalidCredentialsException,
NotFoundException) returns HTTP 500 instead of the expected status code (401, 404, 429, etc.)
even though a correct @ExceptionHandler for that type exists.

Root cause: all @RestControllerAdvice beans without an explicit @Order annotation default to
Ordered.LOWEST_PRECEDENCE. When two beans share the same precedence and one is a catch-all
(@ExceptionHandler(Exception.class)), Spring's tie-breaker is non-deterministic — the catch-all
often wins.

Fix: Annotate every module-specific exception handler with @Order(Ordered.HIGHEST_PRECEDENCE).
Annotate the global catch-all with @Order(Ordered.LOWEST_PRECEDENCE).

Test approach: For every exception type that should return a non-500 status code, trigger it
against a running server and verify the exact HTTP status. Unit tests with @WebMvcTest slices
may not reproduce this — full Spring context wiring (spring-boot:run or integration test with
@SpringBootTest) is required to see the bean ordering resolution.
```

## Target Files

- `~/.claude/skills/engineer-checklist/SKILL.md` — add to judgment_items section
- `~/.claude/skills/qa-checklist/references/common-failure-patterns.md` — add new pattern

## Impact

Engineer agents adding any new @RestControllerAdvice handler will be prompted to add @Order.
QA agents will check status codes against known exception types rather than only verifying
happy-path responses. Affects all Spring Boot modules in any future project using multiple
advice beans.

## Risk

Low. The checklist item is targeted at a specific Spring annotation, not a general policy change.
The QA pattern is additive. No existing behavior changes.
