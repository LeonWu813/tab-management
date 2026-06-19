# Proposal: Require explicit spring-boot-starter-actuator in all Spring Boot projects

## Evidence

Phase 11 (AWS backend deployment), Problem 1:

> `spring-boot-starter-actuator` was completely absent from pom.xml. The /actuator/health endpoint
> only existed via a transitive dependency from springdoc-openapi-starter-webmvc-ui. When
> management.* properties were added, the transitive path broke and the endpoint disappeared. Fix:
> Added explicit `spring-boot-starter-actuator` dependency to pom.xml. Secondary fix: When actuator
> was added, RedisHealthIndicator auto-registered and returned 503 if Redis was slow — fixed by
> setting management.health.redis.enabled=false and management.health.db.enabled=false.

This caused the ALB health check to return 401 (Problem 5), blocking the entire AWS deployment
until the root dependency gap was found. Both failures compounded before the root cause was
identified.

## Proposed Change

Add a new item to the Engineer checklist judgment items in
`~/.claude/skills/engineer-checklist/SKILL.md`:

```
- If the backend uses Spring Boot and exposes a health check endpoint (required by any load
  balancer, container orchestration system, or deployment pipeline): `spring-boot-starter-actuator`
  must appear as an explicit dependency in pom.xml — never rely on transitive inclusion. Also set
  `management.health.redis.enabled=false` and `management.health.db.enabled=false` unless the
  Redis and DB health indicators are explicitly required by spec, to prevent 503 responses when
  those services are slow to respond.
```

Also add a new failure pattern entry to
`~/.claude/skills/qa-checklist/references/common-failure-patterns.md`:

```
## N. Spring Boot Actuator health endpoint absent or returning 503 unexpectedly

If the backend exposes /actuator/health for a load balancer or deployment health check and that
endpoint returns 404 or 503, the most common causes are:

1. spring-boot-starter-actuator is absent from pom.xml and the endpoint was only reachable via
   a transitive dependency that was disrupted when other dependencies changed.
2. spring-boot-starter-actuator is present, but Redis or DB health indicators auto-registered
   and the external service is slow — causing the aggregate status to be DOWN (503) even though
   the application itself is running correctly.

Fix for (1): Add explicit spring-boot-starter-actuator to pom.xml.
Fix for (2): Set management.health.redis.enabled=false and management.health.db.enabled=false
in application.properties unless those health checks are required by spec.

Test approach: After starting the server, curl /actuator/health before registering it with any
load balancer. Confirm the response is HTTP 200 with {"status":"UP"}, not 404 or 503.
```

## Target Files

- `~/.claude/skills/engineer-checklist/SKILL.md` — add to judgment_items section
- `~/.claude/skills/qa-checklist/references/common-failure-patterns.md` — add new pattern

## Impact

Engineer agents on all future Spring Boot modules will check for explicit actuator dependency
before handoff. QA agents will know to test the health endpoint directly before declaring a
backend module PASS. Both apply to any module deployed behind a load balancer.

## Risk

Low. The checklist item is additive and scoped only to Spring Boot projects with health checks.
The QA failure pattern adds awareness without changing any verification workflow.
