# Admin Contact Notification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show a pulsating red-glow envelope icon with unread-count badge on the top bar for admin/owner users, polling every 30s; clicking it navigates to `/admin#contacts`.

**Architecture:** All changes in one file — `HtmlRenderer.java` (jakarta-overrides). Server-renders the icon conditionally for admin. JS polling uses the existing `GET /admin/api/contacts?status=new&limit=0` endpoint. No new endpoints, no GWT changes.

**Tech Stack:** Java 17, server-rendered HTML/CSS/JS via StringBuilder in HtmlRenderer.java, existing AdminServlet endpoint, JUnit tests via `sbt wave/test`.

---

## File Map

| File | Changes |
|------|---------|
| `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java` | All implementation changes |
| `wave/src/test/java/org/waveprotocol/box/server/rpc/HtmlRendererTopBarTest.java` | Add unit tests |

---

## Important Context

- **Jakarta dual-source**: changes MUST be in `wave/src/jakarta-overrides/java/`, NOT `wave/src/main/java/`
- **HtmlRenderer.java** is ~8540 lines. The two top-bar HTML methods are:
  - `renderTopBar(username, domain, userRole)` — used by GWT wave client page (line ~3521)
  - `renderSharedTopBarHtml(fullAddress, contextPath, userRole)` — used by admin/settings/profile pages (line ~3407)
- **renderSharedTopBarJs(contextPath)** is a public static method (line ~3477) called from 3 places; add an overload rather than changing the signature
- **renderWaveClientPage** adds the top bar via `sb.append(topBarHtml)` (line ~2570); its admin polling JS must check `window.__session.role` at runtime
- **Test file**: `wave/src/test/java/org/waveprotocol/box/server/rpc/HtmlRendererTopBarTest.java` uses JUnit 3 (extends TestCase), no annotations
- **Build**: `sbt wave/compile` for server (no GWT changes needed)

---

### Task 1: Add ICON_MAIL constant

**Files:**
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java` (after ICON_WIFI_OFF constant, around line 3626)

- [ ] **Step 1: Add the envelope SVG constant after ICON_WIFI_OFF**

Find this in HtmlRenderer.java:
```java
  private static final String ICON_WIFI_OFF =
      "<svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"white\" stroke-width=\"1.8\" stroke-linecap=\"round\" stroke-linejoin=\"round\">"
      + "<line x1=\"1\" y1=\"1\" x2=\"23\" y2=\"23\"/>"
      + "<path d=\"M16.72 11.06A10.94 10.94 0 0 1 19 12.55\"/>"
      + "<path d=\"M5 12.55a10.94 10.94 0 0 1 5.17-2.39\"/>"
      + "<path d=\"M10.71 5.05A16 16 0 0 1 22.56 9\"/>"
      + "<path d=\"M1.42 9a15.91 15.91 0 0 1 4.7-2.88\"/>"
      + "<path d=\"M8.53 16.11a6 6 0 0 1 6.95 0\"/>"
      + "<circle cx=\"12\" cy=\"19.5\" r=\"1\"/>"
      + "</svg>";

  // =========================================================================
  // 5. Robot Registration Page
```

Replace with:
```java
  private static final String ICON_WIFI_OFF =
      "<svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"white\" stroke-width=\"1.8\" stroke-linecap=\"round\" stroke-linejoin=\"round\">"
      + "<line x1=\"1\" y1=\"1\" x2=\"23\" y2=\"23\"/>"
      + "<path d=\"M16.72 11.06A10.94 10.94 0 0 1 19 12.55\"/>"
      + "<path d=\"M5 12.55a10.94 10.94 0 0 1 5.17-2.39\"/>"
      + "<path d=\"M10.71 5.05A16 16 0 0 1 22.56 9\"/>"
      + "<path d=\"M1.42 9a15.91 15.91 0 0 1 4.7-2.88\"/>"
      + "<path d=\"M8.53 16.11a6 6 0 0 1 6.95 0\"/>"
      + "<circle cx=\"12\" cy=\"19.5\" r=\"1\"/>"
      + "</svg>";

  /** Envelope icon for admin contact notification — white on dark topbar. */
  private static final String ICON_MAIL =
      "<svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"white\" stroke-width=\"1.8\" stroke-linecap=\"round\" stroke-linejoin=\"round\">"
      + "<path d=\"M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z\"/>"
      + "<polyline points=\"22,6 12,13 2,6\"/>"
      + "</svg>";

  // =========================================================================
  // 5. Robot Registration Page
