# TabVault

A full-stack tab management system for saving, organizing, and revisiting browser tabs across devices. Built with a Chrome Extension (MV3), a React PWA Dashboard, and a Spring Boot backend.

**Live Demo:** [https://tab-vault.com](https://tab-vault.com)

---

## Tech Stack

### Backend
| Layer | Technology |
|---|---|
| Runtime | Java 21 |
| Framework | Spring Boot 3.3.5 |
| Database | PostgreSQL |
| ORM | Spring Data JPA + Hibernate 6 |
| Migrations | Flyway |
| Auth | Spring Security 6 + JWT (JJWT 0.12) |
| Cache | Redis (ElastiCache) |
| Scheduling | Quartz (JDBC-backed) |
| Async HTTP | Spring WebFlux (WebClient) |
| Content Extraction | Jsoup · Readability4j · Apache PDFBox |
| AI Analysis | Anthropic Claude API (claude-sonnet-4-6) |
| Push Notifications | Web Push (VAPID) via `web-push` 5.1.1 |
| API Docs | springdoc-openapi (Swagger UI) |
| Health Checks | Spring Boot Actuator |

### PWA Dashboard
| Layer | Technology |
|---|---|
| Framework | React 18 + TypeScript |
| Server State | TanStack Query v5 |
| Client State | Zustand |
| Routing | React Router v6 |
| Styling | Tailwind CSS v3 |
| Icons | Google Material Symbols Outlined |
| Offline / PWA | Vite Plugin PWA + Workbox |
| Build Tool | Vite 5 |

### Chrome Extension
| Layer | Technology |
|---|---|
| Manifest | MV3 |
| Framework | React 18 + TypeScript |
| Background | Service Worker (MV3) |
| Build Tool | Vite 5 |

### Infrastructure
| Layer | Technology |
|---|---|
| Compute | AWS ECS Fargate |
| Container Registry | Amazon ECR |
| Load Balancer | AWS ALB + ACM (SSL termination, health checks) |
| Database | AWS RDS PostgreSQL |
| Cache | AWS ElastiCache Redis |
| Frontend Hosting | AWS S3 + CloudFront |
| TLS Certificates | AWS ACM (auto-renewed) |
| DNS | Squarespace |

---

## Architecture

```
Chrome Extension (MV3)
  └── VITE_API_BASE_URL=https://api.tab-vault.com → ALB → ECS Fargate (Spring Boot)

PWA Dashboard
  └── https://tab-vault.com
        └── CloudFront (CDN + HTTPS) → S3 (static files)
              └── /api/* proxied to → ALB → ECS Fargate (Spring Boot)

Backend API
  └── https://api.tab-vault.com → ALB → ECS Fargate
        ├── RDS PostgreSQL  (persistent storage)
        ├── ElastiCache Redis  (batch rate limiting, session cache)
        └── Claude API  (content analysis, summary, reminders)
```

```
Manual deploy pipeline:
  Local machine
    ├── docker build --platform linux/amd64 → push to ECR
    │     └── aws ecs update-service --force-new-deployment
    └── VITE_API_BASE_URL=... npm run build → aws s3 sync → CloudFront invalidation
```

---

## Domain Model

```
User
└── Item  (LINK | VIDEO | NOTE)
    ├── Category  (optional)
    ├── ContentAnalysisJob  (async Claude analysis pipeline)
    └── SuggestedReminder  (deadline detected from content)
```

Every item is owned by a `userId`. All read and write endpoints scope queries to the authenticated user — no cross-user data access is possible.

---

## Features

### Chrome Extension
Save the current tab or all open tabs with one click. A popup UI shows recent saves and lets users write quick plaintext notes. The background service worker handles JWT authentication, silent token refresh with rotation, and an offline queue that retries failed saves when connectivity returns.

### PWA Dashboard
Full-featured item management: search by full-text (PostgreSQL `tsvector`), filter by item type, category, date range, and tags. Toggle between grid and list view. Items are grouped by category with collapsible section headers. Inline editing of title, summary, and category without navigating away. Delete items directly from the card. Installable as a PWA with offline support via Workbox — cached API responses allow browsing saved items without a network connection.

### Share Target
The PWA registers as a share target via the Web App Manifest. On Android, sharing any URL to TabVault from Chrome or other apps opens the PWA's share page, pre-fills the URL, and saves it with one tap.

### Content Analysis Pipeline
After an item is saved, a background job fetches the page content (via Jsoup for articles, PDFBox for PDFs, or YouTube Data API for video transcripts), then calls the Claude API to generate a plain-English summary, suggest a category, and extract time-sensitive deadlines. Results are written back to the item asynchronously and appear on the dashboard automatically.

### Reminder Service
When a deadline is detected in content analysis, a suggested reminder is created and presented for user confirmation. Confirmed reminders are scheduled via Quartz and dispatched as Web Push notifications (VAPID) at 08:00 UTC on the due date.

### Auto-Cleanup
A daily Quartz job at 09:00 UTC checks for items the user has not visited since their configured staleness threshold. Stale items receive a push notification nudge; items beyond the archive threshold are soft-archived.

### Auth
JWT-based registration and login. Access tokens expire after 15 minutes; refresh tokens have a 7-day sliding window via rotation — every `/api/auth/refresh` call issues a new refresh token, so active sessions stay alive indefinitely.

---

## Key Design Decisions

**ECS Fargate over EC2** — no AMI management, no OS patching, no SSH hardening. For a project at this scale, Fargate's operational simplicity outweighs the per-hour cost premium over EC2.

**JPQL `@Modifying @Query` for deletes on PostgreSQL custom-type entities** — `repository.delete(entity)` in Spring Data JPA performs merge+remove, which interacts incorrectly with `@JdbcTypeCode(SqlTypes.NAMED_ENUM)` (PostgreSQL custom ENUM types). A direct JPQL delete bypasses Hibernate's entity lifecycle entirely and maps straight to `DELETE FROM items WHERE id = ? AND user_id = ?`.

**Quartz JDBC job store, not in-memory** — Quartz's default in-memory store loses all scheduled jobs on a container restart. ECS tasks are ephemeral by design; the JDBC store persists triggers in PostgreSQL, so reminder and cleanup jobs survive forced redeployments.

**`@Modifying @Query` for category reassignment** — when deleting a category, items are explicitly reassigned to `NULL` via JPQL before the category row is deleted. The database `ON DELETE SET NULL` constraint also handles this, but the explicit update keeps JPA-managed entities consistent within the same transaction.

**Refresh token rotation (sliding 7-day session)** — the backend issues a new refresh token on every `/api/auth/refresh` call. The client must store and send the new refresh token on the next cycle. This ensures stolen refresh tokens expire quickly (they are single-use) while keeping active users logged in indefinitely.

**`setAllowedOriginPatterns()` over `setAllowedOrigins()`** — the `CORS_ALLOWED_ORIGINS` list includes `chrome-extension://*` to allow the extension popup to call the API. `setAllowedOrigins()` rejects wildcard patterns and throws `IllegalArgumentException`; `setAllowedOriginPatterns()` supports them.

**S3 + CloudFront over Vercel/Amplify** — full control over cache invalidation, custom error responses (403 → `index.html` for SPA routing), and Origin Access Control. The trade-off is a manual `aws s3 sync` + CloudFront invalidation on every deploy.

**`VITE_API_BASE_URL` baked in at build time** — Vite replaces `import.meta.env.VITE_*` at build time, not runtime. The API base URL must be passed as an environment variable on every `npm run build`, not just set on the server.

**`--platform linux/amd64` on every Docker build** — Apple Silicon Macs produce `arm64` images by default. ECS Fargate runs on `x86_64`. A plain `docker build` on an M-series Mac creates an image that passes ECR validation but silently fails on ECS at runtime with `exec format error`.

**CloudFront `DefaultRootObject: index.html` + custom 403→200 error response** — without `DefaultRootObject`, direct requests to `/` return 403 from S3. Without the custom error response, any deep link (e.g., `/dashboard`) returns 403 instead of serving `index.html` and letting React Router handle the route.

**ACM certificates covering both apex and wildcard** — CloudFront requires an explicit SAN for the apex domain (`tab-vault.com`). A `*.tab-vault.com` wildcard cert alone does not cover the apex; a combined cert (`tab-vault.com` + `*.tab-vault.com`) is required.

---

## Deployment

### Infrastructure

| Resource | Service | Details |
|---|---|---|
| Backend compute | AWS ECS Fargate | Cluster: `tabvault-cluster` · Service: `tabvault-backend` · Task def: `tabvault-backend:2` |
| Container registry | Amazon ECR | `960882269399.dkr.ecr.us-east-1.amazonaws.com/tabvault-backend:latest` |
| Load balancer | AWS ALB | `tabvault-alb` · SSL via ACM `*.tab-vault.com` · health check: `/actuator/health` |
| Database | AWS RDS | PostgreSQL, us-east-1 |
| Cache | AWS ElastiCache | Redis 7 |
| Frontend hosting | AWS S3 + CloudFront | Bucket: `tabvault-pwa-960882269399` · Distribution: `E1B83K1R2F9NPK` |
| DNS | Squarespace | ALIAS `@` + CNAME `www` → CloudFront · CNAME `api` → ALB |

### Backend Deploy

```bash
# Authenticate Docker to ECR
aws ecr get-login-password --region us-east-1 | \
  docker login --username AWS --password-stdin 960882269399.dkr.ecr.us-east-1.amazonaws.com

# Build (MUST use --platform linux/amd64 on Apple Silicon)
docker build --platform linux/amd64 -t tabvault-backend backend/

# Tag and push
docker tag tabvault-backend:latest \
  960882269399.dkr.ecr.us-east-1.amazonaws.com/tabvault-backend:latest
docker push 960882269399.dkr.ecr.us-east-1.amazonaws.com/tabvault-backend:latest

# Force new ECS deployment
aws ecs update-service \
  --cluster tabvault-cluster \
  --service tabvault-backend \
  --force-new-deployment \
  --region us-east-1
```

### Frontend (PWA) Deploy

```bash
cd pwa-dashboard
VITE_API_BASE_URL=https://api.tab-vault.com npm run build
aws s3 sync dist/ s3://tabvault-pwa-960882269399/ --delete
aws cloudfront create-invalidation --distribution-id E1B83K1R2F9NPK --paths "/*"
```

### Chrome Extension Build

```bash
cd chrome-extension
VITE_API_BASE_URL=https://api.tab-vault.com npm run build
# Load dist/ via chrome://extensions → Developer Mode → Load unpacked
```

### Environment Variables (ECS Task Definition)

| Variable | Description |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `production` |
| `DATABASE_URL` | PostgreSQL JDBC connection string (RDS endpoint) |
| `DATABASE_USERNAME` | Database username |
| `DATABASE_PASSWORD` | Database password |
| `JWT_SECRET` | HMAC-SHA256 signing key (64+ chars) |
| `JWT_ACCESS_TOKEN_EXPIRY_MINUTES` | `15` |
| `JWT_REFRESH_TOKEN_EXPIRY_DAYS` | `7` |
| `CORS_ALLOWED_ORIGINS` | `https://tab-vault.com,https://www.tab-vault.com,https://d2ild2z6m4fxnj.cloudfront.net,chrome-extension://*` |
| `ANTHROPIC_API_KEY` | Claude API key for content analysis |
| `CLAUDE_MODEL` | `claude-sonnet-4-6` |
| `YOUTUBE_API_KEY` | YouTube Data API v3 key for video transcript extraction |
| `VAPID_PUBLIC_KEY` | VAPID public key for Web Push |
| `VAPID_PRIVATE_KEY` | VAPID private key for Web Push |
| `VAPID_SUBJECT` | `mailto:...` contact for VAPID |
| `REDIS_URL` | ElastiCache Redis connection URL |

---

## Local Setup

### Prerequisites
- Java 21
- Maven
- PostgreSQL
- Redis (`docker run -d --name redis -p 6379:6379 redis:7-alpine`)
- Node.js 18+

### Backend

```bash
# 1. Create a PostgreSQL database
createdb tabvault

# 2. Start Redis
docker run -d --name redis -p 6379:6379 redis:7-alpine

# 3. Set environment variables (or export inline)
export DATABASE_URL=jdbc:postgresql://localhost:5432/tabvault
export DATABASE_USERNAME=postgres
export DATABASE_PASSWORD=postgres
export JWT_SECRET=your-64-char-secret-here
export ANTHROPIC_API_KEY=your-key   # Optional — content analysis won't run without it
export VAPID_PUBLIC_KEY=...         # Required for push notifications
export VAPID_PRIVATE_KEY=...
export VAPID_SUBJECT=mailto:you@example.com

# 4. Run
cd backend
./mvnw spring-boot:run
```

The backend starts on `http://localhost:8080`. Flyway runs all migrations automatically on startup.

### PWA Dashboard

```bash
cd pwa-dashboard
npm install
npm run dev
```

Runs on `http://localhost:5173`. The Vite dev server proxies `/api` requests to `http://localhost:8080` — no CORS configuration needed locally.

### Chrome Extension

```bash
cd chrome-extension
npm install
npm run build
```

Load the `dist/` folder via `chrome://extensions` → Developer Mode → Load unpacked. The extension calls `http://localhost:8080` by default in development.

---

## Project Structure

```
tab-management/
├── backend/src/main/java/com/tabvault/backend/
│   ├── auth/                # JWT filter, token service, SecurityConfig, AuthController
│   ├── items/               # Item + Category CRUD, ItemController, ItemService, ItemRepository
│   ├── contentanalysis/     # Claude API pipeline, SuggestedReminder, ContentAnalysisJob
│   ├── contentextraction/   # Jsoup article, PDFBox, YouTube transcript extraction
│   ├── reminders/           # Quartz jobs, VAPID Web Push, ReminderController
│   ├── autocleanup/         # Daily staleness check + auto-archive Quartz job
│   └── shared/              # GlobalExceptionHandler, ApiErrorResponse, shared utilities
│
├── pwa-dashboard/src/
│   ├── api/                 # apiRequest client, api-client functions, types
│   ├── auth/                # Login page, auth store (Zustand)
│   ├── dashboard/           # DashboardPage, ItemCard, filters, use-items hooks
│   ├── layout/              # AppShell, navigation
│   ├── share-target/        # ShareTargetPage (Web Share Target API)
│   ├── settings/            # User settings page
│   └── offline/             # Offline queue display
│
└── chrome-extension/src/
    ├── background/          # Service worker, apiClient, token storage, offline queue
    └── popup/               # MainView, RecentItemsList, SaveTabButton, QuickNoteForm
```
