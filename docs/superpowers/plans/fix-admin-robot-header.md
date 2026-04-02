# Plan: Add Shared App Header to Server-Rendered Pages

## Problem

Four logged-in server-rendered pages render their own minimal custom top bars, missing the full
Wave app header (logo, language selector, connection status, user-menu dropdown with sign-out).

Pages with this deficiency:
1. Admin dashboard (`/admin`) — `.admin-header` div
2. Robot Control Center (`/account/robots`) — back link + `.hdr` section
3. Account Settings (`/account/settings`) — `.settings-header` div
4. Profile Edit (`/userprofile/edit`) — `.profile-header` div

Authentication pages (login, register, password reset, magic link) are intentionally excluded —
they are accessed when NOT logged in and use a centered card design.

## Pages to Update

| Page | Render method | URL | Current top bar |
|------|--------------|-----|----------------|
| Admin | `HtmlRenderer.renderAdminPage` | `/admin` | `.admin-header` div |
| Robot Control Center | `RobotDashboardServlet.renderDashboardPage` | `/account/robots` | Back link + `.hdr` |
| Account Settings | `HtmlRenderer.renderAccountSettingsPage` | `/account/settings` | `.settings-header` div |
| Profile Edit | `HtmlRenderer.renderProfileEditPage` | `/userprofile/edit` | `.profile-header` div |

## Research Findings

### Key existing infrastructure in `HtmlRenderer.java`

- `WAVE_LOGO_SVG_SMALL` (line 1369): 28px Firefly Glowing Wave logo SVG
- `WAVE_PRIMARY = "#0077b6"`, `WAVE_ACCENT = "#00b4d8"` — color constants
- `renderTopBar(String username, String domain, String userRole)` (line 3211): Full Wave app
  topbar HTML. **`username` is the LOCAL part only** (e.g. `alice`), NOT the full address.
- Topbar CSS: defined inline in `renderWaveClientPage` (lines ~2205–2543)
- User-menu toggle JS: in `renderWaveClientPage` (lines ~2607-2617)
- `escapeJsonString()` method: already exists in `HtmlRenderer` (used at lines 1753, 2201 etc.)
- `ICON_GLOBE`, `ICON_WIFI_OFF`, `ICON_CLOUD_CHECK` — icon SVG constants

### User/session data available in callers

| Page | fullAddress | domain | userRole |
|------|-------------|--------|---------|
| Admin | `caller.getId().getAddress()` | field | `caller.getRole()` |
| Robot Dashboard | `user.getAddress()` | `this.domain` (field) | not fetched |
| Account Settings | `caller.getId().getAddress()` | field | not fetched |
| Profile Edit | `caller.getId().getAddress()` | field | not fetched |

All callers provide **full address** (e.g. `alice@example.com`). All are root-deployed (contextPath = `""`). Only Admin has `userRole`.

### Data shape (full address vs local part)

`renderTopBar` takes `username` (local part) + `domain` separately. Our callers have full address.
The shared method accepts `fullAddress` and splits internally:
```java
int atIdx = fullAddress.indexOf('@');
String localPart = atIdx > 0 ? fullAddress.substring(0, atIdx) : fullAddress;
```
This avoids callers needing to split.

### userRole handling

Add `userRole` as 4th parameter to the shared HTML method (nullable). The "Admin" link in the
user-menu dropdown is shown only when `"owner".equals(userRole) || "admin".equals(userRole)`.
- Admin page: pass `callerRole` ✓
- Robot Dashboard, Account Settings, Profile Edit: pass `null` → Admin link omitted. Acceptable
  since users who are admins can reach `/admin` from the main Wave app.

### contextPath in JS — safe injection