```

- [ ] **Step 2: Verify the constant is present**

Run:
```bash
grep -n "ICON_MAIL" wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java
```
Expected: 2 lines (the declaration and... actually just the one declaration at this point)

---

### Task 2: Add admin badge CSS to renderSharedTopBarCss()

**Files:**
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java` (in renderSharedTopBarCss, before its return statement)

- [ ] **Step 1: Write the failing test**

In `HtmlRendererTopBarTest.java`, add to the class:
```java
  public void testRenderSharedTopBarCssIncludesAdminBadge() {
    String css = HtmlRenderer.renderSharedTopBarCss();

    assertTrue(css.contains(".admin-msg-btn"));
    assertTrue(css.contains(".admin-badge"));
    assertTrue(css.contains("admin-glow"));
  }
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd /Users/vega/devroot/worktrees/feat-admin-contact-notification
sbt "wave/testOnly org.waveprotocol.box.server.rpc.HtmlRendererTopBarTest"
```
Expected: FAIL — `testRenderSharedTopBarCssIncludesAdminBadge` fails

- [ ] **Step 3: Add admin badge CSS to renderSharedTopBarCss()**

Find this at the end of `renderSharedTopBarCss()`:
```java
    sb.append("  .user-avatar { width: 24px; height: 24px; font-size: 11px; }\n");
    sb.append("}\n");
    return sb.toString();
  }
```

Replace with:
```java
    sb.append("  .user-avatar { width: 24px; height: 24px; font-size: 11px; }\n");
    sb.append("}\n");
    // Admin contact notification badge
    sb.append(".admin-msg-btn { cursor: pointer; text-decoration: none; }\n");
    sb.append(".admin-badge {\n");
    sb.append("  position: absolute; top: -4px; right: -4px;\n");
    sb.append("  min-width: 16px; height: 16px; padding: 0 4px;\n");
    sb.append("  background: #e53e3e; color: white;\n");
    sb.append("  border-radius: 8px; font-size: 10px; font-weight: 700;\n");
    sb.append("  display: inline-flex; align-items: center; justify-content: center;\n");
    sb.append("  line-height: 1; border: 1.5px solid rgba(0,50,100,0.6);\n");
    sb.append("}\n");
    sb.append(".admin-badge.hidden { display: none; }\n");
    sb.append(".admin-msg-btn.has-unread { animation: admin-glow 2s ease-in-out infinite; }\n");
    sb.append("@keyframes admin-glow {\n");
    sb.append("  0%, 100% { box-shadow: 0 0 0 0 rgba(229,62,62,0); }\n");
    sb.append("  50% { box-shadow: 0 0 8px 4px rgba(229,62,62,0.5); }\n");
    sb.append("}\n");
    return sb.toString();
  }
```

- [ ] **Step 4: Run test to verify it passes**

```bash
sbt "wave/testOnly org.waveprotocol.box.server.rpc.HtmlRendererTopBarTest"
```
Expected: `testRenderSharedTopBarCssIncludesAdminBadge` — PASS

---

### Task 3: Add admin badge CSS to wave client inline CSS (renderWaveClientPage)

**Files:**
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java` (in renderWaveClientPage, before closing `</style>`)

- [ ] **Step 1: Add admin badge CSS to wave client inline style block**

In `renderWaveClientPage`, find this (the end of the inline CSS block, around line 2549-2552):
```java
    sb.append("@keyframes prerender-fade-in {\n");
    sb.append("  from { opacity: 0; } to { opacity: 1; }\n");
    sb.append("}\n");
    sb.append("</style>\n");
    // GWT stats + nocache JS
```

Replace with:
```java
    sb.append("@keyframes prerender-fade-in {\n");
    sb.append("  from { opacity: 0; } to { opacity: 1; }\n");
    sb.append("}\n");
    // Admin contact notification badge CSS (same as renderSharedTopBarCss)
    sb.append(".admin-msg-btn { cursor: pointer; text-decoration: none; }\n");
    sb.append(".admin-badge {\n");
    sb.append("  position: absolute; top: -4px; right: -4px;\n");
    sb.append("  min-width: 16px; height: 16px; padding: 0 4px;\n");
    sb.append("  background: #e53e3e; color: white;\n");
    sb.append("  border-radius: 8px; font-size: 10px; font-weight: 700;\n");
    sb.append("  display: inline-flex; align-items: center; justify-content: center;\n");
    sb.append("  line-height: 1; border: 1.5px solid rgba(0,50,100,0.6);\n");
    sb.append("}\n");
    sb.append(".admin-badge.hidden { display: none; }\n");
    sb.append(".admin-msg-btn.has-unread { animation: admin-glow 2s ease-in-out infinite; }\n");
    sb.append("@keyframes admin-glow {\n");
    sb.append("  0%, 100% { box-shadow: 0 0 0 0 rgba(229,62,62,0); }\n");
    sb.append("  50% { box-shadow: 0 0 8px 4px rgba(229,62,62,0.5); }\n");
    sb.append("}\n");
    sb.append("</style>\n");
    // GWT stats + nocache JS
