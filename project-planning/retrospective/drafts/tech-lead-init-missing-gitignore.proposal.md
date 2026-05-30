# Proposal: Tech Lead init review must create .gitignore before instructing user to fill .env

## Evidence

**Incident — 2026-05-30, init phase**

During project initialization the Tech Lead created `.env.example` and `docker-compose.yml` but did not
create a `.gitignore`. The user was then directed by `setup.md` Step 4 to run `cp .env.example .env`
and fill every variable with real secrets — API keys, a JWT secret, and VAPID private key.

At the point the user filled `.env`, no `.gitignore` existed. A single `git add .` would have committed
live credentials to the repository. The gap was only discovered after the fact when the user asked
"do we have a .gitignore?" The `.gitignore` was then created manually.

This is confirmed by the current git state (`.DS_Store` appears in `git status` as untracked, indicating
`.gitignore` did not exist at the time of those files being created) and by the Tech Lead Review recorded
in `project-planning/status.md` (2026-05-30 — init-infra):

> **`.gitignore` check**: Before running `cp .env.example .env`, verify that `.env` (without the
> `.example` suffix) is in `.gitignore`. The `.env.example` file is committed intentionally (it
> contains no secrets). The `.env` file must never be committed.

The Tech Lead recorded this as a Recommendation — an advisory — rather than generating the file itself.
This means the instruction was only visible in `status.md`; the user is directed from `setup.md`, not
`status.md`, and `setup.md` Step 4 tells the user to fill `.env` without the protective check being
enforced.

**Why this matters**

The `.gitignore` must exist before the user is told to copy `.env.example` to `.env`. The current init
sequence creates the problem (`.env.example` with real-secret scaffolding) and then instructs the user
to create the secret-bearing file (`.env`) without first creating the file that prevents it from being
committed. The only enforcement in the current flow is a one-line advisory in `setup.md` Step 4: "Verify
`.env` is listed in `.gitignore` before proceeding." — but that instruction assumes `.gitignore` already
exists, which it does not.

**Root cause**

The Tech Lead's write scope (defined in `~/.claude/agents/tech-lead.md`) lists three init-output files:
`.env.example`, `docker-compose.yml`, and `project-planning/setup.md`. `.gitignore` is not in the write
scope, not in the process steps, and not in the commit command in step 10. The Tech Lead therefore has
no instruction to create it.

---

## Proposed Change

### Change 1 — Add `.gitignore` to the Tech Lead init write scope

In `~/.claude/agents/tech-lead.md`, `<write_scope>` block, add a fourth entry:

**Current:**
```
- `.env.example` — created during init review only; lists all required env vars with placeholder values, never real secrets
- `docker-compose.yml` — created during init review only; defines all required infrastructure services
- `project-planning/setup.md` — created during init review only; step-by-step infrastructure runbook
```

**Proposed — add:**
```
- `.gitignore` — created during init review only; covers secrets (.env), build artifacts, IDE files, OS
  files, and upload directories. Derived from the PRD tech stack (Java/Maven adds target/, Node adds
  node_modules/dist/, Chrome Extension adds chrome-extension/dist/). Created before .env.example so
  the protection is in place before the user is ever instructed to create .env.
```

### Change 2 — Add `.gitignore` as a named step in the Tech Lead init process (step 9)

In `~/.claude/agents/tech-lead.md`, `<process>` step 9, extend the "three infrastructure files" list
to four files and add the `.gitignore` content specification:

**Current step 9 opening:**
```
9. **Init review only** — produce three infrastructure files:
   - **`.env.example`**: ...
   - **`docker-compose.yml`**: ...
   - **`project-planning/setup.md`**: ...
```

**Proposed step 9 opening:**
```
9. **Init review only** — produce four infrastructure files in this order:
   - **`.gitignore`**: create first, before .env.example, so that protection is in place before the
     user is instructed to create .env. At minimum include:
     - Secrets: `.env`, `*.env.local`, `*.env.*.local`
     - Java/Maven (if PRD tech stack includes Java): `target/`, `*.class`, `*.jar`, `*.war`, `*.ear`,
       `hs_err_pid*`
     - Node/npm (if PRD tech stack includes Node/React/Vite): `node_modules/`, `dist/`, `build/`,
       `.vite/`, `.cache/`, `npm-debug.log*`
     - Chrome Extension build output (if PRD includes a Chrome Extension): `chrome-extension/dist/`
     - PWA/frontend build output (if PRD includes a PWA or frontend): `pwa-dashboard/dist/`
     - Uploads directory: `uploads/`
     - IDE files: `.idea/`, `*.iml`, `.vscode/`, `*.swp`, `*.swo`
     - OS files: `.DS_Store`, `Thumbs.db`
     - Docker volumes: `postgres-data/`, `redis-data/`
     - Logs: `*.log`, `logs/`
     Derive the exact entries from the PRD tech stack. A Java+Node project needs both the Java and
     Node sections; a Node-only project omits the Java section.
   - **`.env.example`**: ...
   - **`docker-compose.yml`**: ...
   - **`project-planning/setup.md`**: ...
```