`escapeHtml()` is NOT sufficient for injecting a value into a JS string literal (does not handle
`\`, `'`, `</script>`). Use the existing `escapeJsonString()` method:
```java
sb.append("var _ctx = ").append(escapeJsonString(contextPath)).append(";");
```
All JS references to contextPath use `_ctx` variable.

### `.section-label` and `.section-link-strong` CSS

- `.section-label` is used in `renderTopBar` HTML (line 3249) but has **no CSS defined** in the
  topbar CSS block (lines 2205–2543). Must add in `renderSharedTopBarCss()`:
  ```css
  .section-label { padding: 4px 16px 2px; font-size: 10px; font-weight: 700;
    text-transform: uppercase; letter-spacing: 0.5px; color: #999; }
  ```
- `.section-link-strong` (line 3254) also has **no CSS**. Add emphasis style:
  ```css
  .user-menu-dropdown .section-link-strong { font-weight: 600; color: #0077b6; }
  .user-menu-dropdown .section-link-strong:hover { color: #005f8f !important; }
  ```

### GWT-only `window.openVersionHistory()`

Line 3258: `onclick="window.openVersionHistory(); return false;"` — GWT-only function.
Replace this menu item with a direct `<a href="contextPath + /changelog">` link (same destination
as "What's New" item below it). Remove the `onclick` form.

### CSS range

Full topbar CSS block is lines ~2205–2543. Extract all EXCEPT:
- `WAVE_PANEL_CSS` insertion at line 2322 (GWT panel theme overrides — not needed)
- `.mobile-hamburger`, `.mobile-back`, `.mobile-backdrop` CSS (GWT-specific mobile nav)
- `@media (min-width: 768px) and (max-width: 1023px)` panel layout rules (GWT layout)

The `@media (max-width: 767px)` topbar compact rule IS needed and should be kept.

## Implementation: Three New Static Methods in `HtmlRenderer`

### `renderSharedTopBarCss()` — `public static String`

Returns self-contained CSS for the shared topbar, suitable for any server-rendered page.
Extracted from `renderWaveClientPage` CSS block minus GWT-specific parts. Includes `.section-label`
and `.section-link-strong` CSS additions noted above.

### `renderSharedTopBarHtml(String fullAddress, String domain, String contextPath, String userRole)` — `public static String`

Adapted topbar HTML. Key differences from `renderTopBar`:
- Accepts `fullAddress` (splits internally to get `localPart` for avatar initial)
- Logo links to `escapeHtml(contextPath) + "/"` (not `/?view=landing`)
- No `#mobileHamburger`, no `#mobileBack` buttons
- No `#unsavedStateContainer` (save state is GWT-only; omit entirely)
- All user-menu hrefs prefixed with `escapeHtml(contextPath)`
- "Version History" item **removed entirely** (GWT-only; the existing "What's New" link already
  points to `/changelog` — keeping both would create duplicate menu items)
- `#netstatus` starts with class `offline`; JS updates it to `online`

### `renderSharedTopBarJs(String contextPath)` — `public static String`

Returns `<script>` block using `escapeJsonString` for safe contextPath injection:

```java
sb.append("<script>\n(function(){\n");
sb.append("var _ctx=").append(escapeJsonString(contextPath)).append(";\n");
// 1. User menu toggle
sb.append("var t=document.querySelector('.user-menu-toggle');\n");
sb.append("if(t){t.addEventListener('click',function(e){e.stopPropagation();");
sb.append("document.querySelector('.user-menu-dropdown').classList.toggle('open');});}\n");
sb.append("document.addEventListener('click',function(){var d=document.querySelector('.user-menu-dropdown');if(d)d.classList.remove('open');});\n");
// 2. Connection status
sb.append("var ns=document.getElementById('netstatus');\n");
sb.append("function updNet(){if(!ns)return;ns.className='topbar-icon '+(navigator.onLine?'online':'offline');ns.title=navigator.onLine?'Online':'Offline';}\n");
sb.append("updNet();window.addEventListener('online',updNet);window.addEventListener('offline',updNet);\n");
// 3. Language selector
sb.append("var ls=document.getElementById('lang'),lc=document.getElementById('langCode');\n");
sb.append("if(ls){\n");
sb.append("[['en','English'],['fr','Français'],['de','Deutsch'],['es','Español'],['ar','العربية'],['zh','中文']].forEach(function(l){\n");
sb.append("var o=document.createElement('option');o.value=l[0];o.text=l[1];\n");
sb.append("if(l[0]===(navigator.language||'en').split('-')[0])o.selected=true;\n");
sb.append("ls.appendChild(o);});\n");
sb.append("if(lc)lc.textContent=ls.value.toUpperCase();\n");
sb.append("ls.addEventListener('change',function(){var fd=new FormData();fd.append('locale',ls.value);\n");
sb.append("fetch(_ctx+'/locale',{method:'POST',body:fd}).catch(function(){});\n");
sb.append("if(lc)lc.textContent=ls.value.toUpperCase();});}\n");
sb.append("})();\n</script>\n");
```

## Changes to Each File

### `HtmlRenderer.java` — Admin page (Step 2)

`renderAdminPage(String currentUser, String domain, String callerRole)` at line 3735:
- **Remove** `.admin-header` CSS block (lines ~3755-3766 in current file).
- **After** existing `</style>` tag: append `"<style>\n" + renderSharedTopBarCss() + "\n</style>\n"`
- **Remove** `<div class="admin-header">` ... `</div>` HTML block (lines ~3944-3953).
- **Insert** `renderSharedTopBarHtml(currentUser, domain, "", callerRole)` in its place.
- **Before** `</body>`: append `renderSharedTopBarJs("")`.

### `HtmlRenderer.java` — Account Settings page (Step 3)

`renderAccountSettingsPage(String currentUser, String domain, HumanAccountData account, boolean passwordResetEnabled)` at line 7188:
- **Remove** `.settings-header` CSS block (lines ~7210-7219).
- **After** existing `</style>` tag: append shared topbar CSS style block.
- **Remove** `<div class="settings-header">` HTML block.
- **Insert** `renderSharedTopBarHtml(currentUser, domain, "", null)` in its place.
- **Before** `</body>`: append `renderSharedTopBarJs("")`.

### `HtmlRenderer.java` — Profile Edit page (Step 4)

`renderProfileEditPage(String currentUser, String domain, String imageUrl, HumanAccountData account)` at line 7503:
- **Remove** `.profile-header` CSS block (lines ~7525-7534).
- **After** existing `</style>` tag: append shared topbar CSS style block.
- **Remove** `<div class="profile-header">` HTML block.
- **Insert** `renderSharedTopBarHtml(currentUser, domain, "", null)` in its place.
- **Before** `</body>`: append `renderSharedTopBarJs("")`.

### `RobotDashboardServlet.java` — Robot Control Center (Step 5)

`renderDashboardPage(String userAddress, ..., String contextPath)` at line 547:
- **Remove** `.back-link`, `.hdr`, `.hdr-row`, `.hdr-sub`, `.hdr-line`, `.wave-deco` CSS.
- **After** `</style>` tag: append `"<style>\n" + HtmlRenderer.renderSharedTopBarCss() + "\n</style>\n"`
- **Remove** `<a class="back-link"...>`, `<div class="hdr">...</div>`, `<div class="wave-deco">...</div>`.
- **Insert** `HtmlRenderer.renderSharedTopBarHtml(userAddress, this.domain, contextPath, null)`.
- **Move page title** into `.main` section (before tabs div):
  ```java
  sb.append("<div style=\"margin-bottom:16px\">");
  sb.append("<h2 style=\"font-size:18px;font-weight:700;color:var(--txt)\">Robot Control Center</h2>");
  sb.append("<div style=\"font-size:12px;color:var(--txt3)\">Manage automation robots for ").append(safeUser).append("</div>");
  sb.append("</div>");
  ```
- **Before** `</body>`: append `HtmlRenderer.renderSharedTopBarJs(contextPath)`.

## Files to Change (Summary)

| File | Changes |
|------|---------|
| `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java` | Add 3 static methods; update `renderAdminPage`, `renderAccountSettingsPage`, `renderProfileEditPage`. |
| `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/RobotDashboardServlet.java` | Update `renderDashboardPage`. |

`AdminServlet.java`, `AccountSettingsServlet.java`, `ProfileServlet.java` — NO changes needed.

## Security Checklist

- `fullAddress`, `domain`, `contextPath` in HTML: use `HtmlRenderer.escapeHtml()`
- `contextPath` in JS: use `HtmlRenderer.escapeJsonString()` (NOT `escapeHtml`)
- No `innerHTML` with server data in JS — only `textContent`, `createElement`, `classList`
- All JS manipulation is on classes/titles only; no server data written into DOM via JS

## Compile Verification

```bash
sbt "wave/compile"
```
Must succeed with 0 errors before committing.
