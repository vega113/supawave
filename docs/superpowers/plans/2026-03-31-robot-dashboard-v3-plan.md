# Robot Dashboard v3 — REST API + UI Redesign

**Date:** 2026-03-31
**Branch:** `feat/robot-dashboard-v3`
**Status:** Planning

## Overview

Complete overhaul of the Robot Control Room at `/account/robots`:
1. New REST API at `/api/robots` with JWT Bearer auth
2. UI redesigned to match the mockup (wave blue, card layout, inline editing, toasts)
3. UI calls REST API via `fetch()` instead of form POSTs
4. API docs updated

## Part A — REST API (`RobotApiServlet`)

### Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/robots` | List all robots owned by authenticated user |
| POST | `/api/robots` | Register a new robot |
| GET | `/api/robots/{id}` | Get robot details |
| PUT | `/api/robots/{id}/url` | Update callback URL |
| PUT | `/api/robots/{id}/description` | Update description |
| POST | `/api/robots/{id}/rotate` | Rotate consumer secret |
| POST | `/api/robots/{id}/verify` | Test bot (fetch capabilities) |
| PUT | `/api/robots/{id}/paused` | Pause/unpause |
| DELETE | `/api/robots/{id}` | Soft delete |

### Authentication

- Extract JWT from `Authorization: Bearer <token>` header
- Validate with `keyRing.validator().validate(token, revocationState)`
- Accept tokens with `JwtTokenType.DATA_API_ACCESS` and audience `JwtAudience.DATA_API`
- Map `claims.subject()` to `ParticipantId` — this is the owner
- Return 401 JSON if missing/invalid

### Response Format

```json
// Success
{"id": "bot@domain", "description": "...", "callbackUrl": "...", "status": "active|paused", ...}

// Error
{"error": "message", "code": "ERROR_CODE"}
```

### Soft Delete

DELETE sets `paused=true` and clears callback URL (making the robot inoperable).
A `deleted` flag doesn't exist in the data model — we use the existing `paused` + empty URL as "soft deleted".

### Security

- No CSRF concern: stateless JWT Bearer auth (not cookie-based)
- Ownership check: every mutation verifies `robot.ownerAddress == token.subject`
- Body size limit: 16KB max (same as existing JSON endpoint)

### Key Dependencies

- `JwtKeyRing` — injected, provides `validator()`
- `AccountStore` — for loading robots
- `RobotRegistrar` — for register/update/delete operations
- `RobotCapabilityFetcher` — for verify/test
- `SessionManager` — NOT needed (JWT-only, no sessions)

## Part B — UI Redesign

Replace `renderDashboardPage()` HTML with mockup design:

- **Hero section**: gradient blue (#023e8a → #0096c7 → #48cae4), wavy SVG bottom edge
- **Two-tab layout**: "My Robots" | "Register New"
- **Robot cards**: expand/collapse, 🤖 avatar, name, Active/Paused pill, last active, created date
- **Fields**: 2-column grid, ⓘ tooltips, inline 💾 save buttons
- **Action bar**: Test Bot · Rotate Secret · Pause/Unpause · copy address
- **Danger Zone**: red bg section with checkbox confirm + Delete button
- **Sidebar** (Register tab only): "Build with AI" prompt + API Token generator
- **Toast notifications**: green=success, red=error, blue=info (positioned bottom-right)
- **Mobile responsive**: single column on ≤860px

## Part C — UI Uses REST API

- `renderDashboardPage()` emits the HTML shell only (hero, tabs, empty containers)
- JavaScript `fetch()` calls to `/api/robots` for all data and mutations
- Token generated once via `/robot/dataapi/token` (session-based POST) and cached
- No more `<form method="post">` — all actions via JSON API calls
- Toast notifications for API responses (success/error)

### Token flow for UI
1. Page loads → JS calls `POST /robot/dataapi/token` (session-based, existing endpoint)
2. Gets back `access_token` → stored in JS variable
3. All `/api/robots` calls use `Authorization: Bearer <token>`
4. If 401 → redirect to sign-in

## Part D — API Docs Update

- Add Robot Management API section to ApiDocsServlet
- Update `/api/llm.txt` to mention `POST /api/robots` for LLM robot registration
- Include request/response schemas

## Files to Create/Modify

### NEW
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/RobotApiServlet.java`

### MODIFY
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/RobotDashboardServlet.java` — full UI rewrite
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/ServerMain.java` — register `/api/robots/*`
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/ApiDocsServlet.java` — add robot API docs

## Implementation Order

1. Create `RobotApiServlet.java` with all endpoints
2. Register in `ServerMain.java`
3. Rewrite `RobotDashboardServlet.renderDashboardPage()` with new UI
4. Update `ApiDocsServlet` with robot API documentation
5. Compile and test
