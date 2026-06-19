# Retrospective — Proposed Changes

**Date:** 2026-06-19
**Scope:** Phase 11 retrospective — AWS backend deployment (ECS Fargate, ALB, HTTPS, ECR)
**Phase:** Phase 11

---

## Summary

Seven proposals arising from Phase 11 deployment incidents. Ordered by estimated impact.
Problems 1, 2, 5 (actuator dependency, exception handler ordering, security permitAll) were
the most disruptive because they blocked the deployment entirely or caused silent misrouting
of live traffic. Problems 3, 4, 7 (Docker platform, CORS wildcards, TRACE logging) are
smaller in blast radius but equally predictable on future projects. Problem 6 (CloudShell-first
debugging) is an operational insight with high time-saving value.

---

## Proposals (ordered by estimated impact)

### 1. Require explicit spring-boot-starter-actuator and suppress noisy health indicators

| Field | Value |
|-------|-------|
| **Draft file** | `project-planning/retrospective/drafts/actuator-explicit-dependency.proposal.md` |
| **Primary target** | `~/.claude/skills/engineer-checklist/SKILL.md` — add to judgment_items |
| **Secondary target** | `~/.claude/skills/qa-checklist/references/common-failure-patterns.md` — add new pattern |
| **Rationale** | spring-boot-starter-actuator was absent from pom.xml in Phase 11; the /actuator/health endpoint was only reachable via a transitive dependency that broke when management.* properties were added. This blocked ALB health checks and blocked deployment. The fix also revealed that Redis and DB health indicators auto-register and return 503 on slow responses — requiring explicit disabling. Explicit actuator dependency + suppressed noisy indicators is the correct baseline for any load-balanced Spring Boot deployment. |
| **Agents benefiting** | All engineer agents on Spring Boot modules, all QA agents verifying backend deployments |

---

### 2. Enforce @Order(Ordered.HIGHEST_PRECEDENCE) on all module-specific @RestControllerAdvice beans

| Field | Value |
|-------|-------|
| **Draft file** | `project-planning/retrospective/drafts/exception-handler-order-annotation.proposal.md` |
| **Primary target** | `~/.claude/skills/engineer-checklist/SKILL.md` — add to judgment_items |
| **Secondary target** | `~/.claude/skills/qa-checklist/references/common-failure-patterns.md` — add new pattern |
| **Rationale** | AuthExceptionHandler was missing @Order in Phase 11, causing InvalidCredentialsException to return HTTP 500 instead of 401. This is the second occurrence of this pattern in this project (MOD-002 BUG-003 was the first, already logged as a Skill Recommendation in status.md). The Skill Recommendation was never codified into the checklist. Phase 11 confirms this is a recurring cross-module pattern that will affect any future project with multiple @RestControllerAdvice beans and a global catch-all. |
| **Agents benefiting** | All engineer agents implementing exception handlers, all QA agents verifying status code correctness |

---

### 3. Require /actuator/health to be explicitly permitted in Spring Security for load balancer health checks

| Field | Value |
|-------|-------|
| **Draft file** | `project-planning/retrospective/drafts/actuator-security-permitall.proposal.md` |
| **Primary target** | `~/.claude/skills/engineer-checklist/SKILL.md` — add to judgment_items |
| **Rationale** | ALB health checks hit /actuator/health and received 401 because the JWT filter applied to all endpoints by default. This caused all ALB targets to be marked unhealthy and blocked all traffic. The fix is a single line in SecurityConfig but requires knowing the pattern. The issue is invisible in local development (no ALB present). |
| **Agents benefiting** | All engineer agents implementing SecurityConfig in load-balanced Spring Boot projects |

---

### 4. Mandate --platform linux/amd64 for Docker builds targeting cloud deployment on Apple Silicon

| Field | Value |
|-------|-------|
| **Draft file** | `project-planning/retrospective/drafts/docker-build-platform-flag.proposal.md` |
| **Primary target** | `~/.claude/skills/engineer-checklist/SKILL.md` — add to judgment_items |
| **Secondary target** | `~/.claude/skills/qa-checklist/references/common-failure-patterns.md` — add new pattern |
| **Rationale** | Plain `docker build` on Apple Silicon produced an arm64 image. ECS Fargate runs x86_64. The image built and pushed without error but failed silently at runtime. The failure is only visible in ECS task logs, not locally. Any project with a Dockerfile targeting cloud deployment from an Apple Silicon machine will hit this. |
| **Agents benefiting** | Engineer agents producing Dockerfiles or deployment instructions, QA agents verifying containerized deployments |

---

### 5. Add CloudShell-first rule for debugging HTTPS/TLS connectivity failures in AWS

| Field | Value |
|-------|-------|
| **Draft file** | `project-planning/retrospective/drafts/https-debugging-cloudshell-first.proposal.md` |
| **Primary target** | `~/.claude/skills/qa-checklist/references/common-failure-patterns.md` — add new pattern |
| **Rationale** | Phase 11 spent significant debugging time investigating AWS infrastructure when the root cause was ISP-level SNI-based DPI blocking a newly registered domain. A single AWS CloudShell test at the outset would have immediately confirmed the infrastructure was correct. The pattern also documents two concrete ALB TLS best practices: avoid post-quantum security policies (ELBSecurityPolicy-*-PQ-*) for client compatibility, and do not duplicate a certificate in both the ALB default slot and the SNI list. |
| **Agents benefiting** | QA agents and engineers performing deployment verification against AWS-hosted backends |

---

### 6. Add pre-deployment check prohibiting TRACE/DEBUG framework logging committed to production config