```

- [ ] **Step 2: Compile to verify no errors**

```bash
sbt wave/compile
```
Expected: BUILD SUCCESS

---

### Task 4: Add admin icon to renderSharedTopBarHtml()

**Files:**
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java`

- [ ] **Step 1: Write the failing test**

Add to `HtmlRendererTopBarTest.java`:
```java
  public void testRenderSharedTopBarHtmlAdminShowsEnvelopeIcon() {
    String html = HtmlRenderer.renderSharedTopBarHtml("vega@example.com", "", "admin");

    assertTrue(html.contains("id=\"adminMsgBtn\""));
    assertTrue(html.contains("id=\"adminMsgBadge\""));
    assertTrue(html.contains("href=\"/admin#contacts\""));
  }

  public void testRenderSharedTopBarHtmlUserHasNoEnvelopeIcon() {
    String html = HtmlRenderer.renderSharedTopBarHtml("vega@example.com", "", "user");

    assertFalse(html.contains("id=\"adminMsgBtn\""));
    assertFalse(html.contains("id=\"adminMsgBadge\""));
  }

  public void testRenderSharedTopBarHtmlOwnerShowsEnvelopeIcon() {
    String html = HtmlRenderer.renderSharedTopBarHtml("vega@example.com", "/wave", "owner");

    assertTrue(html.contains("id=\"adminMsgBtn\""));
    assertTrue(html.contains("href=\"/wave/admin#contacts\""));
  }
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
sbt "wave/testOnly org.waveprotocol.box.server.rpc.HtmlRendererTopBarTest"
```
Expected: the 3 new tests fail

- [ ] **Step 3: Add admin icon HTML in renderSharedTopBarHtml()**

In `renderSharedTopBarHtml()`, find:
```java
    // Connection status
    sb.append("    <span id=\"netstatus\" class=\"topbar-icon offline\" title=\"Offline\" aria-live=\"polite\">\n");
    sb.append("      <span class=\"net-icon net-icon-online\">").append(ICON_WIFI).append("</span>\n");
    sb.append("      <span class=\"net-icon net-icon-offline\">").append(ICON_WIFI_OFF).append("</span>\n");
    sb.append("    </span>\n");
    // User menu
    sb.append("    <div class=\"user-menu\">\n");
```

Replace with:
```java
    // Connection status
    sb.append("    <span id=\"netstatus\" class=\"topbar-icon offline\" title=\"Offline\" aria-live=\"polite\">\n");
    sb.append("      <span class=\"net-icon net-icon-online\">").append(ICON_WIFI).append("</span>\n");
    sb.append("      <span class=\"net-icon net-icon-offline\">").append(ICON_WIFI_OFF).append("</span>\n");
    sb.append("    </span>\n");
    // Admin contact notification icon (admin/owner only)
    if ("owner".equals(userRole) || "admin".equals(userRole)) {
      sb.append("    <a id=\"adminMsgBtn\" class=\"topbar-icon admin-msg-btn\" href=\"").append(safeCtx).append("/admin#contacts\" title=\"Contact messages\" aria-label=\"Contact messages\">\n");
      sb.append("      ").append(ICON_MAIL).append("\n");
      sb.append("      <span id=\"adminMsgBadge\" class=\"admin-badge hidden\" aria-hidden=\"true\">0</span>\n");
      sb.append("    </a>\n");
    }
    // User menu
    sb.append("    <div class=\"user-menu\">\n");
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
sbt "wave/testOnly org.waveprotocol.box.server.rpc.HtmlRendererTopBarTest"
```
Expected: all 3 new tests PASS

- [ ] **Step 5: Commit**

```bash
cd /Users/vega/devroot/worktrees/feat-admin-contact-notification
git add wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java \
        wave/src/test/java/org/waveprotocol/box/server/rpc/HtmlRendererTopBarTest.java
git commit -m "feat: add admin envelope icon to shared top bar HTML with badge element"
```

---

### Task 5: Add admin icon to renderTopBar() (wave client top bar)

