# Retrospective — Proposed Changes

**Date:** 2026-05-30
**Scope:** Init phase retrospective — single incident (PM agent tagged [INIT] before setup verification)
**Phase:** Pre-Phase-1 (init)

---

## Summary

One proposal arising from the init-phase incident where the PM agent tagged [INIT] without the user having confirmed environment setup, because the human-gate condition stated by the Tech Lead was dropped when the orchestrating conversation composed the PM agent's invocation prompt.

---

## Proposals (ordered by estimated impact)

### 1. PM agent must not tag [INIT] before setup verification is confirmed

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

> Pattern: Chrome Extension Manifest V3 service worker token storage and refresh — ephemeral service worker lifecycle requires chrome.storage.local persistence and explicit re-authentication triggers rather than in-memory token state.
> Agent: tech-lead

**Assessment:** This pattern is already fully codified in the current system. It appears in:
- `prd.md` MOD-007 Purpose and AC-052, AC-053, AC-054 (acceptance criteria)
- `project-planning/status.md` Tech Lead Reviews — Shared Conventions (chrome.storage.local mandate)
- `~/.claude/skills/engineer-checklist/SKILL.md` judgment items (token storage handled via shared conventions check)

No new proposal needed — the pattern is covered. If the Tech Lead agent logged this recommendation for project-level skill capture (a new skill file covering the MV3 pattern generically, independent of this project), that would require a separate initiative scoped to `~/.claude/skills/` and is outside the scope of this retrospective. Logged here as **needs clarification** on whether the recommendation targets a new system-level skill or was informational only.
