# Proposal: Add CloudShell-first debugging rule for HTTPS/TLS connectivity issues in AWS deployments

## Evidence

Phase 11 (AWS backend deployment), Problem 6:

> After setting up api.tab-vault.com DNS (CNAME to ALB), curl returned HTTP 000. openssl s_client
> showed TCP RST during TLS handshake for api.tab-vault.com SNI. Raw ALB URL worked fine — same
> IPs. Tested from AWS CloudShell — worked immediately. Root cause: Home ISP performing SNI-based
> deep packet inspection, blocking newly registered custom domain. Resolution: Switched internet
> connection; the AWS infrastructure was correct all along.

> Side fix kept: Changed ALB security policy from ELBSecurityPolicy-TLS13-1-2-Res-PQ-2025-09
> (post-quantum, very new) to ELBSecurityPolicy-TLS13-1-2-2021-06 (better client compatibility).
>
> Side fix kept: Removed duplicate SNI cert from ALB listener (same *.tab-vault.com cert was in
> both default slot and additional SNI list — cleaner to keep only in default slot).

The root cause investigation wasted significant time chasing AWS configuration because local
network interference was not suspected. A single CloudShell test at the outset would have
immediately confirmed the infrastructure was correct and shifted focus to local network isolation.
The two side fixes (security policy and duplicate cert) are genuine improvements kept regardless.

## Proposed Change

This incident does not map to an existing skill file — it is operational knowledge for AWS
deployment debugging. The most appropriate home is a new entry in
`~/.claude/skills/qa-checklist/references/common-failure-patterns.md` since QA and deployment
verification are where HTTPS connectivity checks occur:

```
## N. HTTPS/TLS connectivity failures that are actually local network interference

When a newly configured HTTPS endpoint (custom domain, new certificate, new ALB) returns
HTTP 000 or shows a TCP RST during TLS handshake from a local machine, but the raw load
balancer URL (without the custom domain) works fine and the IPs resolve to the same addresses,
the failure may be caused by local network interference rather than any AWS misconfiguration.

Common causes:
- ISP-level SNI-based deep packet inspection blocking newly registered domains.
- Corporate or home firewall rules triggered by domain name patterns.
- Local DNS caching serving stale records.

Debugging rule: Before investigating AWS infrastructure, test from AWS CloudShell (which
routes through AWS's own network, bypassing any local ISP/corporate DPI). If the endpoint
responds correctly from CloudShell, the AWS configuration is correct and the issue is local.

Additional TLS best practices confirmed by this incident:
- Use ELBSecurityPolicy-TLS13-1-2-2021-06 as the default ALB security policy. Avoid
  ELBSecurityPolicy-*-PQ-* (post-quantum) policies — they use newer cipher suites that may
  be blocked or unsupported by clients and inspection appliances.
- Do not add a certificate to both the default slot and the additional SNI list on an ALB
  listener — the duplicate entry is redundant and can cause confusion during debugging.
```

## Target File

- `~/.claude/skills/qa-checklist/references/common-failure-patterns.md` — add new pattern

## Impact

Any future deployment verification step that involves HTTPS connectivity testing will have a
documented fast path: test from CloudShell first to rule out local network interference before
investigating AWS configuration. Saves significant debugging time in any project with a custom
domain on AWS.

## Risk

Low. The proposal is additive guidance, not a constraint change. The CloudShell-first rule is
correct and general.
