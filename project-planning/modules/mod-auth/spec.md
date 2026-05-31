**Last Synced from PRD Revision**: 2 | **Last Updated**: 2026-05-30

---

## Module ID & Name

MOD-001: Authentication

## Purpose

Manages user registration, login, JWT access token issuance, refresh token rotation, and logout. Does not manage profile data or any item-level authorization.

## Context

**Business problem this module addresses:**

Enable users to save any browser tab in one action (click or keyboard shortcut) and have the tab's content automatically summarized and categorized within 10 seconds of saving, so users can close the tab immediately with no manual effort. Provide a searchable dashboard that returns matching saved items within 3 seconds of a search query, so users can reliably locate any previously saved item without reopening their browser history. My saved items are securely stored and accessible across devices under my account — this module provides the authentication foundation for all cross-device access.

**Related user stories (full text):**

**US-015**: As a new user, I want to register an account and log in from both the extension and the PWA dashboard, so that my saved items are securely stored and accessible across devices under my account.

**Non-goals from PRD that bound this module:**

- SSO, SAML, or OIDC federation is out of scope for v1. Authentication is email and password with JWT only.
- Multi-tenancy and organization-level account sharing are out of scope for v1. Each account is personal and single-user.
- A self-service admin panel for user management or system configuration is out of scope for v1.

## Related User Stories: US-015

- US-015

## Requirements

- The system shall create a new user account and return HTTP 201 with the user's display name and a JWT access token when a valid email address and a password of at least 8 characters are submitted to the registration endpoint.
- The system shall return HTTP 409 when a registration request is submitted with an email address already associated with an existing account.
- The system shall return a JWT access token valid for 15 minutes and a refresh token valid for 7 days in the response body when a user submits valid credentials to the login endpoint.
- The system shall return HTTP 401 with a generic error message that does not indicate whether the account exists or the password is incorrect when a user submits invalid credentials to the login endpoint.

## Input / Output Contract

**Input:**

- Registration endpoint: valid email address, password of at least 8 characters, displayName: required string
- Login endpoint: email address, password
- Token refresh endpoint: valid refresh token

**Output:**

- Registration success: HTTP 201, user display name, JWT access token
- Registration duplicate: HTTP 409
- Login success: JWT access token (15-minute expiry), refresh token (7-day expiry) in the response body
- Login failure: HTTP 401, generic error message (must not indicate whether account exists or password is incorrect)

## Dependencies

none

## Acceptance Criteria

- AC-044: The system shall create a new user account and return HTTP 201 with the user's display name and a JWT access token when a valid email address and a password of at least 8 characters are submitted to the registration endpoint.
- AC-045: The system shall return HTTP 409 when a registration request is submitted with an email address already associated with an existing account.
- AC-046: The system shall return a JWT access token valid for 15 minutes and a refresh token valid for 7 days in the response body when a user submits valid credentials to the login endpoint.
- AC-047: The system shall return HTTP 401 with a generic error message that does not indicate whether the account exists or the password is incorrect when a user submits invalid credentials to the login endpoint.
