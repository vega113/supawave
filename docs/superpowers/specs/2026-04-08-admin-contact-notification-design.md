# Admin Contact Notification — Design Spec

**Date:** 2026-04-08  
**Branch:** feat/admin-contact-notification  
**Status:** Approved (full auto mode)

---

## Problem

Admins have no real-time awareness of unread contact form submissions. They must manually navigate to the admin panel's Contact Messages tab to check. This leads to delayed responses.

## Goal

Show an unread-count badge on the top bar for admin/owner users that pulsates with a red glow when there are new contact messages, and navigates to the admin Contact Messages tab when clicked.

---

## Chosen Approach: Server-Rendered Icon + Inline JS Polling

### Why this approach

- The top bar is already server-rendered in `HtmlRenderer.java`. Adding the icon as server-rendered HTML (conditionally for admin) is the natural extension of the existing pattern.
- The existing `GET /admin/api/contacts?status=new&limit=0` endpoint already returns `{"total": N, "messages": []}` — no new endpoint needed.
- No GWT changes required. Avoids GWT compilation step.
- Consistent with how the existing `contactBadge` on the admin tab already works.

---

## Architecture

### Files Modified

| File | Change |
|------|--------|
| `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java` | All changes |

### Changes Inside HtmlRenderer.java

#### 1. `renderTopBar(username, domain, userRole)` — Wave Client Top Bar HTML
Add envelope icon HTML for admin/owner users, between the netstatus icon and user-menu div.

```html
<!-- Admin-only: unread contact messages notification -->
<a id="adminMsgBtn" class="topbar-icon admin-msg-btn"
   href="/admin#contacts" title="N unread contact messages">
  <svg><!-- envelope icon --></svg>
  <span id="adminMsgBadge" class="admin-badge hidden">0</span>
</a>
```

#### 2. `renderSharedTopBarHtml(fullAddress, contextPath, userRole)` — Shared Top Bar HTML
Same envelope icon added identically for admin/owner, using context-path-prefixed href.

#### 3. `renderSharedTopBarCss()` — Shared CSS
Add CSS:
- `.admin-msg-btn` — cursor:pointer, same base style as `.topbar-icon`
- `.admin-badge` — red pill badge (absolute, top-right of icon)
- `.admin-msg-btn.has-unread` — red glow pulsating animation
- `@keyframes admin-glow` — red glow pulse (box-shadow)

#### 4. `renderSharedTopBarJs(contextPath)` → `renderSharedTopBarJs(contextPath, userRole)`
If `userRole` is "admin" or "owner": emit JS that polls every 30s and updates `adminMsgBtn` / `adminMsgBadge`.

All 3 existing call sites updated to pass `userRole`.

#### 5. `renderWaveClientPage(...)` 
Add a JS snippet after the main script block that checks `window.__session.role` at runtime. If admin/owner, starts the same polling interval. This avoids changing the method signature.

#### 6. `renderAdminPage(...)` — Hash-Based Tab Navigation
On page load: read `window.location.hash`. If it is `#contacts`, programmatically click the contacts tab button. This makes `/admin#contacts` deep-link to the contacts tab.

---

## Data Flow

```
[Admin user loads wave client or any page]
  → Server renders top bar HTML with admin envelope icon
  → JS starts 30s polling interval
  → GET /admin/api/contacts?status=new&limit=0
  → { "total": N }
  → if N > 0: show badge with count, add .has-unread class (red glow pulse)
  → if N = 0: hide badge, remove .has-unread class
  
[Admin clicks icon]
  → navigate to /admin#contacts
  → Admin page loads, sees hash = "#contacts"
  → JS activates contacts tab automatically
```

---

## Endpoint Used (Existing)

`GET /admin/api/contacts?status=new&limit=0`

Returns: `{"total": N, "messages": []}`

- Only accessible to admin users (AdminServlet returns 403 for non-admin)
- Lightweight — no message bodies fetched

---

## Visual Behavior

- **No unread messages**: Icon visible but no badge, no glow
- **Has unread messages**: Red pill badge with count ("3"), icon has red box-shadow glow that pulses at 2s intervals
- **Tooltip**: "N unread contact messages" (updated dynamically)
- **Click**: Navigates to `/admin#contacts`

---

## Security

- Icon HTML is only rendered for `userRole == "admin" || userRole == "owner"` (server-side check)
- Polling endpoint (`/admin/api/contacts`) returns 403 for non-admin — defense in depth
- No XSS risk: count is a number from JSON, inserted as `textContent` not `innerHTML`

---

## Testing

1. `sbt wave/compile` — compilation check (no GWT changes)
2. Unit test: `HtmlRendererTopBarTest.java` — add assertions for admin-only icon rendering
3. Manual: Log in as admin, check badge appears; log in as regular user, verify no badge
4. Manual: Create a contact message, wait 30s, verify badge updates
5. Manual: Click badge → verify `/admin#contacts` opens with contacts tab active

---

## Not Needed

- No new servlet/endpoint
- No GWT changes (no `sbt compileGwt` required)
- No feature flag (admin-only, directly gated by role check)
- No database changes
