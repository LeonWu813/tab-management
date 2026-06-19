# Proposal: Require /actuator/health to be explicitly permitted in Spring Security for load balancer health checks

## Evidence

Phase 11 (AWS backend deployment), Problem 5:

> ALB health check hit /actuator/health and got 401. Fix: Added
> .requestMatchers("/actuator/health").permitAll() to SecurityConfig.

This was a direct consequence of the JWT filter being applied to all endpoints by default.
The ALB health checker does not send an Authorization header, so every health check returned
401, causing the ALB to mark all targets as unhealthy and route no traffic to the backend.
The fix is one line in SecurityConfig, but the incident blocked deployment verification until
it was identified.

This compounds with the actuator dependency issue (Problem 1, see separate proposal): the
actuator endpoint must exist AND be publicly accessible for ALB health checks to pass.

## Proposed Change

Add to the engineer-checklist judgment items in `~/.claude/skills/engineer-checklist/SKILL.md`:

```
- If the backend exposes /actuator/health for a load balancer or orchestration health check
  AND uses Spring Security: the health endpoint must be explicitly permitted without
  authentication. Add .requestMatchers("/actuator/health").permitAll() to the SecurityConfig
  http.authorizeHttpRequests chain. Without this, any JWT or session filter will return 401
  to the unauthenticated health checker, causing the load balancer to mark the instance as
  unhealthy regardless of whether the application is actually running.
```

## Target File

- `~/.claude/skills/engineer-checklist/SKILL.md` — add to judgment_items section

## Impact

Engineer agents implementing or modifying SecurityConfig in any Spring Boot project deployed
behind a load balancer will always include the health endpoint permit. Eliminates a recurring
deployment blocker that is invisible during local development (where a load balancer is not present).

## Risk

Low. The change is a single targeted judgment item. Exposing /actuator/health without
authentication is standard practice for load-balanced Spring Boot deployments and does not
create a meaningful security risk (the endpoint returns status UP/DOWN only, no sensitive data).