**Files:**
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java`

- [ ] **Step 1: Write the failing test**

Add to `HtmlRendererTopBarTest.java`:
```java
  public void testRenderTopBarAdminShowsEnvelopeIcon() {
    String html = HtmlRenderer.renderTopBar("vega", "example.com", "admin");

    assertTrue(html.contains("id=\"adminMsgBtn\""));
    assertTrue(html.contains("id=\"adminMsgBadge\""));
    assertTrue(html.contains("href=\"/admin#contacts\""));
  }

  public void testRenderTopBarUserHasNoEnvelopeIcon() {
    String html = HtmlRenderer.renderTopBar("vega", "example.com", "user");

    assertFalse(html.contains("id=\"adminMsgBtn\""));
  }

  public void testRenderTopBarOwnerShowsEnvelopeIcon() {
    String html = HtmlRenderer.renderTopBar("vega", "example.com", "owner");

    assertTrue(html.contains("id=\"adminMsgBtn\""));
  }
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
sbt "wave/testOnly org.waveprotocol.box.server.rpc.HtmlRendererTopBarTest"
```
Expected: the 3 new tests fail

- [ ] **Step 3: Add admin icon HTML in renderTopBar()**

In `renderTopBar()`, find:
```java
      // -- Connection status: wifi-off icon for initial offline state, updated by GWT --
      sb.append("    <span id=\"netstatus\" class=\"topbar-icon offline\" title=\"Offline\">");
      sb.append(ICON_WIFI_OFF).append("</span>\n");
      // -- User menu with avatar --
      sb.append("    <div class=\"user-menu\">\n");
```

Replace with:
```java
      // -- Connection status: wifi-off icon for initial offline state, updated by GWT --
      sb.append("    <span id=\"netstatus\" class=\"topbar-icon offline\" title=\"Offline\">");
      sb.append(ICON_WIFI_OFF).append("</span>\n");
      // -- Admin contact notification icon (admin/owner only) --
      if ("owner".equals(userRole) || "admin".equals(userRole)) {
        sb.append("    <a id=\"adminMsgBtn\" class=\"topbar-icon admin-msg-btn\" href=\"/admin#contacts\" title=\"Contact messages\" aria-label=\"Contact messages\">\n");
        sb.append("      ").append(ICON_MAIL).append("\n");
        sb.append("      <span id=\"adminMsgBadge\" class=\"admin-badge hidden\" aria-hidden=\"true\">0</span>\n");
        sb.append("    </a>\n");
      }
      // -- User menu with avatar --
      sb.append("    <div class=\"user-menu\">\n");
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
sbt "wave/testOnly org.waveprotocol.box.server.rpc.HtmlRendererTopBarTest"
```
Expected: all 3 new tests PASS

- [ ] **Step 5: Compile**

```bash
sbt wave/compile
```
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java \
        wave/src/test/java/org/waveprotocol/box/server/rpc/HtmlRendererTopBarTest.java
git commit -m "feat: add admin envelope icon to GWT wave client top bar"
```

---

### Task 6: Add renderSharedTopBarJs overload + admin polling