### Change 3 — Add `.gitignore` to the Tech Lead init commit command

In `~/.claude/agents/tech-lead.md`, `<process>` step 10 commit command, add `.gitignore`:

**Current:**
```bash
git add project-planning/status.md project-planning/setup.md .env.example docker-compose.yml
```

**Proposed:**
```bash
git add project-planning/status.md project-planning/setup.md .env.example docker-compose.yml .gitignore
```

### Change 4 — Update Tech Lead handoff message to mention .gitignore

In `~/.claude/agents/tech-lead.md`, `<process>` step 11 (the human-facing handoff message):

**Current:**
```
"Tech Lead review complete. Three infrastructure files created: `.env.example`, `docker-compose.yml`,
`project-planning/setup.md`."
```

**Proposed:**
```
"Tech Lead review complete. Four infrastructure files created: `.gitignore`, `.env.example`,
`docker-compose.yml`, `project-planning/setup.md`. `.env` is listed in `.gitignore` — you are
safe to create and fill `.env` without risk of committing secrets."
```

### Change 5 — Add a QA checklist item: .gitignore exists and covers .env before any backend module passes

In `~/.claude/skills/qa-checklist/SKILL.md`, `<core_checklist>` Integration section, add one item:

**Current Integration section (relevant item):**
```
- [ ] **Backend modules only**: `setup.md` is consistent with actual infrastructure config — ports in
  `docker-compose.yml` match `setup.md`, all env vars referenced in `setup.md` exist in `.env.example`
```

**Proposed — add after the existing backend-modules item:**
```
- [ ] **Backend modules only**: `.gitignore` exists at the project root and `.env` is listed in it —
  verify with `cat .gitignore | grep '^\.env$'` before marking any backend module PASS
```

**Rationale:** A backend module that passes all its ACs but is deployed from a repository where `.env`
is not gitignored is still in a dangerous configuration. This check costs one `grep` and prevents the
scenario from being declared clean.

---

## Target Files

| Change | Target File | Nature |
|--------|-------------|--------|
| Change 1 | `~/.claude/agents/tech-lead.md` | Add `.gitignore` to write scope |
| Change 2 | `~/.claude/agents/tech-lead.md` | Add `.gitignore` as first file in init process step 9 |
| Change 3 | `~/.claude/agents/tech-lead.md` | Add `.gitignore` to init commit command in step 10 |
| Change 4 | `~/.claude/agents/tech-lead.md` | Update handoff message in step 11 to name four files |
| Change 5 | `~/.claude/skills/qa-checklist/SKILL.md` | Add backend `.gitignore` existence check to Integration checklist |

Note: This project has no `ARCHITECTURE.md` file. If an `ARCHITECTURE.md` or equivalent Tech Lead
Init Flow diagram is created in future, it should also be updated to show `.gitignore` as the first
init-output file produced before `.env.example`.

---

## Impact

- **Tech Lead agent (init)**: Will produce `.gitignore` as the first infrastructure file, before
  `.env.example` and before the user is ever directed to create `.env`. The gap that allowed secrets
  to exist unprotected is closed at the source.
- **User**: `setup.md` Step 4 already contains "Verify `.env` is listed in `.gitignore` before
  proceeding." That instruction now has a file to verify against — it was previously a dead check
  because `.gitignore` did not exist.
- **QA agents (all backend modules)**: Will explicitly check that `.gitignore` exists and covers
  `.env` before issuing a PASS. This creates a backstop: even if `.gitignore` were missing, no
  backend module could pass QA without surfacing the gap.
- **Future projects using this Tech Lead skill**: Every project initialized with this agent will have
  a correctly scoped `.gitignore` committed before any secret-bearing files are created.

---

## Risk

- **Over-inclusion in .gitignore**: The proposed entries are conservative defaults derived from the
  PRD tech stack. The entries are additive — extra patterns in `.gitignore` only protect more, they
  do not break builds. Risk is negligible.
- **Tech stack derivation error**: If the Tech Lead misreads the PRD tech stack and omits a section
  (e.g., forgets the Chrome Extension `chrome-extension/dist/` entry for a project that includes one),
  the `.gitignore` is still present and covers `.env`. The worst case is an incomplete but functional
  `.gitignore`, not an absent one. The user or a later QA check can add the missing entry.
- **Ordering dependency in step 9**: Specifying that `.gitignore` must be created first requires the
  Tech Lead to write it before it writes `.env.example`. This is a trivially enforceable sequential
  constraint — no risk of circular dependency or ambiguity.
- **Already-initialized projects**: This change only affects future init runs. For this project, the
  `.gitignore` was created manually and committed. No retroactive action is needed.
