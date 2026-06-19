# Proposal: Mandate --platform linux/amd64 for all Docker builds targeting cloud deployment on Apple Silicon

## Evidence

Phase 11 (AWS backend deployment), Problem 3:

> MacBook Pro is arm64 (Apple Silicon), ECS Fargate runs x86_64. Plain `docker build` produces
> arm64 image, fails on Fargate. Fix: Always use `docker buildx build --platform linux/amd64`.

This caused a silent deployment failure: the image built and pushed successfully, but the ECS task
refused to start because the container runtime could not execute an arm64 binary on an x86_64
host. The error was only visible in ECS task logs, not during the docker build or push step.

## Proposed Change

Add a new judgment item to `~/.claude/skills/engineer-checklist/SKILL.md`:

```
- If the project includes a Dockerfile and targets a cloud deployment environment (ECS, GKE,
  Cloud Run, Heroku, Fly.io, or any x86_64 runtime): all Docker builds must use
  `docker buildx build --platform linux/amd64` rather than plain `docker build`. On Apple
  Silicon (arm64) machines, plain `docker build` produces an arm64 image that will silently
  fail to start on x86_64 cloud runtimes. The --platform flag must also be documented in any
  deployment runbook, Makefile, or CI script produced for the project.
```

Also add a new failure pattern entry to
`~/.claude/skills/qa-checklist/references/common-failure-patterns.md`:

```
## N. Docker image architecture mismatch: arm64 image on x86_64 cloud runtime

When a Docker image is built on Apple Silicon (arm64) without an explicit --platform flag and
deployed to an x86_64 cloud runtime (AWS ECS Fargate, GKE, Cloud Run, etc.), the container
will fail to start. The failure is silent at build and push time — it only surfaces as a
container startup error in the cloud provider's task/pod logs.

Symptoms:
- ECS task stops immediately after launch with exit code 1 and "exec format error" in logs.
- GKE pod enters CrashLoopBackOff with the same error.
- The Docker image was successfully built and pushed, so no local build error is visible.

Fix: Always use `docker buildx build --platform linux/amd64 -t <image>:<tag> .` when building
for cloud deployment from any Apple Silicon machine.

Verification: After building, run `docker inspect <image>:<tag> | grep Architecture` and confirm
the output is "amd64", not "arm64".
```

## Target Files

- `~/.claude/skills/engineer-checklist/SKILL.md` — add to judgment_items section
- `~/.claude/skills/qa-checklist/references/common-failure-patterns.md` — add new pattern

## Impact

Any engineer agent producing Docker deployment instructions for this project or future projects
will include the correct --platform flag. QA agents will know to verify image architecture before
a deployment can be declared healthy.

## Risk

Low. The change is additive and specific to Docker on Apple Silicon. On non-Apple-Silicon
machines the --platform flag is a no-op for amd64 targets, so adding it unconditionally is safe.