**Files:**
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java`

- [ ] **Step 1: Write the failing tests**

Add to `HtmlRendererTopBarTest.java`:
```java
  public void testRenderSharedTopBarJsAdminIncludesPolling() {
    String js = HtmlRenderer.renderSharedTopBarJs("/wave", "admin");

    assertTrue(js.contains("adminMsgBtn"));
    assertTrue(js.contains("/admin/api/contacts?status=new&limit=0"));
    assertTrue(js.contains("setInterval"));
  }

  public void testRenderSharedTopBarJsOwnerIncludesPolling() {
    String js = HtmlRenderer.renderSharedTopBarJs("", "owner");

    assertTrue(js.contains("adminMsgBtn"));
    assertTrue(js.contains("/admin/api/contacts?status=new&limit=0"));
  }

  public void testRenderSharedTopBarJsUserNoPolling() {
    String js = HtmlRenderer.renderSharedTopBarJs("", "user");

    assertFalse(js.contains("adminMsgBtn"));
    assertFalse(js.contains("/admin/api/contacts"));
  }

  public void testRenderSharedTopBarJsOneArgNoPolling() {
    // One-arg overload should not include admin polling
    String js = HtmlRenderer.renderSharedTopBarJs("/wave");

    assertFalse(js.contains("adminMsgBtn"));
  }
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
sbt "wave/testOnly org.waveprotocol.box.server.rpc.HtmlRendererTopBarTest"
```
Expected: the 4 new tests fail (testRenderSharedTopBarJsAdminIncludesPolling, testRenderSharedTopBarJsOwnerIncludesPolling, testRenderSharedTopBarJsUserNoPolling fail; testRenderSharedTopBarJsOneArgNoPolling may also fail)

- [ ] **Step 3: Add two-arg overload and refactor renderSharedTopBarJs**

In `HtmlRenderer.java`, find the method:
```java
  public static String renderSharedTopBarJs(String contextPath) {
    StringBuilder sb = new StringBuilder(1024);
    sb.append("<script>\n(function(){\n");
    sb.append("var _ctx=").append(escapeJsonString(contextPath == null ? "" : contextPath)).append(";\n");
```

Add a one-arg delegate BEFORE this method (it must appear before the two-arg version, or Java allows either order):

Find the Javadoc for `renderSharedTopBarJs` and replace the whole method signature + opening line to add both overloads. Specifically, find the exact beginning of the method:

```java
  /**
   * Returns a script block for the shared topbar: user-menu toggle, connection status,
   * and language selector initialization. Safe for server-rendered pages without GWT.
   *
   * @param contextPath servlet context path, typically "" for root deployments
   */
  public static String renderSharedTopBarJs(String contextPath) {
    StringBuilder sb = new StringBuilder(1024);
```

Replace with:
```java
  /**
   * Returns a script block for the shared topbar: user-menu toggle, connection status,
   * and language selector initialization. Safe for server-rendered pages without GWT.
   *
   * @param contextPath servlet context path, typically "" for root deployments
   */
  public static String renderSharedTopBarJs(String contextPath) {
    return renderSharedTopBarJs(contextPath, null);
  }

  /**
   * Returns a script block for the shared topbar. If userRole is "admin" or "owner",
   * also includes the admin contact notification polling script.
   *
   * @param contextPath servlet context path, typically "" for root deployments
   * @param userRole    the user's role; null or "user" omits admin polling
   */
  public static String renderSharedTopBarJs(String contextPath, String userRole) {
    StringBuilder sb = new StringBuilder(1024);
```

- [ ] **Step 4: Add admin polling JS at the end of the new two-arg method**

The current end of `renderSharedTopBarJs` (now the two-arg version) before `return sb.toString()` is:
```java
    sb.append("if(lc)lc.textContent=ls.value.toUpperCase();});}\n");
    sb.append("})();\n</script>\n");
    return sb.toString();
  }
```

Replace with:
```java
    sb.append("if(lc)lc.textContent=ls.value.toUpperCase();});}\n");
    sb.append("})();\n</script>\n");
    // Admin contact notification polling (admin/owner only)
    if ("owner".equals(userRole) || "admin".equals(userRole)) {
      String ctx = escapeJsonString(contextPath == null ? "" : contextPath);
      sb.append("<script>\n(function(){\n");
      sb.append("var _c=").append(ctx).append(";\n");
      sb.append("var btn=document.getElementById('adminMsgBtn');\n");
      sb.append("var badge=document.getElementById('adminMsgBadge');\n");
      sb.append("if(!btn||!badge){return;}\n");
      sb.append("function pollAdmin(){\n");
      sb.append("  fetch(_c+'/admin/api/contacts?status=new&limit=0')\n");
      sb.append("    .then(function(r){return r.json();})\n");
      sb.append("    .then(function(d){\n");
      sb.append("      var n=d.total||0;\n");
      sb.append("      if(n>0){\n");
      sb.append("        badge.textContent=n;\n");
      sb.append("        badge.classList.remove('hidden');\n");
      sb.append("        btn.classList.add('has-unread');\n");
      sb.append("        var label=n+' unread contact message'+(n===1?'':'s');\n");
      sb.append("        btn.title=label;\n");
      sb.append("        btn.setAttribute('aria-label',label);\n");
      sb.append("      }else{\n");
      sb.append("        badge.classList.add('hidden');\n");
      sb.append("        btn.classList.remove('has-unread');\n");
      sb.append("        btn.title='Contact messages';\n");
      sb.append("        btn.setAttribute('aria-label','Contact messages');\n");
      sb.append("      }\n");
      sb.append("    }).catch(function(){});\n");
      sb.append("}\n");
      sb.append("pollAdmin();\n");
      sb.append("setInterval(pollAdmin,30000);\n");
      sb.append("})();\n</script>\n");
    }
    return sb.toString();
  }
```

- [ ] **Step 5: Run tests to verify they pass**

```bash
sbt "wave/testOnly org.waveprotocol.box.server.rpc.HtmlRendererTopBarTest"
```
Expected: all tests PASS

- [ ] **Step 6: Compile**

```bash
sbt wave/compile
```
Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java \
        wave/src/test/java/org/waveprotocol/box/server/rpc/HtmlRendererTopBarTest.java
git commit -m "feat: add admin contact polling to renderSharedTopBarJs for admin/owner role"
```

---

### Task 7: Update renderSharedTopBarJs call sites to pass userRole

**Files:**
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java`

There are 3 call sites. Each must be updated with a unique surrounding context. The call sites are:
1. In `renderAdminPage(currentUser, domain, contextPath, callerRole)` — has `callerRole` variable
2. In `renderAccountSettingsPage(currentUser, domain, contextPath, account, passwordResetEnabled)` — has `account.getRole()`
3. In `renderProfileEditPage(currentUser, domain, contextPath, imageUrl, account)` — has `account.getRole()`

- [ ] **Step 1: Update renderAdminPage call site**

In `renderAdminPage`, find:
```java
    sb.append("})();\n");
    sb.append("</script>\n");
    sb.append(renderSharedTopBarJs(contextPath));
    sb.append("</body>\n</html>\n");
    return sb.toString();
  }

  // =========================================================================
  // 14. Access Denied Page
