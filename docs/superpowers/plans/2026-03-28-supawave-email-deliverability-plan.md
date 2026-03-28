# SupaWave Email Deliverability Investigation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Document why mail from `supawave.ai` sent through Resend is likely landing in spam and capture the exact repo-side and DNS-side follow-up.

**Architecture:** Treat this as an evidence-first ops/documentation task, not a speculative code fix. Compare live DNS for `supawave.ai` and `tube2web.ai`, inspect the repo’s outbound-mail configuration, then publish a precise operator checklist for DNS and deliverability improvements.

**Tech Stack:** Cloudflare DNS, Resend, Apache Wave deployment docs, shell verification

---

### Task 1: Capture Evidence

**Files:**
- Create: `docs/deployment/email-deliverability-supawave.md`
- Modify: `docs/deployment/README.md`

- [ ] **Step 1: Record the live DNS comparison**

Run:

```bash
for d in supawave.ai tube2web.ai; do
  echo "=== $d ==="
  for name in "$d" "_dmarc.$d" "resend._domainkey.$d" "send.$d"; do
    echo "-- $name"
    dig +short TXT "$name"
    dig +short MX "$name"
    dig +short CNAME "$name"
  done
done
```

Expected: both domains expose the same visible Resend-related records and neither publishes `_dmarc`.

- [ ] **Step 2: Confirm the repo’s outbound sender configuration**

Run:

```bash
rg -n "WAVE_EMAIL_FROM|mail_provider|email_from_address" deploy wave/config wave/src
```

Expected: the deployment config uses `core.mail_provider = "resend"` and `core.email_from_address = ${?WAVE_EMAIL_FROM}`.

- [ ] **Step 3: Write the operator-facing checklist**

Document:
- current public records
- likely spam causes
- exact DNS changes to make outside the repo
- safe repo-side follow-up that is still configurable today

### Task 2: Verify And Close Out

**Files:**
- Modify: `docs/deployment/README.md`
- Create: `docs/deployment/email-deliverability-supawave.md`

- [ ] **Step 1: Verify the doc edits**

Run:

```bash
git diff --check
rg -n "email-deliverability-supawave|_dmarc|WAVE_EMAIL_FROM|notify\\.supawave\\.ai" \
  docs/deployment/README.md docs/deployment/email-deliverability-supawave.md
```

Expected: no diff-check errors and the new checklist/doc links are present.

- [ ] **Step 2: Commit**

```bash
git add docs/superpowers/plans/2026-03-28-supawave-email-deliverability-plan.md \
  docs/deployment/README.md \
  docs/deployment/email-deliverability-supawave.md
git commit -m "docs: add supawave email deliverability checklist"
```
