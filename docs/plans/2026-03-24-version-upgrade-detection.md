# Version Upgrade Detection

**Date:** 2026-03-24
**Status:** Implementing

## Problem

When the Wave server is upgraded (new deploy), connected clients continue running
stale GWT JavaScript. Users have no way to know a new version is available and
that they should reload to pick up the latest client code.

## Approach: Version Endpoint Polling (Option A)

Simplest approach -- works independently of the WebSocket channel, easy to
implement and reason about, and gracefully degrades (if the poll fails the
client just keeps running).

### Alternatives considered

| Option | Pros | Cons |
|--------|------|------|
| **A: `/version` polling** | Independent of WS, simple, works offline | Extra HTTP request every 60s (tiny) |
| B: WebSocket notification | No extra HTTP overhead | Tied to WS lifecycle, harder to test |
| C: HTTP response header | No extra request | Requires intercepting every fetch response on the client |

## Implementation

### 1. Server: `VersionServlet` (`/version`)

- Returns `{"version":"<sha>","buildTime":<epoch>}` as JSON.
- `Cache-Control: no-cache, no-store` to prevent CDN/browser caching.
- Version string read from `core.server_version` config (default `"dev"`).
- `buildTime` captured at servlet construction (JVM startup).
- No authentication required (the version string is not sensitive -- it is the
  same information visible in any Docker image tag or deploy log).

### 2. Client: inline JS in `HtmlRenderer.renderWaveClientPage()`

- On page load: `fetch('/version')` and store the returned version.
- Poll every 60 seconds.
- If the version changes: show a fixed-position banner at bottom-right.
- Banner: "A new version of SupaWave is available. [Reload]"
- Styled in the existing Wave blue/teal palette.
- Dismiss button hides it; it reappears on the next poll if still mismatched.

### 3. Config: `reference.conf`

```hocon
core.server_version = "dev"
core.server_version = ${?WAVE_SERVER_VERSION}
```

In deploy, set `WAVE_SERVER_VERSION=<git-sha>` or `<docker-tag>`.

### 4. Servlet registration: `ServerMain.initializeServlets()`

```java
server.addServlet("/version", VersionServlet.class);
```

### 5. Integration test

`VersionServletJakartaIT` -- spins up embedded Jetty, hits `/version`,
validates JSON shape and cache headers.

## Caching considerations

- `Cache-Control: no-cache, no-store` on the `/version` response.
- Client `fetch` uses `{cache: 'no-store'}` to bypass browser cache.
- If a CDN is in front, operators should configure `/version` as uncacheable
  (or rely on the response headers).

## Security

The version endpoint exposes only the version string and a startup timestamp.
This is the same information available in deploy logs, Docker image tags, and
HTTP server headers. It is not considered sensitive.