```

Replace with:
```java
    sb.append("})();\n");
    sb.append("</script>\n");
    sb.append(renderSharedTopBarJs(contextPath, callerRole));
    sb.append("</body>\n</html>\n");
    return sb.toString();
  }

  // =========================================================================
  // 14. Access Denied Page
```

- [ ] **Step 2: Update renderAccountSettingsPage call site**

In `renderAccountSettingsPage`, find:
```java
    sb.append("})();\n");
    sb.append("</script>\n");
    sb.append(renderSharedTopBarJs(contextPath));

    sb.append("</body>\n</html>\n");
    return sb.toString();
  }

  // =========================================================================
  // Profile Edit Page
```

Replace with:
```java
    sb.append("})();\n");
    sb.append("</script>\n");
    sb.append(renderSharedTopBarJs(contextPath, account.getRole()));

    sb.append("</body>\n</html>\n");
    return sb.toString();
  }

  // =========================================================================
  // Profile Edit Page
```

- [ ] **Step 3: Update renderProfileEditPage call site**

In `renderProfileEditPage`, find:
```java
    sb.append("})();\n");
    sb.append("</script>\n");
    sb.append(renderSharedTopBarJs(contextPath));

    sb.append("</body>\n</html>\n");
    return sb.toString();
  }
}
```

Replace with:
```java
    sb.append("})();\n");
    sb.append("</script>\n");
    sb.append(renderSharedTopBarJs(contextPath, account.getRole()));

    sb.append("</body>\n</html>\n");
    return sb.toString();
  }
}
```

- [ ] **Step 4: Compile to verify**

```bash
sbt wave/compile
```
Expected: BUILD SUCCESS

- [ ] **Step 5: Run all top bar tests**

```bash
sbt "wave/testOnly org.waveprotocol.box.server.rpc.HtmlRendererTopBarTest"
```
Expected: all PASS

- [ ] **Step 6: Commit**

```bash
git add wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java
git commit -m "feat: pass userRole to renderSharedTopBarJs at all call sites"
```

---

### Task 8: Add admin contact polling to wave client page (renderWaveClientPage)

**Files:**
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java`

The wave client page uses `window.__session.role` (set at line ~2202) for the runtime admin check.

- [ ] **Step 1: Add appendAdminContactPollingScript private method**

Find this helper method section (after `appendLightboxFragment` which is around line 3055):
```java
  /**
   * Appends the lightbox overlay element and JavaScript for viewing image
   * attachments full-size with navigation.
```

Add BEFORE that javadoc (i.e., insert a new private method right before `appendLightboxFragment`):

Find:
```java
  /**
   * Appends the lightbox overlay element and JavaScript for viewing image
   * attachments full-size with navigation. The global {@code window.openWaveLightbox}
   * function is called from GWT via JSNI when a user clicks an image thumbnail.
   */
  private static void appendLightboxFragment(StringBuilder sb) {
```

Replace with:
```java
  /**
   * Appends a script block that polls for unread admin contact messages and updates
   * the admin notification icon in the top bar. Only activates if the page's
   * {@code window.__session.role} is "admin" or "owner".
   */
  private static void appendAdminContactPollingScript(StringBuilder sb) {
    sb.append("<script>\n(function(){\n");
    sb.append("  var role=window.__session&&window.__session.role;\n");
    sb.append("  if(role!=='admin'&&role!=='owner')return;\n");
    sb.append("  var btn=document.getElementById('adminMsgBtn');\n");
    sb.append("  var badge=document.getElementById('adminMsgBadge');\n");
    sb.append("  if(!btn||!badge)return;\n");
    sb.append("  function pollAdmin(){\n");
    sb.append("    fetch('/admin/api/contacts?status=new&limit=0')\n");
    sb.append("      .then(function(r){return r.json();})\n");
    sb.append("      .then(function(d){\n");
    sb.append("        var n=d.total||0;\n");
    sb.append("        if(n>0){\n");
    sb.append("          badge.textContent=n;\n");
    sb.append("          badge.classList.remove('hidden');\n");
    sb.append("          btn.classList.add('has-unread');\n");
    sb.append("          var label=n+' unread contact message'+(n===1?'':'s');\n");
    sb.append("          btn.title=label;\n");
    sb.append("          btn.setAttribute('aria-label',label);\n");
    sb.append("        }else{\n");
    sb.append("          badge.classList.add('hidden');\n");
    sb.append("          btn.classList.remove('has-unread');\n");
    sb.append("          btn.title='Contact messages';\n");
    sb.append("          btn.setAttribute('aria-label','Contact messages');\n");
    sb.append("        }\n");
    sb.append("      }).catch(function(){});\n");
    sb.append("  }\n");
    sb.append("  pollAdmin();\n");
    sb.append("  setInterval(pollAdmin,30000);\n");
    sb.append("})();\n</script>\n");
  }

  /**
   * Appends the lightbox overlay element and JavaScript for viewing image
   * attachments full-size with navigation. The global {@code window.openWaveLightbox}
   * function is called from GWT via JSNI when a user clicks an image thumbnail.
   */
  private static void appendLightboxFragment(StringBuilder sb) {
```

