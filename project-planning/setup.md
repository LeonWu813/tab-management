**Revision**: 2 | **Updated**: 2026-05-30

---

# TabVault — Infrastructure Setup Runbook

This runbook covers all steps required to bring a fresh development or staging environment to a running, smoke-checked state. Complete every step in order. Do not skip steps — later steps depend on earlier ones.

---

## Prerequisites

### 1. Runtime Versions

Verify the following versions are installed before proceeding.

| Tool | Required Version | Check Command |
|------|-----------------|---------------|
| Java (JDK) | 21 (LTS) | `java -version` |
| Node.js | 24.x LTS | `node --version` |
| npm | 11.x | `npm --version` |
| Docker Desktop | 24.x or later | `docker --version` |
| Docker Compose | v2.x (bundled with Docker Desktop) | `docker compose version` |

If any version is missing or outdated, install the correct version before continuing. Use a version manager (e.g., `nvm` for Node, `sdk` for Java) if you need multiple versions on the same machine.

> **Note (recorded 2026-05-30):** The development machine has Java 25 (non-LTS) and Docker Compose v5. Node 24 LTS and npm 11 are the current required versions and match the installed environment. Java 25 is a non-LTS release; see the Tech Lead Review in `status.md` (2026-05-30 — init-infra) for details on the Java version risk. You may proceed, but consider installing Java 21 LTS to match the stated requirement.

---

### 2. API Keys and Credentials

Obtain the following before running any service. All values will be placed in the `.env` file in Step 4.

| Credential | How to Obtain |
|-----------|---------------|
| `ANTHROPIC_API_KEY` | Log in to https://console.anthropic.com → API Keys → Create Key. Request access to `claude-sonnet-4` model. |
| `YOUTUBE_API_KEY` | Go to https://console.cloud.google.com → Enable "YouTube Data API v3" → Credentials → Create API Key. |
| VAPID key pair (`VAPID_PUBLIC_KEY`, `VAPID_PRIVATE_KEY`) | Generate once per environment using the command in Step 3 below. Never reuse across environments. |
| `JWT_SECRET` | Generate a random 64-character hex string: `openssl rand -hex 64` |
| `DATABASE_URL` | Will be set to the local Docker PostgreSQL instance (see Step 6). |
| `REDIS_URL` | Will be set to the local Docker Redis instance (see Step 6). |

---

### 3. Generate VAPID Keys

Run this once per environment. Copy the output into the `.env` file in Step 4.

```bash
# Requires Node.js installed
npx web-push generate-vapid-keys
```

The command outputs a public key and a private key. Save both — the private key cannot be recovered after this point.

---

## Step 4: Environment File

An `.env.example` file is provided at the project root with every required variable, placeholder values, and inline comments explaining how to obtain each value.

Copy it and fill every variable:

```bash
cp .env.example .env
```

Open `.env` in a text editor and replace every placeholder. Required variables by phase:

**Required for all phases (Phase 1+):**
- `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` — must match `docker-compose.yml`
- `REDIS_URL`
- `JWT_SECRET` — generate with `openssl rand -hex 64`
- `JWT_ACCESS_TOKEN_EXPIRY_MINUTES`, `JWT_REFRESH_TOKEN_EXPIRY_DAYS`

**Required for Phase 2+ (LLM integration):**
- `ANTHROPIC_API_KEY`
- `CLAUDE_MODEL`
- `VAPID_PUBLIC_KEY`, `VAPID_PRIVATE_KEY`, `VAPID_SUBJECT` — generate with `npx web-push generate-vapid-keys`

**Required for Phase 3+ (YouTube extraction):**
- `YOUTUBE_API_KEY`

Do not commit `.env` to git. Verify `.env` is listed in `.gitignore` before proceeding.

---

## Step 5: Create Upload Directory

```bash
mkdir -p ./uploads
```

This directory is required at startup. If it does not exist, the application will fail to initialize.

---

## Step 6: Start Infrastructure Services

Start PostgreSQL 16 and Redis 7.2 using Docker Compose.

```bash
docker compose up -d
```

Wait 10–15 seconds after this command — PostgreSQL takes a moment to complete its first-run initialization.

---

## Step 7: Verify Infrastructure Services

Confirm both containers are running and healthy:

```bash
docker compose ps
```

Expected output: both `tabvault-postgres` and `tabvault-redis` services show `running` or `Up` status. The `STATUS` column should show `healthy` once the healthchecks pass (allow up to 30 seconds after `up -d`).

If either container fails to start or shows `unhealthy`, check the logs:

```bash
docker compose logs postgres
docker compose logs redis
```

Common causes of PostgreSQL failure:
- Port 5432 already in use by a local PostgreSQL installation — stop the local service or change the host port in `docker-compose.yml`.

Common causes of Redis failure:
- Port 6379 already in use — stop the conflicting process or change the host port in `docker-compose.yml`.

