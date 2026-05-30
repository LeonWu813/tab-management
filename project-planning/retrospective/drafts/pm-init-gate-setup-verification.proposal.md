# Proposal: PM agent must not tag [INIT] before setup verification is confirmed

## Evidence

**Incident — 2026-05-30, init phase**

The Tech Lead agent issued a PASS WITH CONDITIONS verdict and explicitly stated in its output:

> "Before the PM tags [INIT], complete every step in setup.md and confirm setup is done. Then run the PM agent to incorporate feedback and tag [INIT]."

The correct three-step gate was:
1. Tech Lead issues PASS WITH CONDITIONS
2. User verifies local environment against setup.md (steps 1–12)
3. User confirms completion to PM → PM resolves Tech Lead conditions, checks setup gate, then tags [INIT] → Doc-Sync runs

What happened instead: the orchestrating Claude (main conversation) composed a prompt to the PM agent that included an instruction to "tag PM Updates with [INIT]" but did not forward the setup verification prerequisite. The PM agent tagged [INIT] immediately based solely on Tech Lead condition resolution, skipping the environment check. Doc-Sync then ran against an unverified environment.

This is recorded in `project-planning/status.md` PM Updates (2026-05-30 [INIT]) and confirmed by `project-planning/status.md` Build Config, which remains blank as of the same date — the build/lint/test commands that should have been elicited during setup confirmation were never collected.

**Why this matters**

`setup.md` step 10 (smoke check) and step 12 (TypeScript type generation) require a live backend. Engineering agents in Phase 1 depend on a verified environment to run their Step 0 infrastructure pre-flight check (defined in `~/.claude/skills/engineer-checklist/SKILL.md`). Tagging [INIT] before setup is verified means any Engineer agent that begins Phase 1 work against an unverified environment will either fail its pre-flight check or, worse, proceed against a misconfigured environment and produce undetectable failures.

**Root cause**

The human-in-the-loop gate (environment verification) is not expressed as an enforceable precondition inside the PM agent's skill. It exists only as a handoff instruction in the Tech Lead's natural-language output — which any intermediary orchestrator can drop when composing the next sub-agent prompt. Because the PM skill (`~/.claude/skills/prd-format/SKILL.md`) defines the [INIT] tag purely as a signal that the PRD is finalized (quality checklist passed + user approval), the setup gate is invisible to the PM agent unless it is explicitly forwarded in the invocation.

---

## Proposed Change

### Change 1 — Add a setup gate section to `prd-format/SKILL.md`

In the `<quick_start>` block, under "New PRD — Finalization", replace the current step 3:

**Current (step 3):**
```
3. Tag PM Updates entry `[INIT]` in `status.md` — this signals that the PRD is finalized and ready for Doc-Sync
```

**Proposed (step 3):**
```
3. Before tagging [INIT]: verify that the user has confirmed environment setup is complete.
   Ask the user directly: "Have you completed all steps in setup.md and confirmed the smoke check
   in Step 10 returns status UP for all components?"
   - If yes: proceed to step 4.
   - If no, or if setup.md does not yet exist: stop. Do not tag [INIT].
     Tell the user: "setup.md must be completed and confirmed before [INIT] can be tagged.
     Complete the setup steps and confirm before re-invoking the PM agent to finalize."
4. Tag PM Updates entry `[INIT]` in `status.md` — this signals that the PRD is finalized and
   ready for Doc-Sync.
```

This makes the gate an explicit named step in the PM's own workflow, rather than a condition that can only arrive via the invoking orchestrator's prompt.

### Change 2 — Add a setup confirmation marker to `setup.md`

At the very end of `project-planning/setup.md`, append:

```markdown
---

## Setup Confirmation

Once all steps above succeed (smoke check in Step 10 shows `"status": "UP"` for all components):

1. Tell the PM agent: "Setup is complete."
2. The PM agent will then verify this confirmation before tagging [INIT] in `status.md`.

**Do not confirm setup until Step 10 passes.** The PM will not proceed to [INIT] without this confirmation.
```

This gives the user a concrete, documented action to take — rather than requiring them to know that they need to relay a specific message to the PM.

### Change 3 — Add an orchestrator anti-pattern entry to `prd-format/references/anti-patterns.md`

Add a new entry titled **"Dropping human-gate conditions when composing sub-agent prompts"**:

```markdown
## Dropping human-gate conditions when composing sub-agent prompts

When an orchestrating agent (or conversation) composes an invocation prompt for a sub-agent,
it may summarize or simplify the preceding agent's handoff output — and in doing so silently
drop human-gate prerequisites that the preceding agent stated explicitly.

**Pattern to avoid:**
- Tech Lead says: "Before the PM tags [INIT], the user must confirm environment setup"
- Orchestrator composes PM prompt as: "Resolve Tech Lead conditions and tag [INIT]"
- Result: PM tags [INIT] without the setup gate being satisfied

**Why it matters:** Human-gate instructions exist precisely because the system cannot verify them
automatically. When an orchestrator drops them, the gate disappears entirely — the sub-agent
never knew it existed and cannot enforce something it was not told about.

**Correct pattern:** Every human-gate condition stated by one agent must be forwarded verbatim
to the user before the next agent is invoked. The user is the gate-keeper; the orchestrator is
not permitted to decide the gate has been met by implication.

**Enforcement:** The PM agent's prd-format skill now asks the user directly whether setup is
complete before tagging [INIT]. This converts the human-gate from a prompt-forwarding
requirement (easily dropped) into an explicit in-skill step (present regardless of how the
PM agent is invoked).
```

---

## Target Files

| Change | Target File | Nature |
|--------|-------------|--------|
| Change 1 | `~/.claude/skills/prd-format/SKILL.md` | Add setup-gate step to New PRD — Finalization quick_start section |
| Change 2 | `project-planning/setup.md` | Append Setup Confirmation section at end of file |
| Change 3 | `~/.claude/skills/prd-format/references/anti-patterns.md` | Add orchestrator gate-dropping anti-pattern entry |

---

## Impact

- **PM agent**: Will not tag [INIT] without explicitly asking the user whether setup is complete. This converts a prompt-forwarding dependency into an in-skill check.
- **Engineer agents**: Phase 1 Step 0 infrastructure pre-flight check will be run against a verified environment, not an assumed one, because [INIT] cannot be tagged until setup passes.
- **Doc-Sync agent**: Will only run after environment is confirmed, so the AMBIGUITY logged about blank Build Config (status.md Sync Report, 2026-05-30) will be addressed at the right time — the PM will have collected build/lint/test commands as part of the same finalization conversation.
- **Orchestrators / main conversation**: Change 1 removes the dependency on the orchestrator forwarding the setup gate. Even if the PM is invoked with a minimal prompt, the PM will surface the gate itself.

---

## Risk

- **False refusal if setup.md does not exist yet**: If a project is initializing before setup.md has been authored, the PM agent will correctly stop. This is the right behavior — setup.md must exist before [INIT] is tagged. No false negative risk here if setup.md is authored as part of the init flow.
- **User bypasses the gate**: The PM asks the user a yes/no question. A user can say "yes" without actually completing setup. This proposal cannot prevent deliberate bypass, only accidental bypass (the original failure mode). Explicit deception is out of scope.
- **Redundant confirmation on re-runs**: If the PM is re-invoked after [INIT] was already tagged (e.g. for a PRD update), the setup gate check applies only to the "New PRD — Finalization" path. Post-sync updates follow a different protocol branch (`post-sync updates`), so this gate does not apply there. No behavioral change for PM update runs.