- [ ] **Step 2: Call appendAdminContactPollingScript from renderWaveClientPage**

In `renderWaveClientPage`, find:
```java
    // -- Version upgrade detection polling --
    appendVersionCheckScript(sb, buildCommit, serverBuildTime, currentReleaseId);
    sb.append("</body>\n</html>\n");
    return sb.toString();
```

Replace with:
```java
    // -- Version upgrade detection polling --
    appendVersionCheckScript(sb, buildCommit, serverBuildTime, currentReleaseId);
    // -- Admin contact notification polling (only activates for admin/owner via __session.role) --
    appendAdminContactPollingScript(sb);
    sb.append("</body>\n</html>\n");
    return sb.toString();
```

- [ ] **Step 3: Compile to verify**

```bash
sbt wave/compile
```
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java
git commit -m "feat: add admin contact polling script to wave client page"
```

---

### Task 9: Add hash-based tab navigation to renderAdminPage

**Files:**
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java`

This allows navigating to `/admin#contacts` to auto-activate the contacts tab.

- [ ] **Step 1: Add hash navigation after tab setup**

In `renderAdminPage`, find (the end of the tab click handler loop):
```java
      sb.append("      if (tab.dataset.tab === 'analytics') { loadAnalyticsHistory(analyticsActiveWindow); loadAnalyticsStatus(); }\n");
      sb.append("    });\n");
      sb.append("  });\n");
      sb.append("  document.querySelectorAll('.window-pill').forEach(function(pill) {\n");
```

Replace with:
```java
      sb.append("      if (tab.dataset.tab === 'analytics') { loadAnalyticsHistory(analyticsActiveWindow); loadAnalyticsStatus(); }\n");
      sb.append("    });\n");
      sb.append("  });\n");
      // Hash-based tab navigation: /admin#contacts auto-activates contacts tab
      sb.append("  var initialHash = window.location.hash ? window.location.hash.slice(1) : '';\n");
      sb.append("  if (initialHash) {\n");
      sb.append("    var targetTab = document.querySelector('.admin-tab[data-tab=\"' + initialHash + '\"]');\n");
      sb.append("    if (targetTab) { targetTab.click(); }\n");
      sb.append("  }\n");
      sb.append("  document.querySelectorAll('.window-pill').forEach(function(pill) {\n");
```

- [ ] **Step 2: Compile to verify**

```bash
sbt wave/compile
```
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java
git commit -m "feat: add hash-based tab navigation to admin page for /admin#contacts deep link"
```

---

### Task 10: Run full test suite and fix any issues

- [ ] **Step 1: Run all HtmlRenderer tests**

```bash
sbt "wave/testOnly org.waveprotocol.box.server.rpc.HtmlRenderer*"
```
Expected: all PASS

- [ ] **Step 2: Run full server test suite**

```bash
sbt wave/test
```
Expected: BUILD SUCCESS (or only pre-existing failures unrelated to this feature)

- [ ] **Step 3: Fix any failures**

If any test fails due to changes in this plan:
- Read the failure message carefully
- Identify which method/assertion is wrong
- Fix the implementation (not the test, unless the test was incorrectly written in this plan)
- Re-run until PASS

---

### Task 11: Local server verification

- [ ] **Step 1: Start local server**

```bash
cd /Users/vega/devroot/worktrees/feat-admin-contact-notification
WAVE_PORT=9899 sbt prepareServerConfig run &
SERVER_PID=$!
# Wait for server to start
sleep 30
curl -s http://localhost:9899/healthz
```
Expected: healthz response (200 or some valid response)

- [ ] **Step 2: Verify admin user sees notification icon**

```bash
# Check that the wave client page includes the admin icon HTML for admin users
# The top bar HTML is rendered server-side, check it via a registered admin session
# Verify the admin icon CSS is present in the page
curl -s http://localhost:9899/ | grep -c "adminMsgBtn"
```

If this returns 0, it means the user is not logged in (anonymous). Log in via browser and check manually:
- Open `http://localhost:9899` in browser
- Log in as admin user (e.g., vega@supawave.ai)
- Verify envelope icon appears in the top bar between the wifi icon and user avatar
- If no unread messages: icon visible, no badge
- Submit a contact form via `/contact` to create an unread message
- Wait up to 30 seconds for badge to appear
- Badge should show "1" with red glow animation
- Click icon — should navigate to `/admin#contacts` and contacts tab should be active

