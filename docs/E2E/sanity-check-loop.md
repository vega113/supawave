# E2E Sanity Check Loop Prompt

Use this prompt with `/loop 1h` to schedule recurring sanity checks of supawave.ai.

## Prerequisites

- Chrome browser with Claude-in-Chrome extension active
- SSH access configured: `ssh supawave` must work without password prompt
- **Disable 1Password for supawave.ai** — it crashes browser automation:
  - 1Password extension → Settings → Excluded Domains → add `supawave.ai`

## Test Users

| User | Password |
|------|----------|
| testSanity1 | testSanity1 |
| testSanity2 | testSanity2 |

Register if needed:
```bash
curl -s -X POST 'https://supawave.ai/auth/register' -d 'address=testSanity1&password=testSanity1'
curl -s -X POST 'https://supawave.ai/auth/register' -d 'address=testSanity2&password=testSanity2'
```

## Lessons Learned

### Tab Accumulation Causes Extension Disconnect
- **Problem**: Each sanity check creates a new tab. Over hours, 30+ tabs accumulate, each holding an open WebSocket to supawave.ai. This causes Chrome to throttle/suspend the MCP extension, leading to "Browser extension is not connected" errors.
- **Fix**: After each check, close the tab used for testing. Keep only the original tabs.
- **Implementation**: After completing browser checks, use `javascript_tool` to close the tab via `window.close()`, or track the tab ID and avoid reusing it.

### Login via JS Form Submit
- **Problem**: Clicking "Sign in" button fails due to 1Password interference
- **Fix**: Use JS: `document.querySelector('form').querySelector('input[type="text"]').value='user'; document.querySelector('input[type="password"]').value='pass'; document.querySelector('form').submit();`

### New Wave via CSS Class
- **Fix**: `document.querySelector('.SWCM2').click()`

### GWT Editor Typing is Flaky
- **Status**: Browser automation `type` action is unreliable with GWT editor. Not an app bug.
- **Recommendation**: Skip text entry in automated checks. Wave creation + blip loading is the real sanity signal.

### curl as Fallback When Browser Disconnects
If browser extension is disconnected, use curl to verify core functionality:
```bash
# Health check
curl -s -o /dev/null -w '%{http_code}' 'https://supawave.ai/healthz'
# Login + session
curl -s -c /tmp/wave-cookies.txt -X POST 'https://supawave.ai/auth/signin' -d 'address=testSanity1&password=testSanity1' -w '\n%{http_code}' -o /dev/null
# Search (proves auth + search + server)
curl -s -b /tmp/wave-cookies.txt 'https://supawave.ai/search/?query=in%3Ainbox&index=0&numResults=20' -w '\n%{http_code}' -o /dev/null
# GWT assets present
curl -s -o /dev/null -w '%{http_code}' 'https://supawave.ai/webclient/webclient.nocache.js'
```

### When Issues Are Found — Take Action
- Create GitHub issues (`gh issue create`) immediately, not just beads tasks or chat reports
- If fix is straightforward, create a PR
- Include links to created issues/PRs in the sanity check output

## GWT DOM Reference

| Element | Selector |
|---------|----------|
| New Wave button | `.SWCM2` |
| Wave editor | `div.document[editabledocmarker="true"]` |
| Search input | `input.SWCPJ[type="search"]` |

---

## Loop Prompt

Copy everything below for `/loop 1h`:

---

E2E Sanity Check for supawave.ai. Use browser automation and SSH.

PHASE 1 — BROWSER E2E TEST:

1. Call tabs_context_mcp, then tabs_create_mcp for a fresh tab. SAVE the new tab ID.
2. Navigate to https://supawave.ai/auth/signout?r=/ to ensure clean session.
3. Login as testSanity1/testSanity1 via javascript_tool: `const f=document.querySelector('form'); f.querySelector('input[type="text"]').value='testSanity1'; f.querySelector('input[type="password"]').value='testSanity1'; f.submit();`
   - If user doesn't exist, register via Bash: `curl -s -X POST 'https://supawave.ai/auth/register' -d 'address=testSanity1&password=testSanity1'`
4. Wait 3s. Verify login via JS: body contains "testsanity1" and signout link exists.
5. Create wave via JS: `document.querySelector('.SWCM2').click()` — verify URL hash changed.
6. Check console: read_console_messages pattern "error|Error|ERR|exception|Exception|fail|404|500|502" (clear=true). Ignore chrome-extension errors.
7. Sign out: navigate to https://supawave.ai/auth/signout?r=/
8. Login as testSanity2/testSanity2 (same JS form submit). Register if needed.
9. Verify login, check inbox count, check console errors.
10. Sign out.
11. **CLOSE THE TAB** used for this check via javascript_tool: `window.close()` — prevents tab accumulation which crashes the extension.

EXTENSION RECOVERY: If "Browser extension is not connected", first try tabs_context_mcp + tabs_create_mcp. If that also fails, fall back to curl checks:
```
curl -s -o /dev/null -w '%{http_code}' 'https://supawave.ai/healthz'
curl -s -c /tmp/wave-cookies.txt -X POST 'https://supawave.ai/auth/signin' -d 'address=testSanity1&password=testSanity1' -w '%{http_code}' -o /dev/null
curl -s -b /tmp/wave-cookies.txt 'https://supawave.ai/search/?query=in%3Ainbox&index=0&numResults=20' -w '%{http_code}' -o /dev/null
curl -s -o /dev/null -w '%{http_code}' 'https://supawave.ai/webclient/webclient.nocache.js'
```

PHASE 2 — SERVER LOG CHECK (always):

```
ssh supawave "docker logs supawave-wave-1 --since 1h 2>&1 | tail -300 | grep -iE 'error|exception|fatal|stacktrace|caused.by|OOM|crash'"
ssh supawave "docker logs supawave-caddy-1 --since 1h 2>&1 | tail -300 | grep -iE 'error|exception|fatal'"
ssh supawave "docker logs supawave-mongo-1 --since 1h 2>&1 | tail -300 | grep -iE 'error|exception|fatal'"
```

PHASE 3 — TRIAGE (if NEW errors found):

1. Check recent commits: `git log --oneline -20` and `gh pr list --repo vega113/supawave --state all --limit 10`
2. If confirmed NEW bug (not already tracked):
   a. Create GitHub issue: `gh issue create --repo vega113/supawave --title "..." --body "..."`
   b. If fix is straightforward, create a PR
3. Known issues to IGNORE (already tracked):
   - Caddy 502s during deploy restart windows (transient)
   - ResendMailProvider missing API key (GitHub #68)
   - RobotCapabilityFetcher missing binding (GitHub #66)
   - FragmentsServlet UnsupportedOperationException (GitHub #67)

OUTPUT:
```
=== SUPAWAVE SANITY CHECK [timestamp] ===
Client: PASS/FAIL/SKIP (details)
Server logs: CLEAN/ISSUES (details)
Action taken: NONE / ISSUE_CREATED (link) / PR_CREATED (link)
=======================================
```
