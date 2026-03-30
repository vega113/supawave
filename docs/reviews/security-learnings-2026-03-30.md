# Security Learnings from Codex Review — 2026-03-30

## Executive Summary

A Codex-driven security review of the incubator-wave codebase identified **21 vulnerabilities across 8 classes**: stored XSS (4), SSRF (3), denial-of-service via resource exhaustion (9), session fixation (1), CSRF (1), TOCTOU race conditions (1), host header injection (1), and authorization bypass (1). The findings cluster around three systemic root causes:

1. **Untrusted data emitted without context-appropriate encoding** — avatar URLs, link annotations, and profile image MIME types were passed into HTML attributes and `href`/`src` values without escaping or scheme validation.
2. **Unbounded server-side work triggered by a single request** — history replays, directory scans, contact fan-outs, text extraction, and cache growth had no caps, making CPU/memory amplification trivially reachable by authenticated users.
3. **Missing server-side enforcement of security invariants** — session ID transport, CSRF origin checks, lock state, and owner-role assignment relied on client-side or UI-only enforcement.

All 21 PRs are catalogued below, grouped by vulnerability class.

---

## 1. Cross-Site Scripting (XSS)

### PRs: #485, #486, #505, #508

### Root Cause Pattern

Attacker-controlled strings (avatar URLs, link annotation values, profile image `data:` URIs) were interpolated into HTML output without context-appropriate sanitization. The code either:

- Trusted that URL-level validation (`sanitizeUri`) was sufficient for an HTML-attribute context (missing the HTML-escape step), or
- Applied no scheme filtering, allowing `javascript:`, `data:text/html`, and `data:image/svg+xml` payloads to survive into rendered `href`/`src` attributes.

### Code Examples from This Repo

