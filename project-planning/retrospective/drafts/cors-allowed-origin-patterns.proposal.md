# Proposal: Use setAllowedOriginPatterns() not setAllowedOrigins() when CORS origins include wildcards

## Evidence

Phase 11 (AWS backend deployment), Problem 4:

> setAllowedOrigins() doesn't support wildcards — throws exception for chrome-extension://*.
> Fix: Changed to setAllowedOriginPatterns() which supports wildcards.

This caused a Spring Security startup exception that blocked the backend from responding to any
CORS preflight requests. The chrome-extension://* origin pattern is required by the project
(MOD-007 Chrome Extension), so this would have been triggered the first time the SecurityConfig
was deployed. The error was not visible locally if the extension was not installed during local
development testing.

## Proposed Change

Add a new failure pattern entry to
`~/.claude/skills/qa-checklist/references/common-failure-patterns.md`:

```
## N. Spring CORS: setAllowedOrigins() rejects wildcard origin patterns

Spring's CorsConfiguration.setAllowedOrigins() does not support wildcard characters (e.g.,
chrome-extension://*, https://*.example.com). Passing a wildcard to setAllowedOrigins() throws
an IllegalArgumentException at startup or at the first CORS request, depending on the Spring
version and configuration mode.

Fix: Use setAllowedOriginPatterns() instead of setAllowedOrigins() whenever any origin entry
contains a wildcard. setAllowedOriginPatterns() supports wildcards and is the correct method
for browser extension origin patterns, subdomain wildcards, and similar use cases.

Test approach: Verify the SecurityConfig CORS setup includes a wildcard origin (especially for
Chrome Extension projects), confirm the application starts without error, and send a CORS
preflight request (OPTIONS with Origin: chrome-extension://fakeid) against the running server
to confirm HTTP 200 with the correct Allow-Origin header is returned.
```

Also add a new judgment item to `~/.claude/skills/engineer-checklist/SKILL.md`:

```
- If the backend configures CORS and any allowed origin includes a wildcard character (*, ?,
  or a Chrome/Firefox extension URL scheme): use setAllowedOriginPatterns() in the CORS
  configuration, not setAllowedOrigins(). setAllowedOrigins() will throw an exception or
  silently block wildcard origins.
```

## Target Files

- `~/.claude/skills/qa-checklist/references/common-failure-patterns.md` — add new pattern
- `~/.claude/skills/engineer-checklist/SKILL.md` — add to judgment_items section

## Impact

Any engineer agent writing Spring Security configuration for a project that includes a browser
extension client will produce correct CORS configuration from the start. Any QA agent verifying
a backend with extension clients will test CORS preflight responses.

## Risk

Low. The proposal is additive. setAllowedOriginPatterns() is a valid Spring API and the correct
replacement for setAllowedOrigins() in all wildcard use cases.
