**Revision**: 1 | **Created**: 2026-05-30

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
| Node.js | 20.x or 22.x LTS | `node --version` |
| npm | 10.x | `npm --version` |
| Docker Desktop | 24.x or later | `docker --version` |
| Docker Compose | v2.x (bundled with Docker Desktop) | `docker compose version` |

If any version is missing or outdated, install the correct version before continuing. Use a version manager (e.g., `nvm` for Node, `sdk` for Java) if you need multiple versions on the same machine.

---

### 2. API Keys and Credentials

Obtain the following before running any service. All values will be placed in the `.env` file in Step 4.

| Credential | How to Obtain |
|-----------|---------------|
| `ANTHROPIC_API_KEY` | Log in to https://console.anthropic.com → API Keys → Create Key. Request access to `claude-sonnet-4` model. |
| `YOUTUBE_API_KEY` | Go to https://console.cloud.google.com → Enable "YouTube Data API v3" → Credentials → Create API Key. |
| VAPID key pair (`VAPID_PUBLIC_KEY`, `VAPID_PRIVATE_KEY`) | Generate once per environment using the command in Step 3 below. Never reuse across environments. |
| `JWT_SECRET` | Generate a random 64-character hex string: `openssl rand -hex 64` |
| `DATABASE_URL` | Will be set to the local Docker PostgreSQL instance (see Step 5). |
| `REDIS_URL` | Will be set to the local Docker Redis instance (see Step 5). |

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

Copy the example environment file and fill every value.

```bash
cp .env.example .env
```

Open `.env` and fill in every variable. A complete `.env` must include at minimum:

```
# Application
NODE_ENV=development
PORT=8080

# Database
DATABASE_URL=jdbc:postgresql://localhost:5432/tabvault
DATABASE_USERNAME=tabvault
DATABASE_PASSWORD=tabvault_dev_password

# Redis
REDIS_URL=redis://localhost:6379

# JWT
JWT_SECRET=<64-character hex string from openssl rand -hex 64>
JWT_ACCESS_TOKEN_EXPIRY_MINUTES=15
JWT_REFRESH_TOKEN_EXPIRY_DAYS=7

# Claude API
ANTHROPIC_API_KEY=<your key from console.anthropic.com>
CLAUDE_MODEL=claude-sonnet-4

# YouTube Data API
YOUTUBE_API_KEY=<your key from Google Cloud Console>

# Web Push (VAPID)
VAPID_PUBLIC_KEY=<public key from npx web-push generate-vapid-keys>
VAPID_PRIVATE_KEY=<private key from npx web-push generate-vapid-keys>
VAPID_SUBJECT=mailto:wu.tsan@northeastern.edu

# File Upload (for any local file handling)
UPLOAD_DIR=./uploads
```

Do not commit `.env` to git. Verify `.env` is listed in `.gitignore` before proceeding.

---

## Step 5: Create Upload Directory

```bash
mkdir -p ./uploads
```

This directory is required at startup. If it does not exist, the application will fail to initialize.

---

## Step 6: Start Infrastructure Services

Start PostgreSQL and Redis using Docker Compose.

```bash
docker compose up -d
```

Verify both containers are running:

```bash
docker compose ps
```

Expected output: both `postgres` and `redis` services show `running` or `Up` status.

If either container fails to start, check logs:

```bash
docker compose logs postgres
docker compose logs redis
```

Wait 5–10 seconds after `docker compose up -d` before proceeding — PostgreSQL takes a moment to finish its initialization on first run.

---

## Step 7: Install Frontend Dependencies

Install dependencies for both frontend applications.

```bash
# PWA Dashboard
cd pwa-dashboard && npm install && cd ..

# Chrome Extension
cd chrome-extension && npm install && cd ..
```

If a `package.json` does not yet exist in a given directory, that frontend has not been scaffolded yet — skip that directory and return to this step when scaffolding is complete.

---

## Step 8: Run Database Migrations

Flyway migrations are run automatically on backend startup. However, you can trigger them manually to verify connectivity before starting the full application:

```bash
# From the backend root (Spring Boot project)
./mvnw flyway:migrate -Dflyway.url=jdbc:postgresql://localhost:5432/tabvault \
  -Dflyway.user=tabvault \
  -Dflyway.password=tabvault_dev_password
```

Expected output: `Successfully applied N migration(s)` with no errors.

If the command fails with a connection error, verify Docker Compose is running (Step 6) and that the credentials in your `.env` match the Docker Compose service configuration.

---

## Step 9: Start the Backend

```bash
# From the backend root (Spring Boot project)
./mvnw spring-boot:run
```

The application should start and log `Started TabVaultApplication` within 30 seconds. If startup fails, the most common causes are:

- PostgreSQL not reachable: verify Step 6 and Step 4 DATABASE_URL
- Redis not reachable: verify Step 6 and Step 4 REDIS_URL
- Missing environment variable: check the startup log for `Could not resolve placeholder`
- VAPID key format error: re-generate keys using Step 3

---

## Step 10: Smoke Check

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

## Step 11: Verify OpenAPI Spec

Confirm the OpenAPI spec endpoint is available (required for frontend type generation):

```bash
curl -s http://localhost:8080/v3/api-docs | python3 -m json.tool | head -20
```

Expected: a valid JSON OpenAPI document beginning with `"openapi": "3.0.x"`.

---

## Step 12: Generate Frontend TypeScript Types

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
| `Connection refused` on port 5432 | PostgreSQL container not running | `docker compose up -d postgres` |
| `Connection refused` on port 6379 | Redis container not running | `docker compose up -d redis` |
| `Could not resolve placeholder 'ANTHROPIC_API_KEY'` | Missing `.env` value | Fill the value in `.env` and restart |
| Flyway migration error: `relation already exists` | Migrations partially applied | Check `flyway_schema_history` table; repair if needed |
| VAPID key error at startup | Malformed key in `.env` | Re-run `npx web-push generate-vapid-keys` and update `.env` |
| `npm install` fails with ERESOLVE | Peer dependency conflict | Run `npm install --legacy-peer-deps` |

---

Once all steps succeed and the smoke check in Step 10 returns `"status": "UP"` for all components, confirm to the PM that setup is complete.