Do not proceed past this step until both services show `healthy` or `Up`.

---

## Step 8: Install Frontend Dependencies

Install dependencies for both frontend applications.

```bash
# PWA Dashboard
cd pwa-dashboard && npm install && cd ..

# Chrome Extension
cd chrome-extension && npm install && cd ..
```

If a `package.json` does not yet exist in a given directory, that frontend has not been scaffolded yet — skip that directory and return to this step when scaffolding is complete.

---

## Step 9: Run Database Migrations

Flyway migrations are run automatically on backend startup. However, you can trigger them manually to verify connectivity before starting the full application:

```bash
# From the backend root (Spring Boot project)
./mvnw flyway:migrate -Dflyway.url=jdbc:postgresql://localhost:5432/tabvault \
  -Dflyway.user=tabvault \
  -Dflyway.password=tabvault_dev_password
```

Expected output: `Successfully applied N migration(s)` with no errors.

If the command fails with a connection error, verify Docker Compose is running (Step 6–7) and that the credentials in your `.env` match the Docker Compose service configuration.

---

## Step 10: Start the Backend

```bash
# From the backend root (Spring Boot project)
./mvnw spring-boot:run
```

The application should start and log `Started TabVaultApplication` within 30 seconds. If startup fails, the most common causes are:

- PostgreSQL not reachable: verify Steps 6–7 and `DATABASE_URL` in `.env`
- Redis not reachable: verify Steps 6–7 and `REDIS_URL` in `.env`
- Missing environment variable: check the startup log for `Could not resolve placeholder`
- VAPID key format error: re-generate keys using Step 3
- `VAPID_PUBLIC_KEY` or `VAPID_PRIVATE_KEY` absent: backend is configured to fail on startup if either is missing (AC-061)

---

## Step 11: Smoke Check

Once the backend is running, verify the health endpoint responds correctly.

```bash
curl -s http://localhost:8080/actuator/health | python3 -m json.tool
```

Expected response:

```json
{
  "status": "UP",
  "components": {
    "db": { "status": "UP" },
    "redis": { "status": "UP" }
  }
}
```

All components must show `"status": "UP"`. If `db` or `redis` shows `DOWN`, the application cannot serve requests correctly — resolve the infrastructure issue before proceeding.

If the `/actuator/health` endpoint is not yet configured, use:

```bash
curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/health
```

A `200` response confirms the backend is running.

---

## Step 12: Verify OpenAPI Spec

Confirm the OpenAPI spec endpoint is available (required for frontend type generation):

```bash
curl -s http://localhost:8080/v3/api-docs | python3 -m json.tool | head -20
```

Expected: a valid JSON OpenAPI document beginning with `"openapi": "3.0.x"`.

---

## Step 13: Generate Frontend TypeScript Types

Once the backend is running and the OpenAPI spec is available:

```bash
# From the pwa-dashboard directory
npx openapi-typescript http://localhost:8080/v3/api-docs -o src/types/api.d.ts
```

Repeat for the Chrome extension frontend if it shares the generated types.

---

## Troubleshooting Quick Reference

| Symptom | Likely Cause | Fix |
|---------|-------------|-----|
| `Connection refused` on port 5432 | PostgreSQL container not running or port conflict | `docker compose up -d postgres`; check if local PostgreSQL is already on 5432 |
| `Connection refused` on port 6379 | Redis container not running or port conflict | `docker compose up -d redis`; check if another Redis is already on 6379 |
| `docker compose ps` shows `unhealthy` | Container started but healthcheck failing | Wait 30 s; run `docker compose logs postgres` or `docker compose logs redis` |
| `Could not resolve placeholder 'ANTHROPIC_API_KEY'` | Missing `.env` value | Fill the value in `.env` and restart |
| Flyway migration error: `relation already exists` | Migrations partially applied | Check `flyway_schema_history` table; repair if needed |
| VAPID key error at startup | Malformed key in `.env` | Re-run `npx web-push generate-vapid-keys` and update `.env` |
| `npm install` fails with ERESOLVE | Peer dependency conflict | Run `npm install --legacy-peer-deps` |
| Java version mismatch with Spring Boot 3.3 | Java 25 (non-LTS) in use | Install Java 21 LTS via `sdk install java 21-tem` |

---

Once all steps succeed and the smoke check in Step 11 returns `"status": "UP"` for all components, confirm to the Tech Lead.

---

## Setup Confirmation

Once all steps above succeed (smoke check in Step 11 shows `"status": "UP"` for all components):

1. Re-invoke the **Tech Lead agent** with the message: **"Setup is complete."**
2. The Tech Lead will record confirmation in `status.md` and give you the green light to invoke the PM agent.
3. Only after the Tech Lead records confirmation should you invoke PM to tag `[INIT]`.

**Do not invoke the PM agent until the Tech Lead has recorded setup confirmation in `status.md`.**