**Avatar src injection (PR #485)** — `ParticipantAvatarViewBuilder.java`:
```java
// BEFORE: safeUrl already passed through sanitizeUri but NOT html-escaped
sb.append("src='").append(safeUrl).append("' ");

// AFTER: double-encode for the HTML attribute context
sb.append("src='").append(EscapeUtils.htmlEscape(safeUrl)).append("' ");
```

**javascript: scheme in public blip renderer (PR #505)** — `PublicWaveBlipRenderer.java`:
```java
// BEFORE: link annotation value emitted directly as href
newLink = activeAnnotations.get("link/manual");

// AFTER: whitelist-based scheme filter applied before rendering
newLink = sanitizeLinkUrl(newLink);  // returns null for javascript:/data: schemes
```

**SVG-based XSS via profile image MIME (PR #508)** — `ProfileServlet.java`:
```java
// BEFORE: any image/* MIME accepted, including image/svg+xml
if (mimeType == null || !mimeType.startsWith("image/")) { ... }

// AFTER: strict raster-only allowlist
private static final Set<String> ALLOWED_PROFILE_IMAGE_MIME_TYPES =
    Set.of("image/png", "image/jpeg", "image/gif", "image/webp");
```

**Rendered link/image URL sanitization (PR #486)** — `ServerHtmlRenderer.java`:
```java
// BEFORE: URI.create() used — rejected URLs with spaces; no scheme check for images
parsedUri = URI.create(trimmedUri);

// AFTER: indexOf(':') extraction; explicit allowlist for http/https/ftp/mailto/wave
int colonIdx = trimmedUrl.indexOf(':');
String normalizedScheme = colonIdx > 0
    ? trimmedUrl.substring(0, colonIdx).toLowerCase(Locale.ROOT) : null;
boolean allowed = "http".equals(normalizedScheme) || "https".equals(normalizedScheme) || ...;
```

### Fix Pattern

1. **Always encode for the output context.** URL sanitization protects the URL channel; HTML-escaping protects the attribute channel. You need both when placing a URL into an HTML attribute.
2. **Allowlist schemes, don't blocklist.** Only permit `http`, `https`, `mailto` (and app-specific schemes like `wave://`). Reject everything else, including `data:` and `javascript:`.
3. **Allowlist MIME types for user uploads.** For images, only accept `image/png`, `image/jpeg`, `image/gif`, `image/webp`. SVG is executable content in a browser.
4. **Set `X-Content-Type-Options: nosniff`** on all responses serving user-controlled content types.

### Prevention

- Adopt a template or builder API that auto-escapes by default (e.g., strict GWT `SafeHtmlBuilder` usage without `appendHtmlConstant` for untrusted data).
- Add a pre-commit/CI check that greps for `appendHtmlConstant` or raw string concatenation of user-controlled values in HTML contexts.

---

## 2. Server-Side Request Forgery (SSRF)

### PRs: #492, #493, #500

### Root Cause Pattern

Server endpoints accepted user-supplied URLs and made outbound HTTP requests without validating the destination. `UrlPreviewServlet` used `HttpURLConnection` with `setInstanceFollowRedirects(true)`, allowing:

- Direct requests to `http://169.254.169.254/...` (cloud metadata endpoints)
- Redirect chains from a public URL to an internal IP
- Requests to `localhost`, link-local, or RFC 1918 private ranges

`RobotRegistrationServlet` was publicly accessible with no authentication, enabling an attacker to trigger outbound fetches from the server.

### Code Examples from This Repo

**URL preview SSRF (PR #492)** — `UrlPreviewServlet.java`:
```java
// BEFORE: only scheme-prefix check, auto-follow redirects
if (!targetUrl.startsWith("http://") && !targetUrl.startsWith("https://")) { ... }
conn.setInstanceFollowRedirects(true);

// AFTER: DNS resolution + IP blocklist + manual redirect loop
validateUrlForPreview(URI.create(targetUrl).toURL());
conn.setInstanceFollowRedirects(false);
// Each redirect hop re-validated:
while (redirectCount <= MAX_REDIRECTS) {
    validateUrlForPreview(currentUrl);
    ...
}
```

**IP blocklist implementation:**
```java
private static boolean isBlockedAddress(InetAddress address) {
    if (address.isAnyLocalAddress() || address.isLoopbackAddress()
        || address.isLinkLocalAddress() || address.isSiteLocalAddress()
        || address.isMulticastAddress()) {
        return true;
    }
    // Also block: CGN (100.64/10), benchmarking (198.18/15), ULA IPv6 (fc00::/7)
}
```

**Unauthenticated robot registration (PR #500)** — `ServerMain.java`:
```java
// BEFORE: public endpoint that triggers outbound HTTP
server.addServlet("/robot/register/*", RobotRegistrationServlet.class);

// AFTER: route removed entirely
```

### Fix Pattern

1. **Resolve DNS before connecting**, then check all resolved addresses against a private/reserved IP blocklist.
2. **Disable auto-redirect following.** Implement bounded manual redirect handling with re-validation at each hop.
3. **Block hostnames**: `localhost`, `*.localhost`, `metadata`, `metadata.google.internal`.
4. **Block IP ranges**: loopback, link-local, site-local, any-local, multicast, CGN (100.64.0.0/10), benchmarking (198.18.0.0/15), ULA IPv6 (fc00::/7).
5. **Remove or auth-gate unused endpoints** that can trigger outbound requests.

### Prevention

- Centralize all server-side HTTP fetching behind a single `SafeHttpClient` utility that enforces SSRF protections.
- Audit any `HttpURLConnection`, `HttpClient`, or `URL.openConnection()` call for SSRF exposure.

---

## 3. Denial of Service (Resource Exhaustion)

### PRs: #495, #497, #498, #499, #501, #502, #503, #506, #509

### Root Cause Pattern

Request handlers performed unbounded work proportional to data size rather than request parameters. Patterns observed:

| Sub-pattern | PRs | Description |
|---|---|---|
| Unbounded history replay | #495, #498 | Full wavelet history loaded into memory and replayed without version-span caps |
| Full storage scan per request | #497, #501 | `FileAccountStore.getAccountByEmail` did an O(n) directory scan per call; `MemoryPerUserWaveViewHandlerImpl` called `loadAllWavelets()` on every cache miss |
| Quadratic fan-out | #499 | `ContactsRecorder` iterated all existing participants for each `AddParticipant` op, producing O(n^2) contact writes |
| Unbounded text extraction | #502, #509 | `extractTextFromBlip` and `collateTextForDocuments` materialized full blip text before truncating |
| Missing backpressure | #503 | Queue counter never incremented, making `MAX_QUEUE_PER_USER` ineffective |
| Unbounded cache growth | #506 | `TagsServlet` cache used a `HashMap` with TTL but no eviction, growing without bound |

### Code Examples from This Repo

**Unbounded history scan (PR #495)** — `VersionedFetchServlet.java`:
```java
// BEFORE: no cap on history version span
// Fetch all deltas and group them — unbounded work

// AFTER: hard cap with early rejection
private static final long MAX_GROUPS_HISTORY_VERSION_SPAN = 10000L;
if (currentVersion > MAX_GROUPS_HISTORY_VERSION_SPAN) {
    response.sendError(SC_REQUEST_ENTITY_TOO_LARGE, "Wavelet history is too large");
    return;
}
```

**Directory scan DoS (PR #497)** — `FileAccountStore.java`:
```java
// BEFORE: O(n) file scan per email lookup
File[] files = dir.listFiles((d, name) -> name.endsWith(ACCOUNT_FILE_EXTENSION));
for (File f : files) { ... readAccount(pid); ... }

// AFTER: one-time email index, O(1) lookup
private final Map<String, ParticipantId> emailToParticipant = Maps.newHashMap();
ParticipantId participantId = emailToParticipant.get(normalizeEmail(email));
```

**Quadratic contact updates (PR #499)** — `ContactsRecorder.java`:
```java
// BEFORE: for each AddParticipant, iterate ALL existing participants
for (ParticipantId participant : participants) {
    contactManager.newCall(participant, receptor, ...);  // O(n) per add
}

// AFTER: only record the direct caller -> receptor relationship
```

**Unbounded text extraction (PR #502)** — `SimpleSearchProviderImpl.java`:
```java
// BEFORE: full blip text materialized
String text = extractTextFromBlip(blip).toLowerCase();

// AFTER: bounded extraction
private static final int MAX_SEARCHABLE_BLIP_TEXT_CHARS = 32768;
String text = extractTextFromBlip(blip, MAX_SEARCHABLE_BLIP_TEXT_CHARS).toLowerCase();
```

### Fix Pattern

1. **Cap work per request.** Every endpoint that iterates over data should have a hard upper bound. Return HTTP 413 when exceeded.
2. **Index hot lookup paths.** Replace O(n) scans with indexed lookups (e.g., email-to-participant map).
3. **Enforce backpressure.** Per-user queue counters must be incremented/decremented correctly, and retries must be tracked.
4. **Bound text extraction.** Pass a `maxChars` parameter and stop processing when reached.
5. **Evict caches.** Any `Map`-based cache must either use an LRU eviction policy or periodic cleanup.
6. **One-time warmup gates.** Storage-scanning operations should run at most once using `AtomicBoolean` or similar.

### Prevention

- Design review: any `for` loop over stored data in a request handler is a red flag. Ask: "What bounds the iteration?"
- Use `LoadingCache` with `maximumSize` and `expireAfterAccess` (Guava) instead of raw `HashMap` for any in-memory cache.

---

## 4. Session Fixation

### PR: #489

### Root Cause Pattern

Jetty's `SessionHandler` supports session IDs via URL path parameters (`;jsessionid=...`) by default. This allows session fixation attacks where an attacker prepends a known session ID to a URL and tricks a victim into authenticating with it.

### Code Example

```java
// BEFORE: default Jetty config allows ;jsessionid=... in URL
SessionHandler sessionHandler = new SessionHandler();

// AFTER: disable URL-based session IDs
sessionHandler.setSessionIdPathParameterName(null);
```

### Fix Pattern

- Disable URL-based session transport at the container level.
- Rely exclusively on `HttpOnly`, `Secure`, `SameSite` cookies for session management.

### Prevention

- Add to the server module's hardening checklist: verify `setSessionIdPathParameterName(null)` is set.
- Test that `GET /path;jsessionid=FAKE` does not create or resume a session.

---

## 5. Cross-Site Request Forgery (CSRF)

### PR: #507

### Root Cause Pattern

State-changing POST endpoints (`/account/settings/email`, `/account/settings/request-password-reset`) relied only on session cookies for authentication, with no origin validation. An attacker could craft a cross-origin form submission to change a victim's email or trigger a password reset.

### Code Example

```java
// BEFORE: no origin check — any authenticated POST accepted
protected void doPost(HttpServletRequest req, ...) {
    HumanAccountData caller = getAuthenticatedUser(req, resp);
    ...
}

// AFTER: same-origin validation before processing
if (!isTrustedSameOriginRequest(req)) {
    sendJsonError(resp, SC_FORBIDDEN, "CSRF validation failed");
    return;
}

private static boolean isTrustedSameOriginRequest(HttpServletRequest req) {
    String origin = req.getHeader("Origin");
    if (origin != null && !origin.isEmpty()) {
        return expectedOrigin.equals(origin);
    }
    String referer = req.getHeader("Referer");
    return referer != null && referer.startsWith(expectedOrigin + "/");
}
```

### Fix Pattern

1. Check the `Origin` header first; fall back to `Referer` if absent.
2. Reject requests where neither header matches the expected server origin.
3. Complement with `SameSite=Strict` or `SameSite=Lax` on session cookies.

### Prevention

- Apply origin validation as a servlet filter to all state-changing endpoints, not per-servlet.

---

## 6. Race Conditions (TOCTOU)

### PR: #494

### Root Cause Pattern

First-user owner assignment checked `accountStore.getAccountCount() == 0` in one call and then persisted the account in a separate call. Two concurrent first registrations could both see `count == 0` and both receive `ROLE_OWNER`.

### Code Example

```java
// BEFORE: check and write are separate, non-atomic operations
assignOwnerIfFirst(account);       // reads count
accountStore.putAccount(account);  // writes account

// AFTER: synchronized critical section
private boolean persistAccountWithOwnerAssignment(HumanAccountDataImpl account) {
    synchronized (accountStore) {
        assignOwnerIfFirst(account);
        accountStore.putAccount(account);
    }
    return true;
}
```

### Fix Pattern

- Wrap check-then-act sequences in a synchronized block or use atomic compare-and-swap.
- For database-backed stores, use a transaction with serializable isolation or a unique constraint.

### Prevention

- Flag any `if (store.count() == 0) { store.write(...) }` pattern as a TOCTOU risk in code review.

---

## 7. Host Header Injection

### PR: #496

### Root Cause Pattern

Email-sending servlets constructed absolute URLs from `req.getScheme()`, `req.getServerName()`, and `req.getServerPort()`. An attacker who controls the `Host` header (trivial with a raw HTTP request) can make password-reset and email-confirmation links point to an attacker-controlled domain, exfiltrating JWT tokens.

### Code Example

```java
// BEFORE: URL built from request headers
String scheme = req.getScheme();
String serverName = req.getServerName();
url.append(scheme).append("://").append(serverName);

// AFTER: canonical URL from config
this.publicBaseUrl = PublicBaseUrlResolver.resolve(config);
// ...
return publicBaseUrl + "/auth/password-reset?token=" + token;
```

### Fix Pattern

- **Never derive canonical URLs from the request.** Use a configuration-driven `public_url` setting.
- Centralize URL construction in a single `PublicBaseUrlResolver` utility.

### Prevention

- Grep for `req.getServerName()`, `req.getScheme()`, `request.getServerName()` in URL-building contexts. These are host-header-injectable unless behind a trusted proxy that overwrites the header.

---

## 8. Authorization Bypass

### PR: #504

### Root Cause Pattern

The wave lock feature (preventing edits to locked waves) was enforced only in the client-side UI. A custom client or direct WebSocket operation submitter could bypass locks and edit content or change the lock document itself. The server's `submitDelta` path had no lock-state validation.

### Code Example

```java
// BEFORE: no lock enforcement in submit path
wavelet.submitRequest(waveletName, delta, resultListener);

// AFTER: server-side WaveLockValidator before applying deltas
String lockError = WaveLockValidator.validate(snapData, deserialized, deserialized.getAuthor());
if (lockError != null) {
    resultListener.onFailure(FederationErrors.badRequest(lockError));
    return;
}
```

The validator checks:
- Only the wave creator may modify `m/lock`
- `ALL_LOCKED`: reject all blip edits
- `ROOT_LOCKED`: reject edits to the root blip only

### Fix Pattern

- **Enforce all security-relevant state on the server.** The client is untrusted.
- Authorization checks belong in the operation-submit path, not the UI layer.

### Prevention

- Any feature that restricts who can do what must have server-side enforcement. If a code review finds the restriction only in client/UI code, flag it.

---

## Checklists

### Implementer Checklist

When writing code, verify:

- [ ] **XSS**: Any user-controlled string placed in HTML is escaped for the output context (attribute, element, URL)
- [ ] **XSS**: URL scheme is allowlisted (http/https/mailto only) before emitting in `href` or `src`
- [ ] **XSS**: User-uploaded content types are allowlisted (no SVG, no text/html)
- [ ] **SSRF**: All outbound HTTP requests validate the target IP against a private-range blocklist
- [ ] **SSRF**: Redirect following is manual with per-hop re-validation
- [ ] **DoS**: Every loop over stored data in a request handler has a hard upper bound
- [ ] **DoS**: Text extraction/serialization has a `maxChars` or `maxBytes` limit
- [ ] **DoS**: Caches use bounded data structures (LRU, `LoadingCache`, or periodic eviction)
- [ ] **DoS**: Queue counters are incremented and decremented atomically with the actual enqueue/dequeue
- [ ] **Session**: Session IDs are cookie-only (no URL path parameters)
- [ ] **CSRF**: State-changing endpoints validate `Origin`/`Referer` headers
- [ ] **Race conditions**: Check-then-act sequences are in a synchronized block or use CAS
- [ ] **Host header**: Canonical URLs come from config, never from `req.getServerName()`
- [ ] **AuthZ**: Security restrictions are enforced server-side, not just in the UI

### Reviewer Checklist

When reviewing code, look for:

- [ ] `appendHtmlConstant` or string concatenation with user data in HTML builders
- [ ] `href=` or `src=` assignments without scheme validation
- [ ] `HttpURLConnection`, `HttpClient`, `URL.openConnection()` without SSRF protections
- [ ] Unbounded `for`/`while` loops over persistent data in request handlers
- [ ] `HashMap` or `ConcurrentHashMap` used as caches without eviction
- [ ] `req.getServerName()` or `req.getScheme()` used to build URLs
- [ ] `setInstanceFollowRedirects(true)` on server-side HTTP connections
- [ ] State-changing servlet handlers (`doPost`, `doPut`, `doDelete`) without CSRF checks
- [ ] Security-relevant conditions checked only in client-side/GWT code
- [ ] `synchronized` blocks that don't cover both check and write in TOCTOU-prone paths

### Architecture Checklist

Design-level concerns for future features:

- [ ] Any endpoint that fetches external resources must go through a centralized `SafeHttpClient` with SSRF protections
- [ ] Any user-facing text rendering must use an auto-escaping template engine
- [ ] Session configuration must be hardened at the container level (cookie-only, `HttpOnly`, `Secure`, `SameSite`)
- [ ] CSRF protection should be applied as a filter, not per-servlet
- [ ] All authorization invariants must be enforced in the server's operation-submit path
- [ ] Caching strategy must specify maximum size and eviction policy before implementation
- [ ] Any `data:` URL handling must validate MIME types against a strict allowlist
- [ ] The `PublicBaseUrlResolver` pattern should be used for all canonical URL generation
