# Proposal: Add pre-deployment check prohibiting TRACE/DEBUG logging levels committed to production config

## Evidence

Phase 11 (AWS backend deployment), Problem 7:

> logging.level.org.springframework.web.servlet.DispatcherServlet=TRACE was added for debugging.
> Generates massive CloudWatch log volume in production. Fix: Removed before final deployment.

This was caught manually before the final deployment commit, but it was present in the
application.properties file for the duration of Phase 11 debugging. In a production deployment
scenario it would have immediately generated excessive CloudWatch costs and log noise, obscuring
real errors. The fix required a deliberate human review before the final push.

## Proposed Change

Add a new judgment item to `~/.claude/skills/engineer-checklist/SKILL.md`:

```
- Before any production deployment commit: scan application.properties (and
  application-prod.properties if present) for any logging.level.* entries set to TRACE or
  DEBUG. TRACE-level logging on any Spring MVC, Hibernate, or security component generates
  extreme log volume in production, inflates cloud logging costs, and obscures real errors.
  Permitted production log levels: INFO and above. DEBUG is permitted for project-specific
  application code only, not for third-party frameworks. Remove any TRACE entries added during
  debugging before committing a production configuration.
```

Also add a new failure pattern to
`~/.claude/skills/qa-checklist/references/common-failure-patterns.md`:

```
## N. TRACE or DEBUG framework logging committed to production application.properties

If application.properties (or any production profile properties file) contains logging.level.*
entries set to TRACE or DEBUG for Spring framework packages (e.g.,
org.springframework.web.servlet.DispatcherServlet, org.hibernate.SQL,
org.springframework.security), the production server will generate excessive log output.

Symptoms in cloud deployments:
- CloudWatch, Stackdriver, or similar log store fills rapidly with framework-level request/response
  traces.
- Log costs increase significantly within hours of deployment.
- Real application errors become difficult to find amid framework debug output.

Check: Before verifying any deployment, grep the committed application.properties for TRACE and
DEBUG log levels: `grep -i "TRACE\|=DEBUG" backend/src/main/resources/application.properties`
Any match on a Spring/Hibernate/Security package is a failure.

Acceptable: logging.level.com.tabvault=DEBUG (project-specific package at DEBUG is acceptable
in production if it was intentionally set and reviewed).
Not acceptable: logging.level.org.springframework.*=TRACE (or DEBUG), logging.level.root=TRACE.
```

## Target Files

- `~/.claude/skills/engineer-checklist/SKILL.md` — add to judgment_items section
- `~/.claude/skills/qa-checklist/references/common-failure-patterns.md` — add new pattern

## Impact

Engineer agents will run a logging level check as part of the pre-handoff checklist before
any commit that includes properties file changes. QA agents will verify log levels as part
of deployment verification. Prevents recurring cost and observability issues in any cloud deployment.

## Risk

Low. The constraint (no TRACE/DEBUG on framework packages) is correct for any production deployment.
The check is simple and targeted.