- [ ] **Step 3: Verify regular user does NOT see notification icon**

- Log out, log in as a regular user
- Verify no envelope icon in top bar

- [ ] **Step 4: Stop server**

```bash
kill $SERVER_PID 2>/dev/null || true
fuser -k 9899/tcp 2>/dev/null || true
```

---

### Task 12: Update changelog and create PR

- [ ] **Step 1: Update changelog**

Edit `wave/config/changelog.json`. Add a new entry at the TOP (newest first):

```json
{
  "releaseId": "admin-contact-notification",
  "date": "2026-04-08",
  "title": "Admin contact notification badge",
  "summary": "Admins and owners now see an envelope icon in the top bar that shows a red badge with unread contact message count. The icon pulses with a glow animation when there are new messages. Clicking it navigates directly to the Contact Messages tab in the admin panel.",
  "changes": [
    "Admin/owner top bar shows envelope icon for unread contact messages",
    "Red pill badge with unread count, glow animation when messages present",
    "Icon polls every 30 seconds; clicking navigates to /admin#contacts",
    "/admin#contacts deep-link auto-activates the Contact Messages tab"
  ]
}
```

Read the file first, then prepend the new entry to the existing array.

- [ ] **Step 2: Commit changelog**

```bash
git add wave/config/changelog.json
git commit -m "chore: add changelog entry for admin contact notification badge"
```

- [ ] **Step 3: Push branch**

```bash
git push origin feat/admin-contact-notification
```

- [ ] **Step 4: Create PR**

```bash
gh pr create \
  --title "feat: admin contact notification badge in top bar" \
  --body "$(cat <<'EOF'
## Summary

- Admin and owner users now see an envelope icon in the top bar (between the wifi icon and user avatar)
- Icon shows a red badge with unread contact message count when there are new messages
- Pulsating red glow animation when there are unread messages
- Polling every 30 seconds via existing `GET /admin/api/contacts?status=new&limit=0`
- Clicking navigates to `/admin#contacts` which auto-activates the Contact Messages tab
- Admin page now supports hash-based deep-linking (`/admin#contacts`, `/admin#users`, etc.)

## Technical Notes

- All changes in `HtmlRenderer.java` (jakarta-overrides) — no new endpoints, no GWT changes
- Icon rendered server-side for admin/owner roles only — regular users never see it
- Wave client page uses `window.__session.role` runtime check for polling
- Other pages (admin, settings, profile) pass `userRole` to `renderSharedTopBarJs`

## Test plan

- [ ] `sbt wave/compile` passes
- [ ] `sbt "wave/testOnly org.waveprotocol.box.server.rpc.HtmlRendererTopBarTest"` — all pass
- [ ] `sbt wave/test` — no new failures
- [ ] Local: admin user sees envelope icon between wifi and avatar
- [ ] Local: regular user does NOT see envelope icon
- [ ] Local: submit contact form → badge appears within 30s with glow animation
- [ ] Local: click badge → `/admin#contacts` → contacts tab is active

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

- [ ] **Step 5: Note the PR URL**

The PR URL will be printed by `gh pr create`. Record it for the PR monitor.

---

## Copilot Review Checkpoint

After all tasks complete and PR is created, run a Copilot review:

```bash
PROMPT="Review the admin contact notification implementation on branch feat/admin-contact-notification.

Diff:
$(git diff origin/main...HEAD)

Focus on:
1. Security: Is the admin-only check robust? Any XSS risks in the badge count display?
2. JavaScript: Any edge cases in the polling (concurrent requests, memory leaks, missing cleanup)?
3. CSS: Will the badge/glow animation conflict with existing styles?
4. Test coverage: Are the tests adequate?
5. Hash navigation: Any edge case in admin page hash-based tab routing?

Report findings as PASS/WARN/FAIL per area."
copilot -p "$PROMPT" --model gpt-5.4 --effort high --silent 2>&1
```

Fix any FAIL items, re-run until all are PASS or WARN.
