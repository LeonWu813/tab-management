# Retrospective — Proposed Changes

**Date:** 2026-05-30
**Scope:** Init phase retrospective — two incidents: (1) PM agent tagged [INIT] before setup verification; (2) Tech Lead init did not create .gitignore, leaving .env unprotected while the user filled it with real secrets
**Phase:** Pre-Phase-1 (init)

---

## Summary

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