| Field | Value |
|-------|-------|
| **Draft file** | `project-planning/retrospective/drafts/no-trace-logging-in-production.proposal.md` |
| **Primary target** | `~/.claude/skills/engineer-checklist/SKILL.md` — add to judgment_items |
| **Secondary target** | `~/.claude/skills/qa-checklist/references/common-failure-patterns.md` — add new pattern |
| **Rationale** | logging.level.org.springframework.web.servlet.DispatcherServlet=TRACE was left in application.properties from debugging and committed for the duration of Phase 11. It was caught manually before the final deployment. A checklist check prevents this class of error from requiring manual memory during pre-deployment review. |
| **Agents benefiting** | All engineer agents modifying application.properties, all QA agents verifying backend deployments |

---

### 7. Use setAllowedOriginPatterns() not setAllowedOrigins() when CORS origins include wildcards

| Field | Value |
|-------|-------|
| **Draft file** | `project-planning/retrospective/drafts/cors-allowed-origin-patterns.proposal.md` |
| **Primary target** | `~/.claude/skills/qa-checklist/references/common-failure-patterns.md` — add new pattern |
| **Secondary target** | `~/.claude/skills/engineer-checklist/SKILL.md` — add to judgment_items |
| **Rationale** | setAllowedOrigins() threw an exception for chrome-extension://* in Phase 11. This is a non-obvious Spring API distinction. Any project that includes a Chrome Extension client (a stated requirement for this project) will need wildcard CORS origin support and will hit this if setAllowedOrigins() is used. |
| **Agents benefiting** | Engineer agents implementing SecurityConfig for projects with browser extension clients |

---

---

# Previous Retrospective Entries

---

**Date:** 2026-05-30
**Scope:** Init phase retrospective — two incidents: (1) PM agent tagged [INIT] before setup verification; (2) Tech Lead init did not create .gitignore, leaving .env unprotected while the user filled it with real secrets
**Phase:** Pre-Phase-1 (init)

---

## Summary (init)

Two proposals arising from init-phase incidents. Ordered by estimated impact — the `.gitignore` gap
is ranked first because it created a direct secrets-exposure risk; the PM init gate is ranked second
because it caused an unverified environment to be declared ready but did not expose secrets.

---

## Proposals (ordered by estimated impact)

### 1. Tech Lead init review must create .gitignore before instructing user to fill .env

| Field | Value |
|-------|-------|
| **Draft file** | `project-planning/retrospective/drafts/tech-lead-init-missing-gitignore.proposal.md` |
| **Primary target** | `~/.claude/agents/tech-lead.md` — write scope, init process step 9 (add .gitignore as first file), step 10 commit command, step 11 handoff message |
| **Secondary target** | `~/.claude/skills/qa-checklist/SKILL.md` — add backend .gitignore existence check to Integration checklist |
| **Rationale** | No agent in the init flow created .gitignore. The user filled .env with live secrets (API keys, JWT secret, VAPID private key) before .gitignore existed — one accidental `git add .` away from committing credentials. The Tech Lead's write scope and step 9 must be updated to produce .gitignore as the first init-output file, before .env.example, so protection is in place before the user is ever directed to create .env. A QA checklist backstop ensures no backend module can pass without verifying .gitignore covers .env. |
| **Agents benefiting** | Tech Lead (gains explicit .gitignore creation responsibility), QA all backend modules (gains .gitignore existence check), users of all future projects initialized with this agent |

---

### 2. PM agent must not tag [INIT] before setup verification is confirmed

| Field | Value |
|-------|-------|
| **Draft file** | `project-planning/retrospective/drafts/pm-init-gate-setup-verification.proposal.md` |
| **Primary target** | `~/.claude/skills/prd-format/SKILL.md` — New PRD — Finalization quick_start section |
| **Secondary targets** | `project-planning/setup.md` (append confirmation section); `~/.claude/skills/prd-format/references/anti-patterns.md` (add orchestrator gate-dropping pattern) |
| **Rationale** | The PM tagged [INIT] immediately on Tech Lead condition resolution because the setup gate was not a named step in the PM skill — it existed only as a handoff instruction in the Tech Lead's output, which the orchestrator dropped when composing the PM prompt. Converting the gate into an explicit in-skill step makes it enforced regardless of how the PM is invoked. |
| **Agents benefiting** | PM (gains explicit gate), Engineer Phase 1 (pre-flight check runs against verified env), Doc-Sync (runs after build config is confirmed) |

---

## Skill Recommendations from status.md (processed)

The following entry appears in `project-planning/status.md` under `## Skill Recommendations`:

> Pattern: Chrome Extension Manifest V3 service worker token storage and refresh — ephemeral service
> worker lifecycle requires chrome.storage.local persistence and explicit re-authentication triggers
> rather than in-memory token state.
> Agent: tech-lead

**Assessment:** This pattern is already fully codified in the current system. It appears in:
- `prd.md` MOD-007 Purpose and AC-052, AC-053, AC-054 (acceptance criteria)
- `project-planning/status.md` Tech Lead Reviews — Shared Conventions (chrome.storage.local mandate)
- `~/.claude/skills/engineer-checklist/SKILL.md` judgment items (token storage handled via shared conventions check)

No new proposal needed — the pattern is covered. If the Tech Lead agent logged this recommendation for
project-level skill capture (a new skill file covering the MV3 pattern generically, independent of this
project), that would require a separate initiative scoped to `~/.claude/skills/` and is outside the
scope of this retrospective. Logged here as **needs clarification** on whether the recommendation
targets a new system-level skill or was informational only.
